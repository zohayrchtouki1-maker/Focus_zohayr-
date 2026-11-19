package com.example.data

import com.example.model.AppBackupState
import com.example.model.AppSettings
import com.example.model.ExportedPrefEntry
import com.example.model.FocusRecord
import com.example.model.FolderItem
import com.example.model.ForestState
import com.example.model.PlantSpecies
import com.example.model.PlantedTree
import com.example.model.ProjectItem
import com.example.model.SubtaskItem
import com.example.model.TagItem
import com.example.model.TaskItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-State Smart Merge & Empty-State Guard Engine for FocusTomato local backup.
 *
 * Enforces:
 * 1. Empty-State Guard detection so fresh installs never overwrite existing backups.
 * 2. Collection Unions by unique ID (keeping latest updatedAt on conflicts) while honoring local soft-delete tombstones.
 * 3. Cumulative / Max preservation for Forest growth, sunlight points, unlocked species, planted trees, and daily streaks.
 * 4. Smart timestamp resolution for AppSettings (inheriting backup settings on fresh installs, honoring newer user edits).
 */
object BackupSmartMerger {

    private val defaultProjectIds = setOf("proj_study", "proj_skills", "proj_routine", "proj_side")
    private val defaultFolderIds = setOf("folder_acad", "folder_life")
    private val defaultTagIds = setOf("tag_imp", "tag_rev", "tag_read", "tag_code")

    fun areSettingsDefaultUnmodified(settings: AppSettings): Boolean {
        if (settings.settingsUpdatedAt > 0L) return false
        val def = AppSettings()
        return settings.pomodoroMinutes == def.pomodoroMinutes &&
            settings.shortBreakMinutes == def.shortBreakMinutes &&
            settings.longBreakMinutes == def.longBreakMinutes &&
            settings.longBreakInterval == def.longBreakInterval &&
            settings.autoStartBreaks == def.autoStartBreaks &&
            settings.autoStartNextPomodoro == def.autoStartNextPomodoro &&
            settings.disableBreaks == def.disableBreaks &&
            settings.alarmEnabled == def.alarmEnabled &&
            settings.enableTaskReminders == def.enableTaskReminders &&
            settings.persistentTimerNotification == def.persistentTimerNotification &&
            settings.alarmSoundAndVibration == def.alarmSoundAndVibration &&
            settings.lockedInRecentsConfirmed == def.lockedInRecentsConfirmed &&
            settings.darkTheme == def.darkTheme &&
            settings.accentColorHex.equals(def.accentColorHex, ignoreCase = true) &&
            settings.language == def.language &&
            settings.whiteNoise == def.whiteNoise &&
            settings.whiteNoiseVolume == def.whiteNoiseVolume &&
            settings.strictBlockerEnabled == def.strictBlockerEnabled &&
            settings.whitelistedPackages.isEmpty() &&
            settings.blockedPackages.isEmpty() &&
            settings.geminiApiKey.isBlank() &&
            settings.geminiModelId == def.geminiModelId
    }

    /**
     * Returns true if the current app state is completely empty (fresh install/reinstall:
     * 0 tasks, 0 sessions, no custom projects/folders/tags, no tree progress, and default unmodified settings).
     */
    fun isAppStateEffectivelyEmpty(state: AppBackupState): Boolean {
        val hasTasks = state.tasks.isNotEmpty()
        val hasSessions = state.focusRecords.isNotEmpty() || state.pomodoroSessions.isNotEmpty()
        val hasCustomProjects = state.projects.any { it.id !in defaultProjectIds }
        val hasCustomFolders = state.folders.any { it.id !in defaultFolderIds }
        val hasCustomTags = state.tags.any { it.id !in defaultTagIds }
        val hasForestProgress = state.forestState.plantedHistory.isNotEmpty() ||
            state.forestState.sunlightPoints > 0 ||
            state.forestState.currentGrowthMinutes > 0 ||
            state.forestState.unlockedSpecies.any { it != PlantSpecies.PINE }
        val hasModifiedSettings = !areSettingsDefaultUnmodified(state.settings)

        return !hasTasks &&
            !hasSessions &&
            !hasCustomProjects &&
            !hasCustomFolders &&
            !hasCustomTags &&
            !hasForestProgress &&
            !hasModifiedSettings
    }

    fun isBackupStateNonEmpty(state: AppBackupState): Boolean {
        return !isAppStateEffectivelyEmpty(state)
    }

    /**
     * Performs a contradiction-free Full-State Smart Merge combining [live] app state with [existingDisk] backup.
     * Any item whose ID is in [localDeletedIds] is intentionally excluded so deleted items do not reappear.
     */
    fun smartMerge(
        live: AppBackupState,
        existingDisk: AppBackupState,
        localDeletedIds: Set<String> = emptySet()
    ): AppBackupState {
        val deleted = localDeletedIds.filter { it.isNotBlank() }.toSet()

        val mergedTasks = mergeTasks(live.tasks, existingDisk.tasks, deleted)
        val mergedProjects = mergeProjects(live.projects, existingDisk.projects, deleted)
        val mergedFolders = mergeFolders(live.folders, existingDisk.folders, deleted)
        val mergedTags = mergeTags(live.tags, existingDisk.tags, deleted)

        val liveSessions = (live.focusRecords + live.pomodoroSessions).distinctBy { it.id }
        val diskSessions = (existingDisk.focusRecords + existingDisk.pomodoroSessions).distinctBy { it.id }
        val mergedSessions = mergeFocusRecords(liveSessions, diskSessions, deleted)

        val mergedForest = mergeForestState(
            live = live.forestState,
            disk = existingDisk.forestState,
            mergedSessions = mergedSessions,
            deletedIds = deleted
        )

        val mergedSettings = mergeSettings(live.settings, existingDisk.settings)
        val mergedPrefs = mergeSharedPreferencesSnapshots(
            livePrefs = live.allPreferences,
            diskPrefs = existingDisk.allPreferences,
            preferDiskSettings = shouldInheritDiskSettings(live.settings, existingDisk.settings)
        )

        val mergedPersistedTimer = live.persistedTimer ?: existingDisk.persistedTimer

        return live.copy(
            backupVersion = 4,
            exportedAt = maxOf(live.exportedAt, existingDisk.exportedAt, System.currentTimeMillis()),
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedSessions,
            pomodoroSessions = mergedSessions,
            settings = mergedSettings,
            allPreferences = mergedPrefs,
            forestState = mergedForest,
            persistedTimer = mergedPersistedTimer,
            deletedIds = emptyList()
        )
    }

    private fun mergeTasks(
        liveTasks: List<TaskItem>,
        diskTasks: List<TaskItem>,
        deletedIds: Set<String>
    ): List<TaskItem> {
        val map = linkedMapOf<String, TaskItem>()

        for (task in diskTasks) {
            if (task.id in deletedIds) continue
            map[task.id] = task.copy(
                subtasks = task.subtasks.filterNot { it.id in deletedIds }
            )
        }

        for (task in liveTasks) {
            if (task.id in deletedIds) continue
            val existing = map[task.id]
            if (existing == null) {
                map[task.id] = task.copy(
                    subtasks = task.subtasks.filterNot { it.id in deletedIds }
                )
            } else {
                val winner = if (task.updatedAtEpochMillis >= existing.updatedAtEpochMillis) task else existing
                val mergedSubtasks = mergeSubtasks(
                    liveSubs = task.subtasks,
                    diskSubs = existing.subtasks,
                    deletedIds = deletedIds
                )
                map[task.id] = winner.copy(
                    subtasks = mergedSubtasks,
                    completedPomodoros = maxOf(task.completedPomodoros, existing.completedPomodoros),
                    elapsedFocusMinutes = maxOf(task.elapsedFocusMinutes, existing.elapsedFocusMinutes)
                )
            }
        }

        return map.values.toList()
    }

    private fun mergeSubtasks(
        liveSubs: List<SubtaskItem>,
        diskSubs: List<SubtaskItem>,
        deletedIds: Set<String>
    ): List<SubtaskItem> {
        val map = linkedMapOf<String, SubtaskItem>()
        for (sub in diskSubs) {
            if (sub.id in deletedIds) continue
            map[sub.id] = sub
        }
        for (sub in liveSubs) {
            if (sub.id in deletedIds) continue
            val existing = map[sub.id]
            if (existing == null || sub.updatedAt >= existing.updatedAt) {
                map[sub.id] = sub
            }
        }
        return map.values.sortedBy { it.sortOrder }
    }

    private fun mergeProjects(
        liveProjects: List<ProjectItem>,
        diskProjects: List<ProjectItem>,
        deletedIds: Set<String>
    ): List<ProjectItem> {
        val map = linkedMapOf<String, ProjectItem>()
        for (proj in diskProjects) {
            if (proj.id in deletedIds) continue
            map[proj.id] = proj
        }
        for (proj in liveProjects) {
            if (proj.id in deletedIds) continue
            val existing = map[proj.id]
            if (existing == null || proj.updatedAt >= existing.updatedAt) {
                map[proj.id] = proj
            }
        }
        return map.values.sortedBy { it.sortOrder }
    }

    private fun mergeFolders(
        liveFolders: List<FolderItem>,
        diskFolders: List<FolderItem>,
        deletedIds: Set<String>
    ): List<FolderItem> {
        val map = linkedMapOf<String, FolderItem>()
        for (folder in diskFolders) {
            if (folder.id in deletedIds) continue
            map[folder.id] = folder
        }
        for (folder in liveFolders) {
            if (folder.id in deletedIds) continue
            val existing = map[folder.id]
            if (existing == null || folder.updatedAt >= existing.updatedAt) {
                map[folder.id] = folder
            }
        }
        return map.values.sortedBy { it.sortOrder }
    }

    private fun mergeTags(
        liveTags: List<TagItem>,
        diskTags: List<TagItem>,
        deletedIds: Set<String>
    ): List<TagItem> {
        val map = linkedMapOf<String, TagItem>()
        for (tag in diskTags) {
            if (tag.id in deletedIds) continue
            map[tag.id] = tag
        }
        for (tag in liveTags) {
            if (tag.id in deletedIds) continue
            val existing = map[tag.id]
            if (existing == null || tag.updatedAt >= existing.updatedAt) {
                map[tag.id] = tag
            }
        }
        return map.values.toList()
    }

    private fun mergeFocusRecords(
        liveRecords: List<FocusRecord>,
        diskRecords: List<FocusRecord>,
        deletedIds: Set<String>
    ): List<FocusRecord> {
        val map = linkedMapOf<String, FocusRecord>()
        for (rec in diskRecords) {
            if (rec.id in deletedIds) continue
            map[rec.id] = rec
        }
        for (rec in liveRecords) {
            if (rec.id in deletedIds) continue
            val existing = map[rec.id]
            if (existing == null || rec.endTimeEpochMillis >= existing.endTimeEpochMillis) {
                map[rec.id] = rec
            }
        }
        return map.values.sortedByDescending { it.startedAtEpochMillis }
    }

    private fun mergeForestState(
        live: ForestState,
        disk: ForestState,
        mergedSessions: List<FocusRecord>,
        deletedIds: Set<String>
    ): ForestState {
        val treesMap = linkedMapOf<String, PlantedTree>()
        for (tree in disk.plantedHistory) {
            if (tree.id !in deletedIds) treesMap[tree.id] = tree
        }
        for (tree in live.plantedHistory) {
            if (tree.id !in deletedIds) treesMap[tree.id] = tree
        }
        val mergedTrees = treesMap.values.sortedByDescending { it.plantedAtEpochMillis }
        val mergedSpecies = (disk.unlockedSpecies + live.unlockedSpecies + listOf(PlantSpecies.PINE)).distinct()

        val (computedCurrentStreak, computedBestStreak) = computeDailyStreaks(mergedSessions)
        val mergedBestStreak = maxOf(
            computedBestStreak,
            computedCurrentStreak,
            live.bestDailyStreak,
            disk.bestDailyStreak
        )
        val mergedCurrentStreak = maxOf(
            computedCurrentStreak,
            live.currentDailyStreak,
            disk.currentDailyStreak
        )

        val chosenActiveSpecies = if (live.activeSpecies != PlantSpecies.PINE) {
            live.activeSpecies
        } else if (disk.activeSpecies != PlantSpecies.PINE) {
            disk.activeSpecies
        } else {
            live.activeSpecies
        }

        return ForestState(
            sunlightPoints = maxOf(live.sunlightPoints, disk.sunlightPoints),
            currentGrowthMinutes = maxOf(live.currentGrowthMinutes, disk.currentGrowthMinutes),
            activeSpecies = if (chosenActiveSpecies in mergedSpecies) chosenActiveSpecies else PlantSpecies.PINE,
            unlockedSpecies = mergedSpecies,
            plantedHistory = mergedTrees,
            currentDailyStreak = mergedCurrentStreak,
            bestDailyStreak = mergedBestStreak
        )
    }

    private fun shouldInheritDiskSettings(live: AppSettings, disk: AppSettings): Boolean {
        val liveUnmodified = live.settingsUpdatedAt == 0L && areSettingsDefaultUnmodified(
            live.copy(
                whitelistedPackages = emptyList(),
                blockedPackages = emptyList()
            )
        )
        val diskHasUserSettings = disk.settingsUpdatedAt > 0L || !areSettingsDefaultUnmodified(disk)
        if (liveUnmodified && diskHasUserSettings) return true
        return disk.settingsUpdatedAt > live.settingsUpdatedAt
    }

    private fun mergeSettings(live: AppSettings, disk: AppSettings): AppSettings {
        val preferDisk = shouldInheritDiskSettings(live, disk)
        val primary = if (preferDisk) disk else live
        val secondary = if (preferDisk) live else disk

        val unionWhitelist = (disk.whitelistedPackages + live.whitelistedPackages)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val unionBlocked = (disk.blockedPackages + live.blockedPackages)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        val resolvedApiKey = primary.geminiApiKey.ifBlank { secondary.geminiApiKey }
        val resolvedModelId = primary.geminiModelId.ifBlank {
            secondary.geminiModelId.ifBlank { "gemini-2.5-flash" }
        }
        val resolvedAccent = primary.accentColorHex.ifBlank {
            secondary.accentColorHex.ifBlank { "#E05246" }
        }

        return primary.copy(
            accentColorHex = resolvedAccent,
            whitelistedPackages = unionWhitelist,
            blockedPackages = unionBlocked,
            geminiApiKey = resolvedApiKey,
            geminiModelId = resolvedModelId,
            settingsUpdatedAt = maxOf(live.settingsUpdatedAt, disk.settingsUpdatedAt)
        )
    }

    private fun mergeSharedPreferencesSnapshots(
        livePrefs: Map<String, List<ExportedPrefEntry>>,
        diskPrefs: Map<String, List<ExportedPrefEntry>>,
        preferDiskSettings: Boolean
    ): Map<String, List<ExportedPrefEntry>> {
        val result = linkedMapOf<String, List<ExportedPrefEntry>>()
        val allNames = (diskPrefs.keys + livePrefs.keys).distinct()
        for (name in allNames) {
            val map = linkedMapOf<String, ExportedPrefEntry>()
            val first = if (preferDiskSettings) livePrefs[name].orEmpty() else diskPrefs[name].orEmpty()
            val second = if (preferDiskSettings) diskPrefs[name].orEmpty() else livePrefs[name].orEmpty()
            first.forEach { map[it.key] = it }
            second.forEach { map[it.key] = it }
            if (map.isNotEmpty()) {
                result[name] = map.values.sortedBy { it.key }
            }
        }
        return result
    }

    /**
     * Computes (currentDailyStreak, bestDailyStreak) in consecutive days from the combined focus session history.
     */
    fun computeDailyStreaks(sessions: List<FocusRecord>): Pair<Int, Int> {
        if (sessions.isEmpty()) return 0 to 0

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val uniqueDays = sessions.mapNotNull { rec ->
            if (rec.startedAtEpochMillis > 0L) {
                DateUtils.epochDayFromMillis(rec.startedAtEpochMillis)
            } else if (rec.dateKey.isNotBlank()) {
                try {
                    val d: Date? = dateFmt.parse(rec.dateKey)
                    d?.let { DateUtils.epochDayFromMillis(it.time) }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }.distinct().sorted()

        if (uniqueDays.isEmpty()) return 0 to 0

        var best = 1
        var run = 1
        for (i in 1 until uniqueDays.size) {
            if (uniqueDays[i] == uniqueDays[i - 1] + 1L) {
                run++
                if (run > best) best = run
            } else {
                run = 1
            }
        }

        val today = DateUtils.todayEpochDay()
        val lastDay = uniqueDays.last()
        val current = if (lastDay == today || lastDay == today - 1L) {
            var c = 1
            var idx = uniqueDays.size - 1
            while (idx > 0 && uniqueDays[idx] == uniqueDays[idx - 1] + 1L) {
                c++
                idx--
            }
            c
        } else {
            0
        }

        return current to maxOf(best, current)
    }
}
