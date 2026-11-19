package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.ai.TomatoAiService
import com.example.ai.TomatoGeminiModel
import com.example.blocker.StrictAppBlockerManager
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.ExportedFocusStats
import com.example.model.ExportedPrefEntry
import com.example.model.ExportedSubtaskItem
import com.example.model.ManualBackupExportSummary
import com.example.model.PendingBackupRestorePrompt
import com.example.model.SubtaskItem
import com.example.model.WhiteNoiseType
import com.example.notifications.FocusNotificationManager
import com.example.notifications.SystemPermissionsHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

        const val BACKUP_FOLDER_NAME = "FocusTomato"
        const val BACKUP_DISPLAY_PATH = "Documents/FocusTomato"
        const val MASTER_BACKUP_FILE_NAME = "FocusTomato_backup.json"
        const val AUTO_BACKUP_FILE_NAME = MASTER_BACKUP_FILE_NAME
        const val PUBLIC_BACKUP_ABSOLUTE_PATH = "/storage/emulated/0/Documents/FocusTomato/FocusTomato_backup.json"

        private const val LEGACY_AUTO_BACKUP_FILE_NAME = "focus_zohayr_auto_backup.json"
        private const val PREFS_MASTER_BACKUP_URI = "master_backup_mediastore_uri"
        private const val PREFS_LOCAL_DELETED_IDS = "local_soft_deleted_ids"
        private const val PREFS_START_FRESH_CHOSEN = "fresh_install_start_fresh_chosen"

        private val LOCAL_ONLY_PREF_KEYS = setOf(
            PREFS_STATE_KEY,
            PREFS_MASTER_BACKUP_URI,
            PREFS_LOCAL_DELETED_IDS,
            PREFS_START_FRESH_CHOSEN
        )

        private val backupMutex = Mutex()
        @Volatile
        private var latestKnownState: AppBackupState? = null
        @Volatile
        private var autoBackupPaused: Boolean = false

        fun resetStaticCacheForTests() {
            latestKnownState = null
            autoBackupPaused = false
        }

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

    fun isAutoBackupPaused(): Boolean = autoBackupPaused

    fun setAutoBackupPaused(paused: Boolean) {
        autoBackupPaused = paused
    }

    fun wasStartFreshChosen(): Boolean = prefs.getBoolean(PREFS_START_FRESH_CHOSEN, false)

    fun markStartFreshChosen(chosen: Boolean) {
        prefs.edit().putBoolean(PREFS_START_FRESH_CHOSEN, chosen).commit()
    }

    // --- Soft-Delete Tombstones (Local Install Only) ---

    fun getLocalDeletedIds(): Set<String> {
        return prefs.getStringSet(PREFS_LOCAL_DELETED_IDS, emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun recordDeletedIds(ids: Collection<String>) {
        val clean = ids.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        val updated = getLocalDeletedIds() + clean
        prefs.edit().putStringSet(PREFS_LOCAL_DELETED_IDS, updated).commit()
    }

    fun removeDeletedIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val current = getLocalDeletedIds()
        val updated = current - ids.toSet()
        if (updated.size != current.size) {
            prefs.edit().putStringSet(PREFS_LOCAL_DELETED_IDS, updated).commit()
        }
    }

    fun clearLocalDeletedIds() {
        prefs.edit().remove(PREFS_LOCAL_DELETED_IDS).commit()
    }

    // --- Public Storage File Helpers ---

    fun getPublicBackupDirectory(): File {
        val publicDocs = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } catch (_: Exception) {
            null
        }
        return if (publicDocs != null) {
            File(publicDocs, BACKUP_FOLDER_NAME)
        } else {
            File("/storage/emulated/0/Documents/$BACKUP_FOLDER_NAME")
        }
    }

    fun getPublicBackupFile(): File {
        return File(getPublicBackupDirectory(), MASTER_BACKUP_FILE_NAME)
    }

    fun isAppStateEffectivelyEmpty(state: AppBackupState): Boolean {
        return BackupSmartMerger.isAppStateEffectivelyEmpty(state)
    }

    /**
     * Reads the existing FocusTomato_backup.json directly from public storage
     * (/storage/emulated/0/Documents/FocusTomato/FocusTomato_backup.json), with MediaStore fallback.
     */
    fun readExistingExternalBackup(): AppBackupState? {
        // 1. Direct File read from /storage/emulated/0/Documents/FocusTomato/FocusTomato_backup.json
        try {
            val publicFile = getPublicBackupFile()
            if (publicFile.exists() && publicFile.isFile && publicFile.canRead() && publicFile.length() > 0L) {
                val raw = publicFile.readText(Charsets.UTF_8)
                parseBackupJson(raw)?.let { return it }
            }
        } catch (_: Exception) {
        }

        // 2. MediaStore fallback (Android 10+ if MANAGE_EXTERNAL_STORAGE is not yet granted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(MASTER_BACKUP_FILE_NAME)
                resolver.query(
                    collectionUri,
                    arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.RELATIVE_PATH
                    ),
                    selection,
                    selectionArgs,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val relPath = cursor.getString(1).orEmpty()
                        if (relPath.isNotBlank() && !relPath.contains(BACKUP_FOLDER_NAME, ignoreCase = true)) {
                            continue
                        }
                        val rowUri = ContentUris.withAppendedId(collectionUri, id)
                        val raw = resolver.openInputStream(rowUri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        }
                        if (!raw.isNullOrBlank()) {
                            parseBackupJson(raw)?.let { return it }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Empty-State Guard & Fresh Install Auto-Restore check:
     * If the current app state is completely empty and a non-empty FocusTomato_backup.json exists,
     * pause auto-backup immediately and return prompt details so the user can Restore All or Start Fresh.
     */
    suspend fun checkFreshInstallExistingBackup(currentState: AppBackupState): PendingBackupRestorePrompt? =
        withContext(Dispatchers.IO) {
            backupMutex.withLock {
                if (wasStartFreshChosen()) return@withLock null
                if (!BackupSmartMerger.isAppStateEffectivelyEmpty(currentState)) return@withLock null

                val externalBackup = readExistingExternalBackup() ?: return@withLock null
                if (!BackupSmartMerger.isBackupStateNonEmpty(externalBackup)) return@withLock null

                autoBackupPaused = true
                val sessionsCount = maxOf(externalBackup.pomodoroSessions.size, externalBackup.focusRecords.size)
                PendingBackupRestorePrompt(
                    backupState = externalBackup,
                    tasksCount = externalBackup.tasks.size,
                    sessionsCount = sessionsCount,
                    projectsCount = externalBackup.projects.size,
                    filePath = "$BACKUP_DISPLAY_PATH/$MASTER_BACKUP_FILE_NAME"
                )
            }
        }

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            val fileState = readStateFromFile(stateFile)
            val prefsState = readStateFromPrefs()
            val legacyUserStates = loadAnyLegacyUserDirectoryStates()

            val deletedIds = getLocalDeletedIds()
            var resolved = when {
                fileState != null && prefsState != null -> {
                    BackupSmartMerger.smartMerge(
                        live = fileState,
                        existingDisk = prefsState,
                        localDeletedIds = deletedIds
                    )
                }
                fileState != null -> fileState
                prefsState != null -> prefsState
                else -> SeedData.createInitialState()
            }

            for (legacy in legacyUserStates) {
                if (legacy.tasks.isNotEmpty() || legacy.focusRecords.isNotEmpty() || legacy.projects.isNotEmpty()) {
                    resolved = BackupSmartMerger.smartMerge(resolved, legacy, deletedIds)
                }
            }

            val isFreshEmpty = fileState == null && prefsState == null && legacyUserStates.isEmpty()
            val normalized = normalizeLoadedState(resolved, reconcileWithLocalPrefs = !isFreshEmpty)
            latestKnownState = normalized

            // IMPORTANT: On startup loadState(), only update internal app storage if non-empty.
            // NEVER overwrite external FocusTomato_backup.json on fresh startup!
            if (!isFreshEmpty) {
                saveLocalPrivateStateOnly(normalized)
            }
            normalized
        }
    }

    /**
     * Saves live app state to local storage and performs Full-State Smart Merge with the existing
     * FocusTomato_backup.json on the device before writing back. Returns the final merged AppBackupState
     * so the live UI always reflects the complete combined data.
     */
    suspend fun saveState(
        state: AppBackupState,
        bypassExternalBackup: Boolean = false
    ): Result<AppBackupState> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            return@withContext try {
                val mergedAndSaved = saveStateInternal(
                    state = state,
                    notifyAutoBackup = !bypassExternalBackup,
                    writeToExternalBackup = !bypassExternalBackup
                )
                Result.success(mergedAndSaved)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveLocalPrivateStateOnly(state: AppBackupState): String {
        val enriched = enrichStateForFullExport(state, reconcileWithLocalPrefs = false)
        val json = adapter.toJson(enriched)
        writePrivateStateFiles(json)
        return json
    }

    private fun writePrivateStateFiles(json: String) {
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
    }

    private fun saveStateInternal(
        state: AppBackupState,
        notifyAutoBackup: Boolean = true,
        writeToExternalBackup: Boolean = true,
        skipExternalSmartMerge: Boolean = false
    ): AppBackupState {
        val localDeletedIds = getLocalDeletedIds()
        val normalizedLive = normalizeLoadedState(state, reconcileWithLocalPrefs = false)

        // Check existing public backup file for Empty-State Guard & Full-State Smart Merge
        val existingExternal = if (writeToExternalBackup) readExistingExternalBackup() else null
        val externalIsNonEmpty = existingExternal != null && BackupSmartMerger.isBackupStateNonEmpty(existingExternal)
        val liveIsEmpty = BackupSmartMerger.isAppStateEffectivelyEmpty(normalizedLive)

        // Empty-State Guard: NEVER overwrite an existing non-empty backup with an empty app state
        if (writeToExternalBackup && (autoBackupPaused || (liveIsEmpty && externalIsNonEmpty))) {
            latestKnownState = normalizedLive
            saveLocalPrivateStateOnly(normalizedLive)
            return normalizedLive
        }

        // Full-State Smart Merge: union existing phone backup with live state before writing
        val finalMerged = if (writeToExternalBackup && !skipExternalSmartMerge && externalIsNonEmpty && existingExternal != null) {
            val merged = BackupSmartMerger.smartMerge(
                live = normalizedLive,
                existingDisk = existingExternal,
                localDeletedIds = localDeletedIds
            )
            if (merged.settings != normalizedLive.settings) {
                syncMergedSettingsToDevicePrefs(
                    previousLive = normalizedLive.settings,
                    mergedSettings = merged.settings
                )
            } else {
                syncSettingsToExplicitPreferences(normalizedLive.settings)
            }
            normalizeLoadedState(merged, reconcileWithLocalPrefs = false)
        } else {
            syncSettingsToExplicitPreferences(normalizedLive.settings)
            normalizedLive
        }

        val enriched = enrichStateForFullExport(finalMerged, reconcileWithLocalPrefs = false)
        val json = adapter.toJson(enriched)

        latestKnownState = enriched
        writePrivateStateFiles(json)

        if (writeToExternalBackup) {
            performSilentAutoBackupInternal(json, enriched, notifyUser = notifyAutoBackup)
        }
        return enriched
    }

    private fun syncMergedSettingsToDevicePrefs(
        previousLive: AppSettings,
        mergedSettings: AppSettings
    ) {
        syncSettingsToExplicitPreferences(mergedSettings)
        try {
            if (mergedSettings.strictBlockerEnabled != previousLive.strictBlockerEnabled) {
                StrictAppBlockerManager.setStrictBlockerEnabled(context, mergedSettings.strictBlockerEnabled)
            }
            if (mergedSettings.whitelistedPackages != previousLive.whitelistedPackages) {
                StrictAppBlockerManager.setWhitelistedPackages(context, mergedSettings.whitelistedPackages.toSet())
            }
        } catch (_: Exception) {
        }
        try {
            if (mergedSettings.geminiApiKey.isNotBlank() &&
                mergedSettings.geminiApiKey != previousLive.geminiApiKey
            ) {
                TomatoAiService.saveApiKey(context, mergedSettings.geminiApiKey)
            }
            if (mergedSettings.geminiModelId.isNotBlank() &&
                mergedSettings.geminiModelId != previousLive.geminiModelId
            ) {
                TomatoAiService.saveSelectedModel(
                    context,
                    TomatoGeminiModel.fromModelId(mergedSettings.geminiModelId)
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun performSilentAutoBackupInternal(
        json: String,
        enriched: AppBackupState,
        notifyUser: Boolean = true
    ) {
        if (autoBackupPaused) return
        try {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(PREFS_LAST_AUTO_BACKUP_TIME, now).commit()
            writeJsonToDocumentsFolder(
                fileName = MASTER_BACKUP_FILE_NAME,
                json = json,
                overwriteExisting = true
            )
            removeLegacyAutoBackupFileIfPresent()

            if (notifyUser) {
                val bytesSize = json.toByteArray(Charsets.UTF_8).size.toLong()
                val summary = ManualBackupExportSummary(
                    fileName = MASTER_BACKUP_FILE_NAME,
                    fileSizeBytes = bytesSize,
                    formattedFileSize = formatFileSize(bytesSize),
                    projectsCount = enriched.projects.size,
                    tasksCount = enriched.tasks.size,
                    subtasksCount = enriched.subtasks.size,
                    pomodoroSessionsCount = enriched.pomodoroSessions.size,
                    totalPrefsCount = enriched.allPreferences.values.sumOf { it.size },
                    updatedTimestamp = formatBackupTimestamp(now, true),
                    mergedState = enriched
                )
                FocusNotificationManager.showAutoBackupSuccessNotification(context, summary)
            }
        } catch (_: Exception) {
        }
    }

    private fun syncSettingsToExplicitPreferences(settings: AppSettings) {
        try {
            val editor = prefs.edit()
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
                .putString("setting_accent_color_hex", settings.accentColorHex)
                .putString("setting_language", settings.language.name)
                .putString("setting_white_noise", settings.whiteNoise.name)
                .putFloat("setting_white_noise_volume", settings.whiteNoiseVolume)
                .putBoolean("strict_blocker_enabled", settings.strictBlockerEnabled)
                .putStringSet("strict_blocker_whitelisted_packages", settings.whitelistedPackages.toSet())
                .putStringSet("strict_blocker_blocked_packages", settings.blockedPackages.toSet())
                .putLong("setting_updated_at", settings.settingsUpdatedAt)

            if (settings.geminiModelId.isNotBlank()) {
                editor.putString(TomatoAiService.PREF_GEMINI_MODEL, settings.geminiModelId)
            }
            if (settings.geminiApiKey.isNotBlank()) {
                editor.putString(TomatoAiService.PREF_GEMINI_API_KEY, settings.geminiApiKey)
            }
            editor.commit()
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
                    if (key in LOCAL_ONLY_PREF_KEYS || rawVal == null) continue
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
                    if (entry.key in LOCAL_ONLY_PREF_KEYS) continue
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

    private fun normalizeLoadedState(
        loaded: AppBackupState,
        reconcileWithLocalPrefs: Boolean = false
    ): AppBackupState {
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
                    createdAt = exp.createdAt,
                    updatedAt = exp.updatedAt
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
                    createdAt = sub.createdAt,
                    updatedAt = sub.updatedAt
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

        val reconciledSettings = if (reconcileWithLocalPrefs) {
            val spStrictEnabled = prefs.getBoolean("strict_blocker_enabled", loaded.settings.strictBlockerEnabled)
            val spWhitelist = prefs.getStringSet("strict_blocker_whitelisted_packages", null)?.toList()
            val spBlocked = prefs.getStringSet("strict_blocker_blocked_packages", null)?.toList()
            val mergedWhitelist = (loaded.settings.whitelistedPackages + spWhitelist.orEmpty()).distinct().sorted()
            val mergedBlocked = (loaded.settings.blockedPackages + spBlocked.orEmpty()).distinct().sorted()
            val spApiKey = prefs.getString(TomatoAiService.PREF_GEMINI_API_KEY, null)?.trim().orEmpty()
            val spModel = prefs.getString(TomatoAiService.PREF_GEMINI_MODEL, null)?.trim().orEmpty()
            loaded.settings.copy(
                strictBlockerEnabled = loaded.settings.strictBlockerEnabled || spStrictEnabled,
                whitelistedPackages = mergedWhitelist,
                blockedPackages = mergedBlocked,
                geminiApiKey = loaded.settings.geminiApiKey.ifBlank { spApiKey },
                geminiModelId = loaded.settings.geminiModelId.ifBlank {
                    spModel.ifBlank { TomatoGeminiModel.FLASH_2_5.modelId }
                }
            )
        } else {
            loaded.settings.copy(
                whitelistedPackages = loaded.settings.whitelistedPackages.distinct().sorted(),
                blockedPackages = loaded.settings.blockedPackages.distinct().sorted()
            )
        }

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

        val (computedCurrentStreak, computedBestStreak) = BackupSmartMerger.computeDailyStreaks(combinedSessions)
        val normalizedForest = loaded.forestState.copy(
            currentDailyStreak = maxOf(computedCurrentStreak, loaded.forestState.currentDailyStreak),
            bestDailyStreak = maxOf(computedBestStreak, loaded.forestState.bestDailyStreak, computedCurrentStreak)
        )

        return loaded.copy(
            backupVersion = 4,
            tasks = normalizedTasks,
            subtasks = allSubtaskExports,
            projects = normalizedProjects,
            folders = normalizedFolders,
            focusRecords = combinedSessions,
            pomodoroSessions = combinedSessions,
            focusStats = stats,
            settings = reconciledSettings,
            forestState = normalizedForest
        )
    }

    fun enrichStateForFullExport(
        state: AppBackupState,
        reconcileWithLocalPrefs: Boolean = true
    ): AppBackupState {
        val spApiKey = prefs.getString(TomatoAiService.PREF_GEMINI_API_KEY, null)?.trim().orEmpty()
        val spModel = prefs.getString(TomatoAiService.PREF_GEMINI_MODEL, null)?.trim().orEmpty()
        val hydratedSettings = state.settings.copy(
            geminiApiKey = state.settings.geminiApiKey.ifBlank { spApiKey },
            geminiModelId = state.settings.geminiModelId.ifBlank {
                spModel.ifBlank { TomatoGeminiModel.FLASH_2_5.modelId }
            }
        )
        val normalized = normalizeLoadedState(
            state.copy(settings = hydratedSettings),
            reconcileWithLocalPrefs = reconcileWithLocalPrefs
        )
        syncSettingsToExplicitPreferences(normalized.settings)
        val allPrefs = collectAllSharedPreferencesSnapshot()
        return normalized.copy(
            backupVersion = 4,
            exportedAt = System.currentTimeMillis(),
            allPreferences = allPrefs
        )
    }

    /**
     * Manual backup export:
     * Enforces Empty-State Guard and Full-State Smart Merge (reads existing FocusTomato_backup.json first,
     * unions with current live state respecting local deletedIds, writes merged result in-place,
     * updates private state, and returns mergedState inside ManualBackupExportSummary).
     */
    suspend fun exportManualBackupWithSummary(
        state: AppBackupState,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<ManualBackupExportSummary> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            return@withContext try {
                val localDeletedIds = getLocalDeletedIds()
                val normalizedLive = normalizeLoadedState(state, reconcileWithLocalPrefs = false)
                val existingExternal = readExistingExternalBackup()
                val externalIsNonEmpty = existingExternal != null &&
                    BackupSmartMerger.isBackupStateNonEmpty(existingExternal)
                val liveIsEmpty = BackupSmartMerger.isAppStateEffectivelyEmpty(normalizedLive)

                if (liveIsEmpty && externalIsNonEmpty) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Existing non-empty backup detected on device. Restore it before exporting an empty state."
                        )
                    )
                }

                val mergedBase = if (externalIsNonEmpty && existingExternal != null) {
                    val merged = BackupSmartMerger.smartMerge(
                        live = normalizedLive,
                        existingDisk = existingExternal,
                        localDeletedIds = localDeletedIds
                    )
                    if (merged.settings != normalizedLive.settings) {
                        syncMergedSettingsToDevicePrefs(
                            previousLive = normalizedLive.settings,
                            mergedSettings = merged.settings
                        )
                    }
                    merged
                } else {
                    normalizedLive
                }

                val fileName = MASTER_BACKUP_FILE_NAME
                val enriched = enrichStateForFullExport(mergedBase, reconcileWithLocalPrefs = false)
                    .copy(exportedAt = nowMillis)
                val json = adapter.toJson(enriched)
                val bytes = json.toByteArray(Charsets.UTF_8)

                writeJsonToDocumentsFolder(
                    fileName = fileName,
                    json = json,
                    overwriteExisting = true
                )
                removeLegacyAutoBackupFileIfPresent()

                latestKnownState = enriched
                writePrivateStateFiles(json)
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
                    updatedTimestamp = formatBackupTimestamp(nowMillis, true),
                    mergedState = enriched
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
            autoBackupPaused = false
            markStartFreshChosen(false)

            val normalizedImported = normalizeLoadedState(imported, reconcileWithLocalPrefs = false)
            if (mode == BackupImportMode.REPLACE) {
                clearLocalDeletedIds()
            } else {
                val restoredIds = buildList {
                    addAll(normalizedImported.tasks.map { it.id })
                    addAll(normalizedImported.tasks.flatMap { t -> t.subtasks.map { it.id } })
                    addAll(normalizedImported.projects.map { it.id })
                    addAll(normalizedImported.folders.map { it.id })
                    addAll(normalizedImported.tags.map { it.id })
                    addAll(normalizedImported.focusRecords.map { it.id })
                    addAll(normalizedImported.forestState.plantedHistory.map { it.id })
                }
                removeDeletedIds(restoredIds)
            }

            val targetState = when (mode) {
                BackupImportMode.MERGE -> BackupSmartMerger.smartMerge(
                    live = normalizeLoadedState(current, reconcileWithLocalPrefs = false),
                    existingDisk = normalizedImported,
                    localDeletedIds = getLocalDeletedIds()
                )
                BackupImportMode.REPLACE -> normalizedImported
            }
            if (targetState.allPreferences.isNotEmpty()) {
                restoreAllSharedPreferencesFromSnapshot(targetState.allPreferences)
            }
            syncSettingsToExplicitPreferences(targetState.settings)
            try {
                StrictAppBlockerManager.setStrictBlockerEnabled(context, targetState.settings.strictBlockerEnabled)
                StrictAppBlockerManager.setWhitelistedPackages(context, targetState.settings.whitelistedPackages.toSet())
            } catch (_: Exception) {
            }
            try {
                if (targetState.settings.geminiApiKey.isNotBlank()) {
                    TomatoAiService.saveApiKey(context, targetState.settings.geminiApiKey)
                }
                if (targetState.settings.geminiModelId.isNotBlank()) {
                    TomatoAiService.saveSelectedModel(
                        context,
                        TomatoGeminiModel.fromModelId(targetState.settings.geminiModelId)
                    )
                }
            } catch (_: Exception) {
            }
            val finalState = normalizeLoadedState(targetState, reconcileWithLocalPrefs = false)
            latestKnownState = finalState
            saveStateInternal(
                state = finalState,
                notifyAutoBackup = false,
                writeToExternalBackup = true,
                skipExternalSmartMerge = (mode == BackupImportMode.REPLACE)
            )
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

        // 1. Clean up any numbered duplicate files like "FocusTomato_backup (1).json" on filesystem
        cleanupFilesystemDuplicateBackupFiles(fileName)

        // 2. Direct persistent public path write FIRST:
        //    /storage/emulated/0/Documents/FocusTomato/FocusTomato_backup.json
        //    Works directly with All Files Access (MANAGE_EXTERNAL_STORAGE) on Android 11+,
        //    legacy storage on Android <= 10, and Robolectric tests — guarantees zero "(1)" duplicates!
        val directWrittenFile = tryWriteDirectlyToPublicFolder(fileName, bytes)
        if (directWrittenFile != null) {
            return Uri.fromFile(directWrittenFile)
        }

        // 3. Fallback to MediaStore on Android 10+ ONLY if MANAGE_EXTERNAL_STORAGE has not yet been granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_FOLDER_NAME/"
            val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            if (overwriteExisting) {
                var targetExistingUri: Uri? = null

                try {
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf("FocusTomato_backup%", LEGACY_AUTO_BACKUP_FILE_NAME)
                    resolver.query(
                        collectionUri,
                        arrayOf(
                            MediaStore.MediaColumns._ID,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.RELATIVE_PATH
                        ),
                        selection,
                        selectionArgs,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val displayName = cursor.getString(1).orEmpty()
                            val rowRelPath = cursor.getString(2).orEmpty()
                            if (rowRelPath.isNotBlank() && !rowRelPath.contains(BACKUP_FOLDER_NAME, ignoreCase = true)) {
                                continue
                            }
                            val rowUri = ContentUris.withAppendedId(collectionUri, id)

                            if (displayName == fileName) {
                                if (targetExistingUri == null) {
                                    targetExistingUri = rowUri
                                } else {
                                    try {
                                        resolver.delete(rowUri, null, null)
                                    } catch (_: Exception) {
                                    }
                                }
                            } else {
                                try {
                                    resolver.delete(rowUri, null, null)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                if (targetExistingUri == null) {
                    val savedUriStr = prefs.getString(PREFS_MASTER_BACKUP_URI, null)
                    if (!savedUriStr.isNullOrBlank()) {
                        try {
                            val candidateUri = Uri.parse(savedUriStr)
                            var isRenamedDuplicate = false
                            resolver.query(
                                candidateUri,
                                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                                null,
                                null,
                                null
                            )?.use { c ->
                                if (c.moveToFirst()) {
                                    val name = c.getString(1).orEmpty()
                                    if (name.isNotBlank() && name != fileName) {
                                        isRenamedDuplicate = true
                                    }
                                }
                            }
                            if (isRenamedDuplicate) {
                                resolver.delete(candidateUri, null, null)
                            } else {
                                targetExistingUri = candidateUri
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                if (targetExistingUri != null) {
                    val wroteInPlace = try {
                        (resolver.openOutputStream(targetExistingUri!!, "wt")
                            ?: resolver.openOutputStream(targetExistingUri!!, "rwt")
                            ?: resolver.openOutputStream(targetExistingUri!!, "w"))?.use { out ->
                            out.write(bytes)
                            out.flush()
                            true
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }
                    if (wroteInPlace) {
                        prefs.edit().putString(PREFS_MASTER_BACKUP_URI, targetExistingUri.toString()).commit()
                        return targetExistingUri!!
                    } else {
                        try {
                            resolver.delete(targetExistingUri!!, null, null)
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val insertedUri = try {
                resolver.insert(collectionUri, values)
            } catch (_: Exception) {
                null
            }

            if (insertedUri != null) {
                val streamWritten = try {
                    (resolver.openOutputStream(insertedUri, "wt")
                        ?: resolver.openOutputStream(insertedUri, "rwt")
                        ?: resolver.openOutputStream(insertedUri))?.use { out ->
                        out.write(bytes)
                        out.flush()
                        true
                    } ?: false
                } catch (_: Exception) {
                    false
                }

                if (streamWritten) {
                    prefs.edit().putString(PREFS_MASTER_BACKUP_URI, insertedUri.toString()).commit()
                    return insertedUri
                } else {
                    try {
                        resolver.delete(insertedUri, null, null)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // 4. Final fallback to app-private Documents mirror if external storage is inaccessible
        val fallbackFile = writeMirrorDocumentsFileOrThrow(fileName, bytes)
        return Uri.fromFile(fallbackFile)
    }

    private fun tryWriteDirectlyToPublicFolder(fileName: String, bytes: ByteArray): File? {
        return try {
            val targetDir = getPublicBackupDirectory()
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            if (!targetDir.exists() || !targetDir.isDirectory) return null

            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile, false).use { out ->
                out.write(bytes)
                out.flush()
            }
            if (targetFile.exists() && targetFile.length() == bytes.size.toLong()) {
                targetFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupFilesystemDuplicateBackupFiles(canonicalFileName: String) {
        try {
            val dir = getPublicBackupDirectory()
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile &&
                        file.name != canonicalFileName &&
                        (file.name.startsWith("FocusTomato_backup") ||
                            file.name.startsWith("focus_zohayr_backup") ||
                            file.name == LEGACY_AUTO_BACKUP_FILE_NAME)
                    ) {
                        try {
                            file.delete()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    @Throws(IOException::class)
    private fun writeMirrorDocumentsFileOrThrow(fileName: String, bytes: ByteArray): File {
        val internalFallbackDir = File(File(context.filesDir, "Documents"), BACKUP_FOLDER_NAME)
        internalFallbackDir.mkdirs()
        val internalFile = File(internalFallbackDir, fileName)
        FileOutputStream(internalFile, false).use { out ->
            out.write(bytes)
            out.flush()
        }
        return internalFile
    }

    private fun removeLegacyAutoBackupFileIfPresent() {
        cleanupFilesystemDuplicateBackupFiles(MASTER_BACKUP_FILE_NAME)
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
                adapter.fromJson(file.readText(Charsets.UTF_8))?.let {
                    normalizeLoadedState(it, reconcileWithLocalPrefs = false)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readStateFromPrefs(): AppBackupState? {
        return try {
            val raw = prefs.getString(PREFS_STATE_KEY, null) ?: return null
            adapter.fromJson(raw)?.let {
                normalizeLoadedState(it, reconcileWithLocalPrefs = false)
            }
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

    fun mergeBackupStates(
        current: AppBackupState,
        imported: AppBackupState,
        localDeletedIds: Set<String> = getLocalDeletedIds()
    ): AppBackupState {
        val normCurrent = normalizeLoadedState(current, reconcileWithLocalPrefs = false)
        val normImported = normalizeLoadedState(imported, reconcileWithLocalPrefs = false)
        val merged = BackupSmartMerger.smartMerge(
            live = normCurrent,
            existingDisk = normImported,
            localDeletedIds = localDeletedIds
        )
        return normalizeLoadedState(merged, reconcileWithLocalPrefs = false)
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
                normalizeLoadedState(
                    parsed.copy(settings = hydratedSettings),
                    reconcileWithLocalPrefs = false
                )
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
            accentColorHex = entries["setting_accent_color_hex"]?.stringValue ?: base.accentColorHex,
            language = entries["setting_language"]?.stringValue?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: base.language,
            whiteNoise = entries["setting_white_noise"]?.stringValue?.let {
                runCatching { WhiteNoiseType.valueOf(it) }.getOrNull()
            } ?: base.whiteNoise,
            whiteNoiseVolume = entries["setting_white_noise_volume"]?.floatValue ?: base.whiteNoiseVolume,
            strictBlockerEnabled = entries["strict_blocker_enabled"]?.booleanValue ?: base.strictBlockerEnabled,
            whitelistedPackages = entries["strict_blocker_whitelisted_packages"]?.stringSetValue ?: base.whitelistedPackages,
            blockedPackages = entries["strict_blocker_blocked_packages"]?.stringSetValue ?: base.blockedPackages,
            geminiModelId = base.geminiModelId.ifBlank {
                entries[TomatoAiService.PREF_GEMINI_MODEL]?.stringValue ?: TomatoGeminiModel.FLASH_2_5.modelId
            },
            geminiApiKey = base.geminiApiKey.ifBlank {
                entries[TomatoAiService.PREF_GEMINI_API_KEY]?.stringValue.orEmpty()
            },
            settingsUpdatedAt = maxOf(
                base.settingsUpdatedAt,
                entries["setting_updated_at"]?.longValue ?: 0L
            )
        )
    }

    fun hasAllFilesAccess(): Boolean = SystemPermissionsHelper.isAllFilesAccessGranted(context)
}
