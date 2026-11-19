package com.example.data

import com.example.model.AppBackupState
import com.example.model.AppSettings
import com.example.model.ExportedPrefEntry
import com.example.model.FocusRecord
import com.example.model.PlantSpecies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackupSmartMerger {
    private val DEFAULT_PROJECT_IDS = setOf("proj_study", "proj_skills", "proj_routine", "proj_side")
    private val DEFAULT_FOLDER_IDS = setOf("folder_acad", "folder_life")
    private val DEFAULT_TAG_IDS = setOf("tag_imp", "tag_rev", "tag_read", "tag_code")

    fun hasDefaultUnmodifiedSettings(settings: AppSettings): Boolean {
        val defaults = AppSettings()
        return settings.pomodoroMinutes == defaults.pomodoroMinutes &&
            settings.shortBreakMinutes == defaults.shortBreakMinutes &&
            settings.longBreakMinutes == defaults.longBreakMinutes &&
            settings.longBreakInterval == defaults.longBreakInterval &&
            settings.autoStartBreaks == defaults.autoStartBreaks &&
            settings.autoStartNextPomodoro == defaults.autoStartNextPomodoro &&
            settings.disableBreaks == defaults.disableBreaks &&
            settings.alarmEnabled == defaults.alarmEnabled &&
            settings.enableTaskReminders == defaults.enableTaskReminders &&
            settings.persistentTimerNotification == defaults.persistentTimerNotification &&
            settings.alarmSoundAndVibration == defaults.alarmSoundAndVibration &&
            settings.darkTheme == defaults.darkTheme &&
            settings.accentColorHex == defaults.accentColorHex &&
            settings.language == defaults.language &&
            settings.whiteNoise == defaults.whiteNoise &&
            settings.whiteNoiseVolume == defaults.whiteNoiseVolume &&
            settings.strictBlockerEnabled == defaults.strictBlockerEnabled &&
            settings.whitelistedPackages.isEmpty() &&
            settings.blockedPackages.isEmpty() &&
            settings.geminiModelId == defaults.geminiModelId &&
            settings.geminiApiKey.isBlank()
    }

    fun isAppStateEffectivelyEmpty(state: AppBackupState): Boolean {
        val hasNoTasks = state.tasks.isEmpty()
        val hasNoSessions = state.focusRecords.isEmpty() && state.pomodoroSessions.isEmpty()
        val hasNoTrees = state.forestState.plantedHistory.isEmpty() &&
            state.forestState.sunlightPoints == 0 &&
            state.forestState.currentGrowthMinutes == 0
        val hasOnlyDefaultOrg = state.projects.all { it.id in DEFAULT_PROJECT_IDS } &&
            state.folders.all { it.id in DEFAULT_FOLDER_IDS } &&
            state.tags.all { it.id in DEFAULT_TAG_IDS }
        val hasUnmodifiedSettings = state.settings.settingsUpdatedAt == 0L &&
            hasDefaultUnmodifiedSettings(state.settings)
        return hasNoTasks && hasNoSessions && hasNoTrees && hasOnlyDefaultOrg && hasUnmodifiedSettings
    }

    fun isBackupStateNonEmpty(backup: AppBackupState): Boolean {
        return backup.tasks.isNotEmpty() ||
            backup.focusRecords.isNotEmpty() ||
            backup.pomodoroSessions.isNotEmpty() ||
            backup.forestState.plantedHistory.isNotEmpty() ||
            backup.forestState.sunlightPoints > 0 ||
            backup.forestState.currentGrowthMinutes > 0 ||
            backup.projects.any { it.id !in DEFAULT_PROJECT_IDS } ||
            backup.folders.any { it.id !in DEFAULT_FOLDER_IDS } ||
            backup.tags.any { it.id !in DEFAULT_TAG_IDS } ||
            backup.settings.settingsUpdatedAt > 0L ||
            !hasDefaultUnmodifiedSettings(backup.settings)
    }

    /**
     * Computes (currentDailyStreak, bestDailyStreak) from the combined focus session history.
     */
    fun computeDailyStreaks(
        sessions: List<FocusRecord>,
        todayEpochDay: Long = DateUtils.todayEpochDay()
    ): Pair<Int, Int> {
        if (sessions.isEmpty()) return 0 to 0

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val activeDays = sessions.mapNotNull { rec ->
            val fromKey = rec.dateKey.trim().takeIf { it.isNotEmpty() }?.let { key ->
                runCatching {
                    val d = parser.parse(key) ?: return@let null
                    d.time / 86_400_000L
                }.getOrNull()
            }
            fromKey ?: DateUtils.epochDayFromMillis(rec.startedAtEpochMillis)
        }.distinct().sorted()

        if (activeDays.isEmpty()) return 0 to 0

        var bestStreak = 1
        var runLength = 1
        for (i in 1 until activeDays.size) {
            if (activeDays[i] == activeDays[i - 1] + 1L) {
                runLength++
                if (runLength > bestStreak) bestStreak = runLength
            } else {
                runLength = 1
            }
        }

        val lastDay = activeDays.last()
        val currentStreak = if (lastDay == todayEpochDay || lastDay == todayEpochDay - 1L) {
            var cur = 1
            for (i in activeDays.lastIndex downTo 1) {
                if (activeDays[i] - activeDays[i - 1] == 1L) {
                    cur++
                } else {
                    break
                }
            }
            cur
        } else {
            0
        }

        return currentStreak to bestStreak
    }

    /**
     * Full-State Smart Merge:
     * Unions all collections by ID keeping latest updatedAt on conflicts, applies local soft-delete tombstones,
     * preserves max/cumulative gamification stats & streaks, and resolves settings by settingsUpdatedAt timestamp.
     */
    fun smartMerge(
        live: AppBackupState,
        existingDisk: AppBackupState,
        localDeletedIds: Set<String>
    ): AppBackupState {
        // 1. Tasks & Subtasks (Union by ID, latest updatedAtEpochMillis wins, excluding localDeletedIds)
        val liveTasksById = live.tasks.associateBy { it.id }
        val diskTasksById = existingDisk.tasks.associateBy { it.id }
        val allTaskIds = (liveTasksById.keys + diskTasksById.keys)
            .filterNot { it in localDeletedIds }

        val mergedTasks = allTaskIds.mapNotNull { id ->
            val liveTask = liveTasksById[id]
            val diskTask = diskTasksById[id]
            when {
                liveTask != null && diskTask != null -> {
                    val primary = if (diskTask.updatedAtEpochMillis > liveTask.updatedAtEpochMillis) {
                        diskTask
                    } else {
                        liveTask
                    }
                    // Union subtasks by ID, keeping latest updatedAt, excluding localDeletedIds
                    val liveSubs = liveTask.subtasks.associateBy { it.id }
                    val diskSubs = diskTask.subtasks.associateBy { it.id }
                    val mergedSubtasks = (liveSubs.keys + diskSubs.keys)
                        .filterNot { it in localDeletedIds }
                        .mapNotNull { subId ->
                            val ls = liveSubs[subId]
                            val ds = diskSubs[subId]
                            when {
                                ls != null && ds != null -> {
                                    val lTs = maxOf(ls.updatedAt, ls.createdAt)
                                    val dTs = maxOf(ds.updatedAt, ds.createdAt)
                                    if (dTs > lTs) ds else ls
                                }
                                ls != null -> ls
                                else -> ds
                            }
                        }
                        .sortedBy { it.sortOrder }

                    primary.copy(
                        subtasks = mergedSubtasks,
                        completedPomodoros = maxOf(liveTask.completedPomodoros, diskTask.completedPomodoros),
                        elapsedFocusMinutes = maxOf(liveTask.elapsedFocusMinutes, diskTask.elapsedFocusMinutes)
                    )
                }
                liveTask != null -> liveTask.copy(
                    subtasks = liveTask.subtasks.filterNot { it.id in localDeletedIds }
                )
                diskTask != null -> diskTask.copy(
                    subtasks = diskTask.subtasks.filterNot { it.id in localDeletedIds }
                )
                else -> null
            }
        }

        // 2. Projects (Union by ID, latest updatedAt wins, excluding localDeletedIds)
        val liveProjectsById = live.projects.associateBy { it.id }
        val diskProjectsById = existingDisk.projects.associateBy { it.id }
        val mergedProjects = (liveProjectsById.keys + diskProjectsById.keys)
            .filterNot { it in localDeletedIds }
            .mapNotNull { id ->
                val lp = liveProjectsById[id]
                val dp = diskProjectsById[id]
                when {
                    lp != null && dp != null -> {
                        val lTs = maxOf(lp.updatedAt, lp.createdAt)
                        val dTs = maxOf(dp.updatedAt, dp.createdAt)
                        if (dTs > lTs) dp else lp
                    }
                    lp != null -> lp
                    else -> dp
                }
            }

        // 3. Folders (Union by ID, latest updatedAt wins, excluding localDeletedIds)
        val liveFoldersById = live.folders.associateBy { it.id }
        val diskFoldersById = existingDisk.folders.associateBy { it.id }
        val mergedFolders = (liveFoldersById.keys + diskFoldersById.keys)
            .filterNot { it in localDeletedIds }
            .mapNotNull { id ->
                val lf = liveFoldersById[id]
                val df = diskFoldersById[id]
                when {
                    lf != null && df != null -> {
                        val lTs = maxOf(lf.updatedAt, lf.createdAt)
                        val dTs = maxOf(df.updatedAt, df.createdAt)
                        if (dTs > lTs) df else lf
                    }
                    lf != null -> lf
                    else -> df
                }
            }

        // 4. Tags (Union by ID, latest updatedAt wins, excluding localDeletedIds)
        val liveTagsById = live.tags.associateBy { it.id }
        val diskTagsById = existingDisk.tags.associateBy { it.id }
        val mergedTags = (liveTagsById.keys + diskTagsById.keys)
            .filterNot { it in localDeletedIds }
            .mapNotNull { id ->
                val lt = liveTagsById[id]
                val dt = diskTagsById[id]
                when {
                    lt != null && dt != null -> if (dt.updatedAt > lt.updatedAt) dt else lt
                    lt != null -> lt
                    else -> dt
                }
            }

        // 5. Pomodoro History & Focus Session Logs (Union by ID, never decreasing total focus time)
        val combinedRawRecords = existingDisk.focusRecords +
            existingDisk.pomodoroSessions +
            live.focusRecords +
            live.pomodoroSessions
        val mergedRecords = combinedRawRecords
            .filterNot { it.id in localDeletedIds }
            .groupBy { it.id }
            .map { (_, copies) ->
                copies.maxWithOrNull(
                    compareBy<FocusRecord> { it.endTimeEpochMillis }
                        .thenBy { it.durationSeconds }
                        .thenBy { it.durationMinutes }
                ) ?: copies.first()
            }
            .sortedByDescending { it.startedAtEpochMillis }

        // 6. Settings, Theme Colors, Timer Preferences, Gemini AI & Blocker Lists (Smart Timestamp Resolution)
        val liveSettings = live.settings
        val diskSettings = existingDisk.settings
        val useDiskSettings = when {
            liveSettings.settingsUpdatedAt == 0L && hasDefaultUnmodifiedSettings(liveSettings) -> {
                diskSettings.settingsUpdatedAt > 0L || !hasDefaultUnmodifiedSettings(diskSettings)
            }
            diskSettings.settingsUpdatedAt > liveSettings.settingsUpdatedAt -> true
            liveSettings.settingsUpdatedAt > diskSettings.settingsUpdatedAt -> false
            else -> false
        }

        val baseSettings = if (useDiskSettings) diskSettings else liveSettings
        val otherSettings = if (useDiskSettings) liveSettings else diskSettings

        val mergedWhitelist = (diskSettings.whitelistedPackages + liveSettings.whitelistedPackages)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val mergedBlocked = (diskSettings.blockedPackages + liveSettings.blockedPackages)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val resolvedApiKey = baseSettings.geminiApiKey.ifBlank { otherSettings.geminiApiKey }
        val resolvedSettingsTimestamp = maxOf(liveSettings.settingsUpdatedAt, diskSettings.settingsUpdatedAt)

        val mergedSettings = baseSettings.copy(
            whitelistedPackages = mergedWhitelist,
            blockedPackages = mergedBlocked,
            geminiApiKey = resolvedApiKey,
            strictBlockerEnabled = if (useDiskSettings) {
                diskSettings.strictBlockerEnabled
            } else {
                liveSettings.strictBlockerEnabled
            },
            settingsUpdatedAt = resolvedSettingsTimestamp
        )

        // 7. Progress & Gamification (Max / Cumulative Preservation)
        val mergedPlantedHistory = (live.forestState.plantedHistory + existingDisk.forestState.plantedHistory)
            .filterNot { it.id in localDeletedIds }
            .distinctBy { it.id }
            .sortedByDescending { it.plantedAtEpochMillis }
        val mergedUnlockedSpecies = (live.forestState.unlockedSpecies + existingDisk.forestState.unlockedSpecies)
            .distinct()
        val mergedSunlightPoints = maxOf(live.forestState.sunlightPoints, existingDisk.forestState.sunlightPoints)
        val mergedGrowthMinutes = maxOf(
            live.forestState.currentGrowthMinutes,
            existingDisk.forestState.currentGrowthMinutes
        )
        val (computedCurrentStreak, computedBestStreak) = computeDailyStreaks(mergedRecords)
        val mergedCurrentStreak = maxOf(
            computedCurrentStreak,
            live.forestState.currentDailyStreak,
            existingDisk.forestState.currentDailyStreak
        )
        val mergedBestStreak = maxOf(
            computedBestStreak,
            live.forestState.bestDailyStreak,
            existingDisk.forestState.bestDailyStreak,
            mergedCurrentStreak
        )
        val resolvedActiveSpecies = when {
            useDiskSettings -> existingDisk.forestState.activeSpecies
            live.forestState.activeSpecies != PlantSpecies.PINE -> live.forestState.activeSpecies
            else -> existingDisk.forestState.activeSpecies
        }.let { sp -> if (sp in mergedUnlockedSpecies) sp else PlantSpecies.PINE }

        val mergedForest = live.forestState.copy(
            sunlightPoints = mergedSunlightPoints,
            currentGrowthMinutes = mergedGrowthMinutes,
            activeSpecies = resolvedActiveSpecies,
            unlockedSpecies = mergedUnlockedSpecies,
            plantedHistory = mergedPlantedHistory,
            currentDailyStreak = mergedCurrentStreak,
            bestDailyStreak = mergedBestStreak
        )

        // 8. Merge allPreferences maps (winning settings side wins key collisions)
        val mergedAllPrefs = mergePreferenceMaps(
            primary = if (useDiskSettings) existingDisk.allPreferences else live.allPreferences,
            secondary = if (useDiskSettings) live.allPreferences else existingDisk.allPreferences
        )

        return live.copy(
            backupVersion = 4,
            exportedAt = maxOf(live.exportedAt, existingDisk.exportedAt, System.currentTimeMillis()),
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedRecords,
            pomodoroSessions = mergedRecords,
            settings = mergedSettings,
            allPreferences = mergedAllPrefs,
            forestState = mergedForest,
            persistedTimer = live.persistedTimer ?: existingDisk.persistedTimer,
            deletedIds = localDeletedIds.sorted()
        )
    }

    private fun mergePreferenceMaps(
        primary: Map<String, List<ExportedPrefEntry>>,
        secondary: Map<String, List<ExportedPrefEntry>>
    ): Map<String, List<ExportedPrefEntry>> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val result = linkedMapOf<String, List<ExportedPrefEntry>>()
        val allNames = (primary.keys + secondary.keys).distinct()
        for (name in allNames) {
            val secMap = secondary[name].orEmpty().associateBy { it.key }
            val priMap = primary[name].orEmpty().associateBy { it.key }
            val combined = LinkedHashMap<String, ExportedPrefEntry>(secMap)
            combined.putAll(priMap)
            if (combined.isNotEmpty()) {
                result[name] = combined.values.sortedBy { it.key }
            }
        }
        return result
    }
}
