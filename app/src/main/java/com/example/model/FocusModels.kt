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
    val isDone: Boolean = false
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
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class FolderItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isExpanded: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ProjectItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#E74F44",
    val folderId: String? = null,
    val isArchived: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TagItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#0EA5E9"
)

@JsonClass(generateAdapter = true)
data class FocusRecord(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val taskTitle: String,
    val projectId: String? = null,
    val startedAtEpochMillis: Long,
    val durationMinutes: Int,
    val isStopwatch: Boolean = false
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
    val plantedHistory: List<PlantedTree> = emptyList()
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
    val darkTheme: Boolean = false,
    val language: AppLanguage = AppLanguage.AR,
    val whiteNoise: WhiteNoiseType = WhiteNoiseType.NONE,
    val whiteNoiseVolume: Float = 0.6f
)

@JsonClass(generateAdapter = true)
data class AppBackupState(
    val tasks: List<TaskItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val focusRecords: List<FocusRecord> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val forestState: ForestState = ForestState()
)
