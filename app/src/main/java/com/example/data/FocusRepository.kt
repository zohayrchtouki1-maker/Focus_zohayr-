package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.ExportedFocusStats
import com.example.model.ExportedPrefEntry
import com.example.model.ExportedSubtaskItem
import com.example.model.FocusRecord
import com.example.model.ManualBackupExportSummary
import com.example.model.SubtaskItem
import com.example.model.WhiteNoiseType
import com.example.notifications.FocusNotificationManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        const val PREFS_NAME = "focus_todo_local_storage"
        private const val PREFS_STATE_KEY = "focus_todo_state_json"
        const val PREFS_LAST_AUTO_BACKUP_TIME = "last_auto_backup_time"

        const val BACKUP_FOLDER_NAME = "Focus_Zohayr"
        const val BACKUP_DISPLAY_PATH = "Documents/Focus_Zohayr"
        const val MASTER_BACKUP_FILE_NAME = "focus_zohayr_backup.json"
        const val AUTO_BACKUP_FILE_NAME = MASTER_BACKUP_FILE_NAME
        private const val LEGACY_AUTO_BACKUP_FILE_NAME = "focus_zohayr_auto_backup.json"

        private val backupMutex = Mutex()
        @Volatile
        private var latestKnownState: AppBackupState? = null

        fun formatBackupTimestamp(epochMillis: Long, isArabic: Boolean = true): String {
            if (epochMillis <= 0L) {
                return if (isArabic) "لم يتم النسخ بعد" else "Not backed up yet"
            }
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(epochMillis))
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.ENGLISH, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.ENGLISH, "%.2f MB", mb)
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
        backupMutex.withLock {
            val fileState = readStateFromFile(stateFile)
            val prefsState = readStateFromPrefs()
            val legacyUserStates = loadAnyLegacyUserDirectoryStates()

            var resolved = when {
                fileState != null && prefsState != null -> mergeBackupStates(prefsState, fileState)
                fileState != null -> fileState
                prefsState != null -> prefsState
                else -> SeedData.createInitialState()
            }

            for (legacy in legacyUserStates) {
                if (legacy.tasks.isNotEmpty() || legacy.focusRecords.isNotEmpty() || legacy.projects.isNotEmpty()) {
                    resolved = mergeBackupStates(resolved, legacy)
                }
            }

            val normalized = normalizeLoadedState(resolved)
            latestKnownState = normalized
            saveStateInternal(normalized)
            normalized
        }
    }

    suspend fun saveState(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            return@withContext try {
                val normalized = normalizeLoadedState(state)
                latestKnownState = normalized
                saveStateInternal(normalized)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveStateInternal(state: AppBackupState) {
        syncSettingsToExplicitPreferences(state.settings)
        val enriched = enrichStateForFullExport(state)
        val json = adapter.toJson(enriched)
        try {
            stateFile.parentFile?.mkdirs()
            val tmpFile = File(stateFile.parentFile, "${stateFile.name}.tmp")
            tmpFile.writeText(json, Charsets.UTF_8)
            if (!tmpFile.renameTo(stateFile)) {
                stateFile.writeText(json, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            try {
                stateFile.writeText(json, Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        try {
            prefs.edit().putString(PREFS_STATE_KEY, json).commit()
        } catch (_: Exception) {
        }
        performSilentAutoBackupInternal(json, enriched)
    }

    private fun performSilentAutoBackupInternal(json: String, enriched: AppBackupState? = null) {
        try {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, now).commit()
            writeJsonToDocumentsFolder(
                fileName = MASTER_BACKUP_FILE_NAME,
                json = json,
                overwriteExisting = true
            )
            removeLegacyAutoBackupFileIfPresent()

            val bytesSize = json.toByteArray(Charsets.UTF_8).size.toLong()
            val summary = ManualBackupExportSummary(
                fileName = MASTER_BACKUP_FILE_NAME,
                fileSizeBytes = bytesSize,
                formattedFileSize = formatFileSize(bytesSize),
                projectsCount = enriched?.projects?.size ?: 0,
                tasksCount = enriched?.tasks?.size ?: 0,
                subtasksCount = enriched?.subtasks?.size ?: 0,
                pomodoroSessionsCount = enriched?.pomodoroSessions?.size ?: 0,
                totalPrefsCount = enriched?.allPreferences?.values?.sumOf { it.size } ?: 0,
                updatedTimestamp = formatBackupTimestamp(now, true)
            )
            FocusNotificationManager.showAutoBackupSuccessNotification(context, summary)
        } catch (_: Exception) {
        }
    }

    private fun syncSettingsToExplicitPreferences(settings: AppSettings) {
        try {
            prefs.edit()
                .putInt("setting_pomodoro_minutes", settings.pomodoroMinutes)
                .putInt("setting_short_break_minutes", settings.shortBreakMinutes)
                .putInt("setting_long_break_minutes", settings.longBreakMinutes)
                .putInt("setting_long_break_interval", settings.longBreakInterval)
                .putBoolean("setting_auto_start_breaks", settings.autoStartBreaks)
                .putBoolean("setting_auto_start_next_pomodoro", settings.autoStartNextPomodoro)
                .putBoolean("setting_disable_breaks", settings.disableBreaks)
                .putBoolean("setting_alarm_enabled", settings.alarmEnabled)
                .putBoolean("setting_enable_task_reminders", settings.enableTaskReminders)
                .putBoolean("setting_persistent_timer_notification", settings.persistentTimerNotification)
                .putBoolean("setting_alarm_sound_and_vibration", settings.alarmSoundAndVibration)
                .putBoolean("setting_locked_in_recents_confirmed", settings.lockedInRecentsConfirmed)
                .putBoolean("setting_has_prompted_permissions_on_launch", settings.hasPromptedPermissionsOnLaunch)
                .putBoolean("setting_dark_theme", settings.darkTheme)
                .putString("setting_language", settings.language.name)
                .putString("setting_white_noise", settings.whiteNoise.name)
                .putFloat("setting_white_noise_volume", settings.whiteNoiseVolume)
                .putBoolean("strict_blocker_enabled", settings.strictBlockerEnabled)
                .putStringSet("strict_blocker_whitelisted_packages", settings.whitelistedPackages.toSet())
                .commit()
        } catch (_: Exception) {
        }
    }

    private fun collectAllSharedPreferencesSnapshot(): Map<String, List<ExportedPrefEntry>> {
        val result = linkedMapOf<String, List<ExportedPrefEntry>>()
        val prefNames = linkedSetOf<String>()
        prefNames.add(PREFS_NAME)
        prefNames.add("${context.packageName}_preferences")

        try {
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                sharedPrefsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".xml")) {
                        prefNames.add(file.name.removeSuffix(".xml"))
                    }
                }
            }
        } catch (_: Exception) {
        }

        for (prefName in prefNames) {
            try {
                val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val entries = mutableListOf<ExportedPrefEntry>()
                for ((key, rawVal) in sp.all) {
                    if (key == PREFS_STATE_KEY || rawVal == null) continue
                    val entry = when (rawVal) {
                        is String -> ExportedPrefEntry(key = key, type = "STRING", stringValue = rawVal)
                        is Int -> ExportedPrefEntry(key = key, type = "INT", intValue = rawVal)
                        is Long -> ExportedPrefEntry(key = key, type = "LONG", longValue = rawVal)
                        is Float -> ExportedPrefEntry(key = key, type = "FLOAT", floatValue = rawVal)
                        is Boolean -> ExportedPrefEntry(key = key, type = "BOOLEAN", booleanValue = rawVal)
                        is Set<*> -> {
                            val strSet = rawVal.filterIsInstance<String>().sorted()
                            ExportedPrefEntry(key = key, type = "STRING_SET", stringSetValue = strSet)
                        }
                        else -> ExportedPrefEntry(key = key, type = "STRING", stringValue = rawVal.toString())
                    }
                    entries.add(entry)
                }
                if (entries.isNotEmpty()) {
                    result[prefName] = entries.sortedBy { it.key }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun restoreAllSharedPreferencesFromSnapshot(allPreferences: Map<String, List<ExportedPrefEntry>>) {
        val preservedLastBackup = getLastAutoBackupTimeMillis()
        for ((prefName, entries) in allPreferences) {
            try {
                val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                for (entry in entries) {
                    if (entry.key == PREFS_STATE_KEY) continue
                    when (entry.type.uppercase(Locale.ENGLISH)) {
                        "STRING" -> entry.stringValue?.let { editor.putString(entry.key, it) }
                        "INT" -> entry.intValue?.let { editor.putInt(entry.key, it) }
                        "LONG" -> entry.longValue?.let { editor.putLong(entry.key, it) }
                        "FLOAT" -> entry.floatValue?.let { editor.putFloat(entry.key, it) }
                        "BOOLEAN" -> entry.booleanValue?.let { editor.putBoolean(entry.key, it) }
                        "STRING_SET" -> entry.stringSetValue?.let { editor.putStringSet(entry.key, it.toSet()) }
                    }
                }
                editor.commit()
            } catch (_: Exception) {
            }
        }
        if (preservedLastBackup > 0L) {
            try {
                prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, preservedLastBackup).commit()
            } catch (_: Exception) {
            }
        }
    }

    private fun normalizeLoadedState(loaded: AppBackupState): AppBackupState {
        // Reconcile any top-level relational subtasks into their parent TaskItem.subtasks
        val topLevelSubtasksByTask = loaded.subtasks.groupBy { it.parentTaskId }
        val normalizedTasks = loaded.tasks.mapIndexed { taskIdx, task ->
            val inlineSubs = task.subtasks.mapIndexed { subIdx, sub ->
                sub.copy(
                    parentTaskId = task.id,
                    sortOrder = if (sub.sortOrder != 0) sub.sortOrder else subIdx
                )
            }
            val topSubs = topLevelSubtasksByTask[task.id].orEmpty().map { exp ->
                SubtaskItem(
                    id = exp.id,
                    title = exp.title,
                    isDone = exp.isCompleted,
                    parentTaskId = task.id,
                    sortOrder = exp.sortOrder,
                    createdAt = exp.createdAt
                )
            }
            val mergedSubs = (inlineSubs + topSubs)
                .distinctBy { it.id }
                .sortedBy { it.sortOrder }

            task.copy(
                subtasks = mergedSubs,
                sortOrder = if (task.sortOrder != 0) task.sortOrder else taskIdx
            )
        }

        val allSubtaskExports = normalizedTasks.flatMap { task ->
            task.subtasks.mapIndexed { idx, sub ->
                ExportedSubtaskItem(
                    id = sub.id,
                    parentTaskId = task.id,
                    title = sub.title,
                    isCompleted = sub.isDone,
                    sortOrder = idx,
                    createdAt = sub.createdAt
                )
            }
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val combinedSessions = (loaded.focusRecords + loaded.pomodoroSessions)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }
            .map { rec ->
                val computedDateKey = rec.dateKey.ifBlank {
                    dateFmt.format(Date(rec.startedAtEpochMillis))
                }
                val durSecs = if (rec.durationSeconds > 0) rec.durationSeconds else rec.durationMinutes * 60
                val endMillis = if (rec.endTimeEpochMillis > rec.startedAtEpochMillis) {
                    rec.endTimeEpochMillis
                } else {
                    rec.startedAtEpochMillis + durSecs * 1000L
                }
                rec.copy(
                    dateKey = computedDateKey,
                    durationSeconds = durSecs,
                    endTimeEpochMillis = endMillis
                )
            }

        val builtInIds = setOf("proj_study", "proj_skills", "proj_routine", "proj_side")
        val normalizedProjects = loaded.projects.mapIndexed { idx, proj ->
            proj.copy(
                sortOrder = if (proj.sortOrder != 0) proj.sortOrder else idx,
                isDefault = proj.isDefault || (proj.id in builtInIds)
            )
        }

        val spStrictEnabled = prefs.getBoolean("strict_blocker_enabled", loaded.settings.strictBlockerEnabled)
        val spWhitelist = prefs.getStringSet("strict_blocker_whitelisted_packages", null)?.toList()
        val mergedWhitelist = (loaded.settings.whitelistedPackages + spWhitelist.orEmpty()).distinct().sorted()
        val reconciledSettings = loaded.settings.copy(
            strictBlockerEnabled = loaded.settings.strictBlockerEnabled || spStrictEnabled,
            whitelistedPackages = mergedWhitelist
        )

        val normalizedFolders = loaded.folders.mapIndexed { idx, folder ->
            folder.copy(sortOrder = if (folder.sortOrder != 0) folder.sortOrder else idx)
        }

        val dailyMap = combinedSessions
            .groupBy { it.dateKey }
            .mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

        val totalFocusMins = combinedSessions.sumOf { it.durationMinutes }
        val stats = ExportedFocusStats(
            totalFocusMinutes = totalFocusMins,
            totalFocusSeconds = combinedSessions.sumOf { it.durationSeconds },
            totalPomodoroSessions = combinedSessions.size,
            totalCompletedTasks = normalizedTasks.count { it.isCompleted },
            totalActiveTasks = normalizedTasks.count { !it.isCompleted },
            totalSubtasks = allSubtaskExports.size,
            completedSubtasks = allSubtaskExports.count { it.isCompleted },
            dailyMinutesByDateKey = dailyMap
        )

        return loaded.copy(
            backupVersion = 3,
            tasks = normalizedTasks,
            subtasks = allSubtaskExports,
            projects = normalizedProjects,
            folders = normalizedFolders,
            focusRecords = combinedSessions,
            pomodoroSessions = combinedSessions,
            focusStats = stats,
            settings = reconciledSettings
        )
    }

    fun enrichStateForFullExport(state: AppBackupState): AppBackupState {
        // Reconcile passed-in state with any state in memory/disk so zero user items are ever dropped
        var reconciled = state
        latestKnownState?.let { known ->
            if (known.tasks.size > reconciled.tasks.size ||
                known.projects.size > reconciled.projects.size ||
                known.focusRecords.size > reconciled.focusRecords.size
            ) {
                reconciled = mergeBackupStates(known, reconciled).copy(
                    settings = state.settings,
                    persistedTimer = state.persistedTimer ?: known.persistedTimer
                )
            }
        }
        syncSettingsToExplicitPreferences(reconciled.settings)
        val normalized = normalizeLoadedState(reconciled)
        val allPrefs = collectAllSharedPreferencesSnapshot()
        return normalized.copy(
            backupVersion = 3,
            exportedAt = System.currentTimeMillis(),
            allPreferences = allPrefs
        )
    }

    suspend fun exportManualBackupWithSummary(
        state: AppBackupState,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<ManualBackupExportSummary> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            return@withContext try {
                val fileName = MASTER_BACKUP_FILE_NAME
                val enriched = enrichStateForFullExport(state).copy(exportedAt = nowMillis)
                val json = adapter.toJson(enriched)
                val bytes = json.toByteArray(Charsets.UTF_8)
                writeJsonToDocumentsFolder(
                    fileName = fileName,
                    json = json,
                    overwriteExisting = true
                )
                removeLegacyAutoBackupFileIfPresent()
                prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, nowMillis).commit()

                val totalPrefsCount = enriched.allPreferences.values.sumOf { it.size }
                val summary = ManualBackupExportSummary(
                    fileName = fileName,
                    fileSizeBytes = bytes.size.toLong(),
                    formattedFileSize = formatFileSize(bytes.size.toLong()),
                    projectsCount = enriched.projects.size,
                    tasksCount = enriched.tasks.size,
                    subtasksCount = enriched.subtasks.size,
                    pomodoroSessionsCount = enriched.pomodoroSessions.size,
                    totalPrefsCount = totalPrefsCount,
                    updatedTimestamp = formatBackupTimestamp(nowMillis, true)
                )
                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun exportManualBackupToDocuments(
        state: AppBackupState,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<String> {
        return exportManualBackupWithSummary(state, nowMillis).map { it.fileName }
    }

    suspend fun restoreFullStateAtomically(
        current: AppBackupState,
        imported: AppBackupState,
        mode: BackupImportMode
    ): AppBackupState = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            val normalizedImported = normalizeLoadedState(imported)
            val targetState = when (mode) {
                BackupImportMode.MERGE -> mergeBackupStates(current, normalizedImported)
                BackupImportMode.REPLACE -> normalizedImported
            }
            if (normalizedImported.allPreferences.isNotEmpty()) {
                restoreAllSharedPreferencesFromSnapshot(normalizedImported.allPreferences)
            }
            val finalState = normalizeLoadedState(targetState)
            latestKnownState = finalState
            saveStateInternal(finalState)
            finalState
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
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            val existingUri = ContentUris.withAppendedId(collectionUri, id)
                            // Remove any extra duplicate MediaStore rows for the same filename
                            while (cursor.moveToNext()) {
                                val dupId = cursor.getLong(0)
                                val dupUri = ContentUris.withAppendedId(collectionUri, dupId)
                                try {
                                    resolver.delete(dupUri, null, null)
                                } catch (_: Exception) {
                                }
                            }
                            val wroteExisting = try {
                                (resolver.openOutputStream(existingUri, "wt")
                                    ?: resolver.openOutputStream(existingUri, "rwt")
                                    ?: resolver.openOutputStream(existingUri))?.use { out ->
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

    private fun removeLegacyAutoBackupFileIfPresent() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(LEGACY_AUTO_BACKUP_FILE_NAME, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_FOLDER_NAME%")
                resolver.query(
                    collectionUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val legacyUri = ContentUris.withAppendedId(collectionUri, id)
                        try {
                            resolver.delete(legacyUri, null, null)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (publicDocs != null) {
                val legacyPublicFile = File(File(publicDocs, BACKUP_FOLDER_NAME), LEGACY_AUTO_BACKUP_FILE_NAME)
                if (legacyPublicFile.exists()) {
                    legacyPublicFile.delete()
                }
            }
        } catch (_: Exception) {
        }
        try {
            val internalFallbackDir = File(File(context.filesDir, "Documents"), BACKUP_FOLDER_NAME)
            val legacyInternalFile = File(internalFallbackDir, LEGACY_AUTO_BACKUP_FILE_NAME)
            if (legacyInternalFile.exists()) {
                legacyInternalFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun readStateFromFile(file: File): AppBackupState? {
        return try {
            if (file.exists()) {
                adapter.fromJson(file.readText(Charsets.UTF_8))?.let { normalizeLoadedState(it) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readStateFromPrefs(): AppBackupState? {
        return try {
            val raw = prefs.getString(PREFS_STATE_KEY, null) ?: return null
            adapter.fromJson(raw)?.let { normalizeLoadedState(it) }
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

    fun mergeBackupStates(current: AppBackupState, imported: AppBackupState): AppBackupState {
        val normCurrent = normalizeLoadedState(current)
        val normImported = normalizeLoadedState(imported)

        val mergedTasks = (normImported.tasks + normCurrent.tasks)
            .groupBy { it.id }
            .map { (_, duplicates) ->
                val primary = duplicates.maxByOrNull { it.updatedAtEpochMillis } ?: duplicates.first()
                val allSubs = duplicates.flatMap { it.subtasks }.distinctBy { it.id }
                primary.copy(subtasks = allSubs)
            }

        val mergedProjects = (normCurrent.projects + normImported.projects)
            .distinctBy { it.id }
        val mergedFolders = (normCurrent.folders + normImported.folders)
            .distinctBy { it.id }
        val mergedTags = (normCurrent.tags + normImported.tags)
            .distinctBy { it.id }
        val mergedRecords = (normCurrent.focusRecords + normImported.focusRecords + normImported.pomodoroSessions)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }

        val mergedPlantedHistory = (normCurrent.forestState.plantedHistory + normImported.forestState.plantedHistory)
            .distinctBy { it.id }
            .sortedByDescending { it.plantedAtEpochMillis }
        val mergedUnlockedSpecies = (normCurrent.forestState.unlockedSpecies + normImported.forestState.unlockedSpecies)
            .distinct()
        val mergedSunlight = maxOf(normCurrent.forestState.sunlightPoints, normImported.forestState.sunlightPoints)

        return normalizeLoadedState(
            normCurrent.copy(
                tasks = mergedTasks,
                projects = mergedProjects,
                folders = mergedFolders,
                tags = mergedTags,
                focusRecords = mergedRecords,
                pomodoroSessions = mergedRecords,
                settings = normImported.settings,
                allPreferences = if (normImported.allPreferences.isNotEmpty()) normImported.allPreferences else normCurrent.allPreferences,
                forestState = normCurrent.forestState.copy(
                    sunlightPoints = mergedSunlight,
                    unlockedSpecies = mergedUnlockedSpecies,
                    plantedHistory = mergedPlantedHistory
                )
            )
        )
    }

    fun exportJson(state: AppBackupState): String {
        return adapter.toJson(enrichStateForFullExport(state))
    }

    fun createShareableBackupFile(state: AppBackupState): File {
        val backupsDir = File(context.cacheDir, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()
        val outFile = File(backupsDir, MASTER_BACKUP_FILE_NAME)
        outFile.writeText(exportJson(state), Charsets.UTF_8)
        return outFile
    }

    fun parseBackupJson(json: String): AppBackupState? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            val hasValidStructure = trimmed.contains("\"tasks\"") ||
                trimmed.contains("\"projects\"") ||
                trimmed.contains("\"settings\"") ||
                trimmed.contains("\"focusRecords\"") ||
                trimmed.contains("\"pomodoroSessions\"")
            if (!hasValidStructure) return null
            adapter.fromJson(trimmed)?.let { parsed ->
                val hydratedSettings = applyExportedPreferencesToSettings(parsed.settings, parsed.allPreferences)
                normalizeLoadedState(parsed.copy(settings = hydratedSettings))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExportedPreferencesToSettings(
        base: AppSettings,
        allPrefs: Map<String, List<ExportedPrefEntry>>
    ): AppSettings {
        val entries = allPrefs[PREFS_NAME].orEmpty().associateBy { it.key }
        if (entries.isEmpty()) return base
        return base.copy(
            pomodoroMinutes = entries["setting_pomodoro_minutes"]?.intValue ?: base.pomodoroMinutes,
            shortBreakMinutes = entries["setting_short_break_minutes"]?.intValue ?: base.shortBreakMinutes,
            longBreakMinutes = entries["setting_long_break_minutes"]?.intValue ?: base.longBreakMinutes,
            longBreakInterval = entries["setting_long_break_interval"]?.intValue ?: base.longBreakInterval,
            autoStartBreaks = entries["setting_auto_start_breaks"]?.booleanValue ?: base.autoStartBreaks,
            autoStartNextPomodoro = entries["setting_auto_start_next_pomodoro"]?.booleanValue ?: base.autoStartNextPomodoro,
            disableBreaks = entries["setting_disable_breaks"]?.booleanValue ?: base.disableBreaks,
            alarmEnabled = entries["setting_alarm_enabled"]?.booleanValue ?: base.alarmEnabled,
            enableTaskReminders = entries["setting_enable_task_reminders"]?.booleanValue ?: base.enableTaskReminders,
            persistentTimerNotification = entries["setting_persistent_timer_notification"]?.booleanValue ?: base.persistentTimerNotification,
            alarmSoundAndVibration = entries["setting_alarm_sound_and_vibration"]?.booleanValue ?: base.alarmSoundAndVibration,
            lockedInRecentsConfirmed = entries["setting_locked_in_recents_confirmed"]?.booleanValue ?: base.lockedInRecentsConfirmed,
            hasPromptedPermissionsOnLaunch = entries["setting_has_prompted_permissions_on_launch"]?.booleanValue ?: base.hasPromptedPermissionsOnLaunch,
            darkTheme = entries["setting_dark_theme"]?.booleanValue ?: base.darkTheme,
            language = entries["setting_language"]?.stringValue?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: base.language,
            whiteNoise = entries["setting_white_noise"]?.stringValue?.let {
                runCatching { WhiteNoiseType.valueOf(it) }.getOrNull()
            } ?: base.whiteNoise,
            whiteNoiseVolume = entries["setting_white_noise_volume"]?.floatValue ?: base.whiteNoiseVolume,
            strictBlockerEnabled = entries["strict_blocker_enabled"]?.booleanValue ?: base.strictBlockerEnabled,
            whitelistedPackages = entries["strict_blocker_whitelisted_packages"]?.stringSetValue ?: base.whitelistedPackages
        )
    }
}
