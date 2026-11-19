package com.example.model

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class AppLanguage { AR, EN }

enum class SmartListType {
    TODAY,
    TOMORROW,
    UPCOMING,
    SOMEDAY,
    EVENTS,
    INBOX,
    COMPLETED
}

enum class TaskPriority(val level: Int) {
    HIGH(3),
    MEDIUM(2),
    LOW(1),
    NONE(0)
}

enum class RecurrenceRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class TaskSortOption {
    PRIORITY,
    DUE_DATE,
    POMODORO_ESTIMATE,
    ALPHABETICAL
}

enum class TimerPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK
}

enum class TimerCountMode {
    COUNTDOWN,
    STOPWATCH
}

enum class WhiteNoiseType {
    NONE,
    CLOCK,
    RAIN,
    WAVES,
    FOREST,
    CAFE
}

enum class PlantSpecies(val costPoints: Int, val colorHex: Long) {
    PINE(0, 0xFF10B981),
    CHERRY_BLOSSOM(300, 0xFFF472B6),
    BONSAI(500, 0xFF059669),
    SUNFLOWER(400, 0xFFF59E0B),
    CACTUS(350, 0xFF22C55E),
    OAK(800, 0xFF16A34A)
}

enum class ReportPeriod {
    TODAY,
    WEEK,
    MONTH,
    ALL
}

@JsonClass(generateAdapter = true)
data class SubtaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false,
    val parentTaskId: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class ExportedSubtaskItem(
    val id: String,
    val parentTaskId: String,
    val title: String,
    val isCompleted: Boolean,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val projectId: String? = null,
    val tagIds: List<String> = emptyList(),
    val priority: TaskPriority = TaskPriority.NONE,
    val estimatedPomodoros: Int = 1,
    val completedPomodoros: Int = 0,
    val customPomodoroMinutes: Int? = null,
    val elapsedFocusMinutes: Int = 0,
    val dueDateEpochDay: Long? = null,
    val isSomeday: Boolean = false,
    val isEvent: Boolean = false,
    val reminderTime: String? = null,
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val notes: String = "",
    val subtasks: List<SubtaskItem> = emptyList(),
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class FolderItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isExpanded: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class ProjectItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#E05246",
    val folderId: String? = null,
    val isArchived: Boolean = false,
    val iconName: String = "folder",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class TagItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#3B7BF6",
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class FocusRecord(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val taskTitle: String,
    val projectId: String? = null,
    val startedAtEpochMillis: Long,
    val durationMinutes: Int,
    val isStopwatch: Boolean = false,
    val sessionType: String = "FOCUS",
    val endTimeEpochMillis: Long = startedAtEpochMillis + durationMinutes * 60_000L,
    val durationSeconds: Int = durationMinutes * 60,
    val wasCompleted: Boolean = true,
    val dateKey: String = ""
)

@JsonClass(generateAdapter = true)
data class ExportedFocusStats(
    val totalFocusMinutes: Int = 0,
    val totalFocusSeconds: Int = 0,
    val totalPomodoroSessions: Int = 0,
    val totalCompletedTasks: Int = 0,
    val totalActiveTasks: Int = 0,
    val totalSubtasks: Int = 0,
    val completedSubtasks: Int = 0,
    val dailyMinutesByDateKey: Map<String, Int> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class ExportedPrefEntry(
    val key: String,
    val type: String,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null,
    val booleanValue: Boolean? = null,
    val stringSetValue: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class PlantedTree(
    val id: String = UUID.randomUUID().toString(),
    val species: PlantSpecies,
    val plantedAtEpochMillis: Long,
    val durationMinutes: Int
)

@JsonClass(generateAdapter = true)
data class ForestState(
    val sunlightPoints: Int = 1250,
    val currentGrowthMinutes: Int = 15,
    val activeSpecies: PlantSpecies = PlantSpecies.PINE,
    val unlockedSpecies: List<PlantSpecies> = listOf(PlantSpecies.PINE),
    val plantedHistory: List<PlantedTree> = emptyList(),
    val currentDailyStreak: Int = 0,
    val bestDailyStreak: Int = 0
)

@JsonClass(generateAdapter = true)
data class PersistedTimerState(
    val activeTaskId: String? = null,
    val phase: TimerPhase = TimerPhase.FOCUS,
    val countMode: TimerCountMode = TimerCountMode.COUNTDOWN,
    val isRunning: Boolean = false,
    val hasStarted: Boolean = false,
    val strictMode: Boolean = false,
    val totalPhaseSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val stopwatchSeconds: Int = 0,
    val timerEndTimeEpochMillis: Long? = null,
    val stopwatchStartTimeEpochMillis: Long? = null,
    val cycleCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class AppSettings(
    val pomodoroMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakInterval: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartNextPomodoro: Boolean = false,
    val disableBreaks: Boolean = false,
    val alarmEnabled: Boolean = true,
    val enableTaskReminders: Boolean = true,
    val persistentTimerNotification: Boolean = true,
    val alarmSoundAndVibration: Boolean = true,
    val lockedInRecentsConfirmed: Boolean = false,
    val hasPromptedPermissionsOnLaunch: Boolean = false,
    val darkTheme: Boolean = true,
    val accentColorHex: String = "#E05246",
    val language: AppLanguage = AppLanguage.AR,
    val whiteNoise: WhiteNoiseType = WhiteNoiseType.NONE,
    val whiteNoiseVolume: Float = 0.6f,
    val strictBlockerEnabled: Boolean = false,
    val whitelistedPackages: List<String> = emptyList(),
    val blockedPackages: List<String> = emptyList(),
    val geminiModelId: String = "gemini-2.5-flash",
    val geminiApiKey: String = "",
    val settingsUpdatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class AppBackupState(
    val backupVersion: Int = 4,
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskItem> = emptyList(),
    val subtasks: List<ExportedSubtaskItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val focusRecords: List<FocusRecord> = emptyList(),
    val pomodoroSessions: List<FocusRecord> = emptyList(),
    val focusStats: ExportedFocusStats = ExportedFocusStats(),
    val settings: AppSettings = AppSettings(),
    val allPreferences: Map<String, List<ExportedPrefEntry>> = emptyMap(),
    val forestState: ForestState = ForestState(),
    val persistedTimer: PersistedTimerState? = null,
    val deletedIds: List<String> = emptyList()
)

data class PendingBackupRestorePrompt(
    val backupState: AppBackupState,
    val tasksCount: Int,
    val sessionsCount: Int,
    val projectsCount: Int,
    val filePath: String
)

data class ManualBackupExportSummary(
    val fileName: String,
    val fileSizeBytes: Long,
    val formattedFileSize: String,
    val projectsCount: Int,
    val tasksCount: Int,
    val subtasksCount: Int,
    val pomodoroSessionsCount: Int,
    val totalPrefsCount: Int,
    val updatedTimestamp: String = "",
    val mergedState: AppBackupState? = null
)
