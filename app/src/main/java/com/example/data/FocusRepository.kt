package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.model.AppBackupState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

enum class CloudSyncStatus {
    UNAUTHENTICATED,
    SYNCING,
    SYNCED,
    OFFLINE_CACHED
}

data class LocalAccountRecord(
    val userId: String = "",
    val email: String = "",
    val passwordHash: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

data class ActiveUserSession(
    val userId: String,
    val email: String,
    val idToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiresAtEpochMillis: Long = 0L,
    val lastSyncedEpochMillis: Long = 0L,
    val pendingCloudPush: Boolean = false
)

class FocusRepository(private val context: Context) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")
    private val accountListType = Types.newParameterizedType(List::class.java, LocalAccountRecord::class.java)
    private val accountsAdapter = moshi.adapter<List<LocalAccountRecord>>(accountListType).indent("  ")
    private val sessionAdapter = moshi.adapter(ActiveUserSession::class.java).indent("  ")

    private val rootStateFile: File get() = File(context.filesDir, "focus_todo_state.json")
    private val accountsFile: File get() = File(context.filesDir, "focus_accounts.json")
    private val activeSessionFile: File get() = File(context.filesDir, "focus_active_session.json")
    private val customApiKeyFile: File get() = File(context.filesDir, "focus_firebase_api_key.txt")

    val firebaseProjectId: String
        get() = BuildConfig.FIREBASE_PROJECT_ID.takeIf {
            it.isNotBlank() && it != "YOUR_FIREBASE_PROJECT_ID"
        } ?: "focus-zohayr"

    fun getResolvedFirebaseApiKey(): String {
        val fromBuildConfig = BuildConfig.FIREBASE_WEB_API_KEY.trim()
        if (fromBuildConfig.isNotBlank() &&
            fromBuildConfig != "YOUR_FIREBASE_WEB_API_KEY" &&
            fromBuildConfig != "MY_FIREBASE_WEB_API_KEY"
        ) {
            return fromBuildConfig
        }
        return try {
            if (customApiKeyFile.exists()) {
                customApiKeyFile.readText(Charsets.UTF_8).trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun saveCustomFirebaseApiKey(apiKey: String) {
        try {
            val cleaned = apiKey.trim()
            if (cleaned.isNotEmpty()) {
                customApiKeyFile.writeText(cleaned, Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
    }

    private fun userStateFile(userId: String): File {
        val dir = File(context.filesDir, "users/$userId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "focus_todo_state.json")
    }

    private fun userMetaFile(userId: String): File {
        val dir = File(context.filesDir, "users/$userId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "sync_meta.json")
    }

    private fun activeStateFile(): File {
        val session = getActiveSessionSync()
        return if (session != null) userStateFile(session.userId) else rootStateFile
    }

    fun getActiveSessionSync(): ActiveUserSession? {
        return try {
            if (activeSessionFile.exists()) {
                sessionAdapter.fromJson(activeSessionFile.readText(Charsets.UTF_8))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signUp(email: String, password: String): Result<ActiveUserSession> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("INVALID_EMAIL"))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("WEAK_PASSWORD"))
        }

        val apiKey = getResolvedFirebaseApiKey()
        if (apiKey.isNotBlank()) {
            val remoteResult = firebaseRestAuth(
                endpointAction = "signUp",
                email = email.trim(),
                password = password,
                apiKey = apiKey
            )
            if (remoteResult.isSuccess) {
                val session = remoteResult.getOrThrow()
                cacheLocalAccountCredentials(session.userId, session.email, password)
                saveActiveSessionSync(session)
                migrateAndSyncFirstLogin(session)
                return@withContext Result.success(getActiveSessionSync() ?: session)
            } else {
                val errMsg = remoteResult.exceptionOrNull()?.message ?: ""
                if (errMsg == "EMAIL_EXISTS" || errMsg == "INVALID_EMAIL" || errMsg == "WEAK_PASSWORD") {
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }
                // If network is unreachable, fall through to offline-capable account creation
                if (!errMsg.startsWith("NETWORK_")) {
                    return@withContext Result.failure(IllegalStateException(errMsg.ifBlank { "AUTH_FAILED" }))
                }
            }
        }

        // Offline cache fallback when network is unavailable or key is configured later
        val accounts = loadAccountsSync().toMutableList()
        if (accounts.any { it.email.equals(normalizedEmail, ignoreCase = true) }) {
            return@withContext Result.failure(IllegalStateException("EMAIL_EXISTS"))
        }

        val uid = "uid_" + sha256(normalizedEmail).take(18)
        cacheLocalAccountCredentials(uid, email.trim(), password)
        migrateLocalDataToUserIfNeeded(uid)

        val session = ActiveUserSession(
            userId = uid,
            email = email.trim(),
            pendingCloudPush = true
        )
        saveActiveSessionSync(session)
        Result.success(session)
    }

    suspend fun signIn(email: String, password: String): Result<ActiveUserSession> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("INVALID_EMAIL"))
        }
        if (password.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("EMPTY_PASSWORD"))
        }

        val apiKey = getResolvedFirebaseApiKey()
        if (apiKey.isNotBlank()) {
            val remoteResult = firebaseRestAuth(
                endpointAction = "sign" + "InWithPassword",
                email = email.trim(),
                password = password,
                apiKey = apiKey
            )
            if (remoteResult.isSuccess) {
                val session = remoteResult.getOrThrow()
                cacheLocalAccountCredentials(session.userId, session.email, password)
                saveActiveSessionSync(session)
                migrateAndSyncFirstLogin(session)
                return@withContext Result.success(getActiveSessionSync() ?: session)
            } else {
                val errMsg = remoteResult.exceptionOrNull()?.message ?: ""
                if (errMsg == "ACCOUNT_NOT_FOUND" || errMsg == "WRONG_PASSWORD" || errMsg == "INVALID_LOGIN_CREDENTIALS") {
                    return@withContext Result.failure(IllegalStateException("WRONG_PASSWORD"))
                }
                if (!errMsg.startsWith("NETWORK_")) {
                    return@withContext Result.failure(IllegalStateException(errMsg.ifBlank { "AUTH_FAILED" }))
                }
            }
        }

        // Offline cached account sign-in when offline
        val accounts = loadAccountsSync()
        val match = accounts.find { it.email.equals(normalizedEmail, ignoreCase = true) }
            ?: return@withContext Result.failure(IllegalStateException("ACCOUNT_NOT_FOUND"))

        if (match.passwordHash != sha256(password)) {
            return@withContext Result.failure(IllegalStateException("WRONG_PASSWORD"))
        }

        migrateLocalDataToUserIfNeeded(match.userId)
        val session = ActiveUserSession(
            userId = match.userId,
            email = match.email,
            pendingCloudPush = true
        )
        saveActiveSessionSync(session)
        Result.success(session)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            // Ensure current user's state is flushed locally before signing out
            val session = getActiveSessionSync()
            if (session != null) {
                val currentFile = userStateFile(session.userId)
                if (currentFile.exists()) {
                    val state = adapter.fromJson(currentFile.readText(Charsets.UTF_8))
                    if (state != null) {
                        pushStateToFirestore(session, state)
                    }
                }
            }
            if (activeSessionFile.exists()) {
                activeSessionFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Migrates any existing local data (from root `focus_todo_state.json`) into the user's
     * Firestore document `users/{userId}` and local offline cache `users/{userId}/focus_todo_state.json`
     * so nothing is lost on first login.
     */
    private suspend fun migrateAndSyncFirstLogin(session: ActiveUserSession) {
        val localAnonymousState = readStateFromFile(rootStateFile)
        val localUserFile = userStateFile(session.userId)
        val existingUserLocalState = readStateFromFile(localUserFile)

        // Check if `users/{userId}` already exists in Cloud Firestore
        val remoteFetch = fetchStateFromFirestore(session)
        val remoteState = remoteFetch.getOrNull()

        val baseState = when {
            remoteState != null && existingUserLocalState != null -> {
                mergeStates(remoteState, existingUserLocalState)
            }
            remoteState != null && localAnonymousState != null -> {
                mergeStates(remoteState, localAnonymousState)
            }
            remoteState != null -> remoteState
            existingUserLocalState != null && localAnonymousState != null -> {
                mergeStates(existingUserLocalState, localAnonymousState)
            }
            existingUserLocalState != null -> existingUserLocalState
            localAnonymousState != null -> localAnonymousState
            else -> SeedData.createInitialState()
        }

        val cleaned = cleanLegacySamples(baseState)
        saveStateInternal(cleaned, localUserFile)

        // Push the migrated/merged state to `users/{userId}` in Cloud Firestore
        val pushed = pushStateToFirestore(session, cleaned)
        val updatedSession = session.copy(
            lastSyncedEpochMillis = if (pushed) System.currentTimeMillis() else session.lastSyncedEpochMillis,
            pendingCloudPush = !pushed
        )
        saveActiveSessionSync(updatedSession)
    }

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

    private fun migrateLocalDataToUserIfNeeded(userId: String) {
        try {
            val targetFile = userStateFile(userId)
            if (!targetFile.exists() && rootStateFile.exists()) {
                val localState = readStateFromFile(rootStateFile)
                if (localState != null) {
                    val cleaned = cleanLegacySamples(localState)
                    saveStateInternal(cleaned, targetFile)
                }
            }
        } catch (_: Exception) {
        }
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

    suspend fun saveState(state: AppBackupState): Boolean = withContext(Dispatchers.IO) {
        val targetFile = activeStateFile()
        saveStateInternal(state, targetFile)
        val session = getActiveSessionSync() ?: return@withContext false
        writeLocalTimestamp(session.userId, System.currentTimeMillis())

        val pushed = pushStateToFirestore(session, state)
        saveActiveSessionSync(
            session.copy(
                lastSyncedEpochMillis = if (pushed) System.currentTimeMillis() else session.lastSyncedEpochMillis,
                pendingCloudPush = !pushed
            )
        )
        pushed
    }

    /**
     * Synchronizes the signed-in user's `users/{userId}` document with Cloud Firestore:
     * - If there are pending local offline changes, pushes them to Firestore first.
     * - Otherwise pulls remote changes from `users/{userId}` if the cloud document is newer.
     */
    suspend fun syncWithCloudIfSignedIn(currentLocalState: AppBackupState): Pair<AppBackupState?, CloudSyncStatus> =
        withContext(Dispatchers.IO) {
            val session = getActiveSessionSync()
                ?: return@withContext (null to CloudSyncStatus.UNAUTHENTICATED)

            val refreshedSession = ensureValidSessionToken(session)
            if (refreshedSession.idToken.isNullOrBlank()) {
                return@withContext (null to CloudSyncStatus.OFFLINE_CACHED)
            }

            if (refreshedSession.pendingCloudPush) {
                val pushed = pushStateToFirestore(refreshedSession, currentLocalState)
                if (pushed) {
                    val now = System.currentTimeMillis()
                    writeLocalTimestamp(refreshedSession.userId, now)
                    saveActiveSessionSync(
                        refreshedSession.copy(
                            lastSyncedEpochMillis = now,
                            pendingCloudPush = false
                        )
                    )
                    return@withContext (null to CloudSyncStatus.SYNCED)
                } else {
                    return@withContext (null to CloudSyncStatus.OFFLINE_CACHED)
                }
            }

            val remoteResult = fetchStateFromFirestoreWithTimestamp(refreshedSession)
            if (remoteResult.isFailure) {
                return@withContext (null to CloudSyncStatus.OFFLINE_CACHED)
            }

            val pair = remoteResult.getOrNull()
            if (pair == null) {
                // Document does not exist on server yet; push local state now
                val pushed = pushStateToFirestore(refreshedSession, currentLocalState)
                val now = System.currentTimeMillis()
                if (pushed) {
                    writeLocalTimestamp(refreshedSession.userId, now)
                    saveActiveSessionSync(
                        refreshedSession.copy(
                            lastSyncedEpochMillis = now,
                            pendingCloudPush = false
                        )
                    )
                    return@withContext (null to CloudSyncStatus.SYNCED)
                }
                return@withContext (null to CloudSyncStatus.OFFLINE_CACHED)
            }

            val (remoteState, remoteTimestamp) = pair
            val localTimestamp = readLocalTimestamp(refreshedSession.userId)
            val cleanedRemote = cleanLegacySamples(remoteState)

            if (remoteTimestamp > localTimestamp + 1500L) {
                saveStateInternal(cleanedRemote, userStateFile(refreshedSession.userId))
                writeLocalTimestamp(refreshedSession.userId, remoteTimestamp)
                saveActiveSessionSync(
                    refreshedSession.copy(
                        lastSyncedEpochMillis = System.currentTimeMillis(),
                        pendingCloudPush = false
                    )
                )
                return@withContext (cleanedRemote to CloudSyncStatus.SYNCED)
            } else {
                saveActiveSessionSync(
                    refreshedSession.copy(
                        lastSyncedEpochMillis = System.currentTimeMillis(),
                        pendingCloudPush = false
                    )
                )
                return@withContext (null to CloudSyncStatus.SYNCED)
            }
        }

    private fun firebaseRestAuth(
        endpointAction: String,
        email: String,
        password: String,
        apiKey: String
    ): Result<ActiveUserSession> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:$endpointAction?key=$apiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }

            if (code in 200..299) {
                val json = JSONObject(body)
                val localId = json.optString("localId")
                val registeredEmail = json.optString("email", email)
                val idToken = json.optString("idToken")
                val refreshToken = json.optString("refreshToken")
                val expiresInSecs = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L

                Result.success(
                    ActiveUserSession(
                        userId = localId,
                        email = registeredEmail,
                        idToken = idToken,
                        refreshToken = refreshToken,
                        tokenExpiresAtEpochMillis = System.currentTimeMillis() + (expiresInSecs - 120L) * 1000L,
                        lastSyncedEpochMillis = System.currentTimeMillis(),
                        pendingCloudPush = false
                    )
                )
            } else {
                val errMessage = try {
                    JSONObject(body).optJSONObject("error")?.optString("message") ?: "AUTH_ERROR_$code"
                } catch (_: Exception) {
                    "AUTH_ERROR_$code"
                }
                val mapped = when {
                    errMessage.contains("EMAIL_EXISTS") -> "EMAIL_EXISTS"
                    errMessage.contains("INVALID_EMAIL") -> "INVALID_EMAIL"
                    errMessage.contains("WEAK_PASSWORD") -> "WEAK_PASSWORD"
                    errMessage.contains("EMAIL_NOT_FOUND") -> "ACCOUNT_NOT_FOUND"
                    errMessage.contains("INVALID_PASSWORD") || errMessage.contains("INVALID_LOGIN_CREDENTIALS") -> "WRONG_PASSWORD"
                    else -> errMessage
                }
                Result.failure(IllegalStateException(mapped))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("NETWORK_ERROR: ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun ensureValidSessionToken(session: ActiveUserSession): ActiveUserSession {
        val apiKey = getResolvedFirebaseApiKey()
        if (apiKey.isBlank()) return session
        val now = System.currentTimeMillis()
        if (!session.idToken.isNullOrBlank() && now < session.tokenExpiresAtEpochMillis) {
            return session
        }
        val refresh = session.refreshToken ?: return session
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://securetoken.googleapis.com/v1/token?key=$apiKey")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("refresh_token", refresh)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val newIdToken = json.optString("id_token", session.idToken ?: "")
                val newRefresh = json.optString("refresh_token", refresh)
                val expiresIn = json.optString("expires_in", "3600").toLongOrNull() ?: 3600L
                val updated = session.copy(
                    idToken = newIdToken,
                    refreshToken = newRefresh,
                    tokenExpiresAtEpochMillis = now + (expiresIn - 120L) * 1000L
                )
                saveActiveSessionSync(updated)
                updated
            } else {
                session
            }
        } catch (_: Exception) {
            session
        } finally {
            conn?.disconnect()
        }
    }

    private fun pushStateToFirestore(session: ActiveUserSession, state: AppBackupState): Boolean {
        val refreshed = ensureValidSessionToken(session)
        val idToken = refreshed.idToken?.takeIf { it.isNotBlank() } ?: return false
        val projectId = firebaseProjectId
        val userId = refreshed.userId
        val now = System.currentTimeMillis()

        var conn: HttpURLConnection? = null
        return try {
            val docUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$userId"
            conn = (URL(docUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val stateJsonString = adapter.toJson(state)
            val fieldsObj = JSONObject().apply {
                put("userId", JSONObject().put("stringValue", userId))
                put("email", JSONObject().put("stringValue", refreshed.email))
                put("updatedAtEpochMillis", JSONObject().put("integerValue", now.toString()))
                put("taskCount", JSONObject().put("integerValue", state.tasks.size.toString()))
                put("projectCount", JSONObject().put("integerValue", state.projects.size.toString()))
                put("stateJson", JSONObject().put("stringValue", stateJsonString))
            }
            val rootObj = JSONObject().put("fields", fieldsObj)

            conn.outputStream.use { os ->
                os.write(rootObj.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                writeLocalTimestamp(userId, now)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchStateFromFirestore(session: ActiveUserSession): Result<AppBackupState?> {
        return fetchStateFromFirestoreWithTimestamp(session).map { it?.first }
    }

    private fun fetchStateFromFirestoreWithTimestamp(
        session: ActiveUserSession
    ): Result<Pair<AppBackupState, Long>?> {
        val refreshed = ensureValidSessionToken(session)
        val idToken = refreshed.idToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("NO_TOKEN"))
        val projectId = firebaseProjectId
        val userId = refreshed.userId

        var conn: HttpURLConnection? = null
        return try {
            val docUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$userId"
            conn = (URL(docUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $idToken")
            }

            val code = conn.responseCode
            if (code == 404) {
                return Result.success(null)
            }
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val fields = json.optJSONObject("fields") ?: return Result.success(null)
                val stateJson = fields.optJSONObject("stateJson")?.optString("stringValue")
                val updatedAt = fields.optJSONObject("updatedAtEpochMillis")
                    ?.optString("integerValue")
                    ?.toLongOrNull() ?: 0L

                if (!stateJson.isNullOrBlank()) {
                    val parsed = adapter.fromJson(stateJson)
                    if (parsed != null) {
                        return Result.success(parsed to updatedAt)
                    }
                }
                Result.success(null)
            } else {
                Result.failure(IllegalStateException("FIRESTORE_HTTP_$code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun writeLocalTimestamp(userId: String, timestamp: Long) {
        try {
            val file = userMetaFile(userId)
            val json = JSONObject().put("updatedAtEpochMillis", timestamp)
            file.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun readLocalTimestamp(userId: String): Long {
        return try {
            val file = userMetaFile(userId)
            if (file.exists()) {
                JSONObject(file.readText(Charsets.UTF_8)).optLong("updatedAtEpochMillis", 0L)
            } else 0L
        } catch (_: Exception) {
            0L
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

    private fun cacheLocalAccountCredentials(userId: String, email: String, password: String) {
        try {
            val normalizedEmail = email.trim().lowercase(Locale.ROOT)
            val accounts = loadAccountsSync().filterNot {
                it.email.equals(normalizedEmail, ignoreCase = true)
            }.toMutableList()
            accounts.add(
                LocalAccountRecord(
                    userId = userId,
                    email = email.trim(),
                    passwordHash = sha256(password)
                )
            )
            saveAccountsSync(accounts)
        } catch (_: Exception) {
        }
    }

    private fun loadAccountsSync(): List<LocalAccountRecord> {
        return try {
            if (accountsFile.exists()) {
                accountsAdapter.fromJson(accountsFile.readText(Charsets.UTF_8)) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAccountsSync(accounts: List<LocalAccountRecord>) {
        try {
            accountsFile.writeText(accountsAdapter.toJson(accounts), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun saveActiveSessionSync(session: ActiveUserSession) {
        try {
            activeSessionFile.writeText(sessionAdapter.toJson(session), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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
