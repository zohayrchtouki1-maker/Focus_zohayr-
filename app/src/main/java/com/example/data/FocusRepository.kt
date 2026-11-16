package com.example.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.example.model.AppBackupState
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

object DriveScopes {
    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
}

enum class CloudSyncStatus {
    UNAUTHENTICATED,
    SYNCING,
    SYNCED,
    SYNC_FAILED
}

data class ActiveUserSession(
    val userId: String,
    val email: String,
    val displayName: String? = null
)

class FocusRepository(private val context: Context) {
    companion object {
        const val DRIVE_BACKUP_FILE_NAME = "focus_todo_backup.json"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")

    private val rootStateFile: File get() = File(context.filesDir, "focus_todo_state.json")

    private fun userStateFile(userId: String): File {
        val safeId = userId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = File(context.filesDir, "users/$safeId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, DRIVE_BACKUP_FILE_NAME)
    }

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions())
    }

    fun getGoogleSignInIntent(): Intent {
        return getGoogleSignInClient().signInIntent
    }

    fun getActiveSessionSync(): ActiveUserSession? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val email = account.email ?: return null
            val id = account.id ?: email
            ActiveUserSession(
                userId = id,
                email = email,
                displayName = account.displayName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun activeStateFile(): File {
        val session = getActiveSessionSync()
        return if (session != null) userStateFile(session.userId) else rootStateFile
    }

    /**
     * Completes Google Sign-In using the returned [GoogleSignInAccount], downloads/restores
     * `focus_todo_backup.json` from the user's Google Drive `appDataFolder`, merges any pre-login
     * local tasks so nothing is lost, and uploads the unified `focus_todo_backup.json` to `appDataFolder`.
     */
    suspend fun handleGoogleSignInSuccess(
        account: GoogleSignInAccount
    ): Result<Pair<ActiveUserSession, AppBackupState>> = withContext(Dispatchers.IO) {
        val email = account.email
            ?: return@withContext Result.failure(
                IllegalStateException("Google account did not provide an email address.")
            )
        val androidAccount = account.account
            ?: Account(email, "com.google")
        val userId = account.id ?: email
        val session = ActiveUserSession(
            userId = userId,
            email = email,
            displayName = account.displayName
        )

        try {
            val syncedState = syncAndRestoreWithDriveAppData(androidAccount, session)
            Result.success(session to syncedState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val client = getGoogleSignInClient()
            suspendCancellableCoroutine<Unit> { cont ->
                client.signOut().addOnCompleteListener {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Downloads `focus_todo_backup.json` from Google Drive `appDataFolder` (if it exists),
     * merges with any local state, caches locally, and uploads the updated `focus_todo_backup.json`
     * to `appDataFolder`.
     */
    suspend fun forceCloudSyncNow(currentLocalState: AppBackupState): Result<AppBackupState> =
        withContext(Dispatchers.IO) {
            val account = try {
                GoogleSignIn.getLastSignedInAccount(context)
            } catch (_: Exception) {
                null
            } ?: return@withContext Result.failure(
                IllegalStateException("Not signed in with Google. Please sign in first.")
            )

            val email = account.email
                ?: return@withContext Result.failure(
                    IllegalStateException("Signed-in Google account has no email.")
                )
            val androidAccount = account.account ?: Account(email, "com.google")
            val session = ActiveUserSession(
                userId = account.id ?: email,
                email = email,
                displayName = account.displayName
            )

            try {
                // Save current in-memory state to local user file first so syncAndRestore merges it
                saveStateInternal(cleanLegacySamples(currentLocalState), userStateFile(session.userId))
                val synced = syncAndRestoreWithDriveAppData(androidAccount, session)
                Result.success(synced)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun syncAndRestoreWithDriveAppData(
        androidAccount: Account,
        session: ActiveUserSession
    ): AppBackupState {
        var accessToken = fetchDriveAccessToken(androidAccount)
        val existingFileId = try {
            findBackupFileIdInAppData(accessToken)
        } catch (e: DriveUnauthorizedException) {
            GoogleAuthUtil.clearToken(context, accessToken)
            accessToken = fetchDriveAccessToken(androidAccount)
            findBackupFileIdInAppData(accessToken)
        }

        val remoteState: AppBackupState? = if (existingFileId != null) {
            downloadBackupFileFromAppData(accessToken, existingFileId)
        } else {
            null
        }

        val localAnonymousState = readStateFromFile(rootStateFile)
        val localUserState = readStateFromFile(userStateFile(session.userId))

        val merged = when {
            remoteState != null && localUserState != null -> mergeStates(localUserState, remoteState)
            remoteState != null && localAnonymousState != null && localAnonymousState.tasks.isNotEmpty() ->
                mergeStates(remoteState, localAnonymousState)
            remoteState != null -> remoteState
            localUserState != null && localAnonymousState != null -> mergeStates(localUserState, localAnonymousState)
            localUserState != null -> localUserState
            localAnonymousState != null -> localAnonymousState
            else -> SeedData.createInitialState()
        }

        val cleaned = cleanLegacySamples(merged)
        saveStateInternal(cleaned, userStateFile(session.userId))

        // Clear migrated anonymous tasks from root file so they don't re-merge later
        if (localAnonymousState != null && localAnonymousState.tasks.isNotEmpty()) {
            saveStateInternal(SeedData.createInitialState(), rootStateFile)
        }

        uploadBackupFileToAppData(accessToken, existingFileId, cleaned)
        return cleaned
    }

    private fun pushStateToDriveAppDataOrThrow(state: AppBackupState) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Not signed in with Google.")
        val email = account.email ?: throw IllegalStateException("Google account email is missing.")
        val androidAccount = account.account ?: Account(email, "com.google")

        var accessToken = fetchDriveAccessToken(androidAccount)
        val existingFileId = try {
            findBackupFileIdInAppData(accessToken)
        } catch (_: DriveUnauthorizedException) {
            GoogleAuthUtil.clearToken(context, accessToken)
            accessToken = fetchDriveAccessToken(androidAccount)
            findBackupFileIdInAppData(accessToken)
        }

        uploadBackupFileToAppData(accessToken, existingFileId, cleanLegacySamples(state))
    }

    private fun fetchDriveAccessToken(account: Account): String {
        val scopeStr = "oauth2:${DriveScopes.DRIVE_APPDATA}"
        return GoogleAuthUtil.getToken(context, account, scopeStr)
    }

    private fun findBackupFileIdInAppData(accessToken: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val query = URLEncoder.encode("name = '$DRIVE_BACKUP_FILE_NAME' and trashed = false", "UTF-8")
            val url = URL(
                "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime)&orderBy=modifiedTime+desc&pageSize=1"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code == 401) {
                throw DriveUnauthorizedException()
            }
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                throw IllegalStateException("Google Drive list failed (HTTP $code): ${extractDriveError(errBody)}")
            }

            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
            } else {
                null
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadBackupFileFromAppData(accessToken: String, fileId: String): AppBackupState? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                throw IllegalStateException("Google Drive download failed (HTTP $code): ${extractDriveError(errBody)}")
            }

            val rawJson = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseBackupJson(rawJson)?.let { cleanLegacySamples(it) }
        } finally {
            conn?.disconnect()
        }
    }

    private fun uploadBackupFileToAppData(
        accessToken: String,
        existingFileId: String?,
        state: AppBackupState
    ) {
        val jsonContent = adapter.toJson(state)
        var conn: HttpURLConnection? = null
        try {
            if (existingFileId != null) {
                // Update existing focus_todo_backup.json in appDataFolder
                val url = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media&fields=id,modifiedTime")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                conn.outputStream.use { os ->
                    os.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
            } else {
                // Create new focus_todo_backup.json inside appDataFolder via multipart upload
                val boundary = "FocusTodoDriveBoundary_${System.currentTimeMillis()}"
                val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,modifiedTime")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                }

                val metadataJson = JSONObject().apply {
                    put("name", DRIVE_BACKUP_FILE_NAME)
                    put("parents", org.json.JSONArray().put("appDataFolder"))
                    put("mimeType", "application/json")
                }.toString()

                val multipartBody = buildString {
                    append("--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(metadataJson)
                    append("\r\n--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(jsonContent)
                    append("\r\n--$boundary--\r\n")
                }

                conn.outputStream.use { os ->
                    os.write(multipartBody.toByteArray(Charsets.UTF_8))
                }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                throw IllegalStateException("Google Drive upload failed (HTTP $code): ${extractDriveError(errBody)}")
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractDriveError(errBody: String): String {
        if (errBody.isBlank()) return "Unknown Drive error"
        return try {
            val json = JSONObject(errBody)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: errBody.take(160)
        } catch (_: Exception) {
            errBody.take(160)
        }
    }

    private class DriveUnauthorizedException : Exception("Google Drive token expired")

    private fun mergeStates(primary: AppBackupState, secondary: AppBackupState): AppBackupState {
        val mergedTasks = (primary.tasks + secondary.tasks)
            .distinctBy { it.id }
        val mergedProjects = (primary.projects + secondary.projects)
            .distinctBy { it.id }
        val mergedFolders = (primary.folders + secondary.folders)
            .distinctBy { it.id }
        val mergedTags = (primary.tags + secondary.tags)
            .distinctBy { it.id }
        val mergedRecords = (primary.focusRecords + secondary.focusRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }

        return primary.copy(
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedRecords
        )
    }

    private fun readStateFromFile(file: File): AppBackupState? {
        return try {
            if (file.exists()) {
                adapter.fromJson(file.readText(Charsets.UTF_8))?.let { cleanLegacySamples(it) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanLegacySamples(loaded: AppBackupState): AppBackupState {
        val cleanedTasks = loaded.tasks.filterNot { it.id in SeedData.legacySampleTaskIds }
        val cleanedRecords = loaded.focusRecords.filterNot { it.id in SeedData.legacySampleRecordIds }
        return if (cleanedTasks.size != loaded.tasks.size || cleanedRecords.size != loaded.focusRecords.size) {
            loaded.copy(tasks = cleanedTasks, focusRecords = cleanedRecords)
        } else {
            loaded
        }
    }

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        val targetFile = activeStateFile()
        try {
            if (targetFile.exists()) {
                val raw = targetFile.readText(Charsets.UTF_8)
                adapter.fromJson(raw)?.let { loaded ->
                    val cleaned = cleanLegacySamples(loaded)
                    if (cleaned !== loaded) {
                        saveStateInternal(cleaned, targetFile)
                    }
                    return@withContext cleaned
                }
            }
        } catch (_: Exception) {
            // Fallback to fresh seed data if file is corrupted
        }
        val initial = SeedData.createInitialState()
        saveStateInternal(initial, targetFile)
        initial
    }

    suspend fun saveState(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        val targetFile = activeStateFile()
        saveStateInternal(state, targetFile)
        getActiveSessionSync() ?: return@withContext Result.success(Unit)
        try {
            pushStateToDriveAppDataOrThrow(state)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveStateInternal(state: AppBackupState, targetFile: File = activeStateFile()) {
        try {
            targetFile.parentFile?.mkdirs()
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeText(adapter.toJson(state), Charsets.UTF_8)
            tmpFile.renameTo(targetFile)
        } catch (_: Exception) {
        }
    }

    fun exportJson(state: AppBackupState): String {
        return adapter.toJson(state)
    }

    fun parseBackupJson(json: String): AppBackupState? {
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}
