package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.AppBackupState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupImportMode {
    MERGE,
    REPLACE
}

class FocusRepository(private val context: Context) {
    companion object {
        private const val LOCAL_STATE_FILE_NAME = "focus_todo_state.json"
        private const val PREFS_NAME = "focus_todo_local_storage"
        private const val PREFS_STATE_KEY = "focus_todo_state_json"
        const val PREFS_LAST_AUTO_BACKUP_TIME = "last_auto_backup_time"

        const val BACKUP_FOLDER_NAME = "Focus_Zohayr"
        const val BACKUP_DISPLAY_PATH = "Documents/Focus_Zohayr"
        const val AUTO_BACKUP_FILE_NAME = "focus_zohayr_auto_backup.json"

        fun generateBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(nowMillis))
            return "focus_zohayr_backup_$dateStr.json"
        }

        fun generateManualTimestampedBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ENGLISH).format(Date(nowMillis))
            return "focus_zohayr_backup_$stamp.json"
        }

        fun formatBackupTimestamp(epochMillis: Long, isArabic: Boolean = true): String {
            if (epochMillis <= 0L) {
                return if (isArabic) "لم يتم النسخ بعد" else "Not backed up yet"
            }
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date(epochMillis))
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")

    private val stateFile: File get() = File(context.filesDir, LOCAL_STATE_FILE_NAME)
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastAutoBackupTimeMillis(): Long {
        return prefs.getLong(PREFS_LAST_AUTO_BACKUP_TIME, 0L)
    }

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        val fileState = readStateFromFile(stateFile)
        val prefsState = readStateFromPrefs()
        val legacyUserStates = loadAnyLegacyUserDirectoryStates()

        var resolved = fileState ?: prefsState ?: SeedData.createInitialState()
        for (legacy in legacyUserStates) {
            if (legacy.tasks.isNotEmpty() || legacy.focusRecords.isNotEmpty()) {
                resolved = mergeBackupStates(resolved, legacy)
            }
        }

        val cleaned = cleanLegacySamples(resolved)
        saveStateInternal(cleaned)
        cleaned
    }

    suspend fun saveState(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            saveStateInternal(cleanLegacySamples(state))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveStateInternal(state: AppBackupState) {
        val json = adapter.toJson(state)
        try {
            stateFile.parentFile?.mkdirs()
            val tmpFile = File(stateFile.parentFile, "${stateFile.name}.tmp")
            tmpFile.writeText(json, Charsets.UTF_8)
            tmpFile.renameTo(stateFile)
        } catch (_: Exception) {
        }
        try {
            prefs.edit().putString(PREFS_STATE_KEY, json).apply()
        } catch (_: Exception) {
        }
        performSilentAutoBackupInternal(json)
    }

    private fun performSilentAutoBackupInternal(json: String) {
        try {
            writeJsonToDocumentsFolder(
                fileName = AUTO_BACKUP_FILE_NAME,
                json = json,
                overwriteExisting = true
            )
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, now).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun exportManualBackupToDocuments(
        state: AppBackupState,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val fileName = generateManualTimestampedBackupFileName(nowMillis)
            val json = exportJson(state)
            writeJsonToDocumentsFolder(
                fileName = fileName,
                json = json,
                overwriteExisting = true
            )
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, now).apply()
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Throws(IOException::class)
    fun writeJsonToDocumentsFolder(
        fileName: String,
        json: String,
        overwriteExisting: Boolean
    ): Uri {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) {
            throw IOException("Backup JSON payload is empty")
        }

        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_FOLDER_NAME/"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            if (overwriteExisting) {
                try {
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(fileName, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_FOLDER_NAME%")
                    resolver.query(
                        collectionUri,
                        arrayOf(MediaStore.MediaColumns._ID),
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            val existingUri = ContentUris.withAppendedId(collectionUri, id)
                            val wroteExisting = try {
                                resolver.openOutputStream(existingUri, "wt")?.use { out ->
                                    out.write(bytes)
                                    out.flush()
                                    true
                                } ?: false
                            } catch (_: Exception) {
                                false
                            }
                            if (wroteExisting) {
                                alsoWriteMirrorDocumentsFile(fileName, bytes)
                                return existingUri
                            } else {
                                try {
                                    resolver.delete(existingUri, null, null)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val insertedUri = try {
                resolver.insert(collectionUri, values)
            } catch (_: Exception) {
                null
            }

            if (insertedUri != null) {
                var streamWritten = false
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                        streamWritten = true
                    }
                } catch (_: Exception) {
                    streamWritten = false
                }

                try {
                    val completeValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, completeValues, null, null)
                } catch (_: Exception) {
                }

                alsoWriteMirrorDocumentsFile(fileName, bytes)
                if (streamWritten) {
                    return insertedUri
                }
            }
        }

        // Direct Documents/Focus_Zohayr fallback (API < 29 or local test environment)
        val fallbackFile = writeMirrorDocumentsFileOrThrow(fileName, bytes)
        return Uri.fromFile(fallbackFile)
    }

    private fun alsoWriteMirrorDocumentsFile(fileName: String, bytes: ByteArray) {
        try {
            writeMirrorDocumentsFileOrThrow(fileName, bytes)
        } catch (_: Exception) {
        }
    }

    @Throws(IOException::class)
    private fun writeMirrorDocumentsFileOrThrow(fileName: String, bytes: ByteArray): File {
        val publicDocs = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } catch (_: Exception) {
            null
        }
        val targetDir = if (publicDocs != null) {
            File(publicDocs, BACKUP_FOLDER_NAME)
        } else {
            File(File(context.filesDir, "Documents"), BACKUP_FOLDER_NAME)
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            val internalFallbackDir = File(File(context.filesDir, "Documents"), BACKUP_FOLDER_NAME)
            internalFallbackDir.mkdirs()
            val internalFile = File(internalFallbackDir, fileName)
            internalFile.writeBytes(bytes)
            return internalFile
        }

        return try {
            val outFile = File(targetDir, fileName)
            outFile.writeBytes(bytes)
            outFile
        } catch (_: Exception) {
            val internalFallbackDir = File(File(context.filesDir, "Documents"), BACKUP_FOLDER_NAME)
            internalFallbackDir.mkdirs()
            val internalFile = File(internalFallbackDir, fileName)
            internalFile.writeBytes(bytes)
            internalFile
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

    private fun readStateFromPrefs(): AppBackupState? {
        return try {
            val raw = prefs.getString(PREFS_STATE_KEY, null) ?: return null
            adapter.fromJson(raw)?.let { cleanLegacySamples(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAnyLegacyUserDirectoryStates(): List<AppBackupState> {
        return try {
            val usersDir = File(context.filesDir, "users")
            if (!usersDir.exists() || !usersDir.isDirectory) return emptyList()
            val states = mutableListOf<AppBackupState>()
            usersDir.listFiles()?.forEach { userDir ->
                val f = File(userDir, "focus_todo_backup.json")
                readStateFromFile(f)?.let { states.add(it) }
            }
            usersDir.deleteRecursively()
            states
        } catch (_: Exception) {
            emptyList()
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

    fun mergeBackupStates(current: AppBackupState, imported: AppBackupState): AppBackupState {
        val cleanedImported = cleanLegacySamples(imported)
        val mergedTasks = (cleanedImported.tasks + current.tasks)
            .distinctBy { it.id }
        val mergedProjects = (current.projects + cleanedImported.projects)
            .distinctBy { it.id }
        val mergedFolders = (current.folders + cleanedImported.folders)
            .distinctBy { it.id }
        val mergedTags = (current.tags + cleanedImported.tags)
            .distinctBy { it.id }
        val mergedRecords = (current.focusRecords + cleanedImported.focusRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }

        val mergedPlantedHistory = (current.forestState.plantedHistory + cleanedImported.forestState.plantedHistory)
            .distinctBy { it.id }
            .sortedByDescending { it.plantedAtEpochMillis }
        val mergedUnlockedSpecies = (current.forestState.unlockedSpecies + cleanedImported.forestState.unlockedSpecies)
            .distinct()
        val mergedSunlight = maxOf(current.forestState.sunlightPoints, cleanedImported.forestState.sunlightPoints)

        return current.copy(
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedRecords,
            forestState = current.forestState.copy(
                sunlightPoints = mergedSunlight,
                unlockedSpecies = mergedUnlockedSpecies,
                plantedHistory = mergedPlantedHistory
            )
        )
    }

    fun exportJson(state: AppBackupState): String {
        return adapter.toJson(cleanLegacySamples(state))
    }

    fun createShareableBackupFile(state: AppBackupState): File {
        val backupsDir = File(context.cacheDir, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()
        val fileName = generateBackupFileName()
        val outFile = File(backupsDir, fileName)
        outFile.writeText(exportJson(state), Charsets.UTF_8)
        return outFile
    }

    fun parseBackupJson(json: String): AppBackupState? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            // Verify JSON contains valid structural fields before accepting
            val hasValidStructure = trimmed.contains("\"tasks\"") ||
                trimmed.contains("\"projects\"") ||
                trimmed.contains("\"settings\"") ||
                trimmed.contains("\"focusRecords\"")
            if (!hasValidStructure) return null
            adapter.fromJson(trimmed)?.let { cleanLegacySamples(it) }
        } catch (_: Exception) {
            null
        }
    }
}
