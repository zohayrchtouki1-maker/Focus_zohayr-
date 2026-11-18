package com.example.notifications

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UnreadSection {
    TASKS,
    POMODORO,
    REPORTS,
    FOREST,
    SETTINGS_BACKUP,
    BLOCKER
}

data class UnreadBadgesState(
    val tasksUnread: Int = 0,
    val pomodoroUnread: Int = 0,
    val reportsUnread: Int = 0,
    val forestUnread: Int = 0,
    val settingsBackupUnread: Int = 0,
    val blockerUnread: Int = 0,
    val unreadTaskIds: Set<String> = emptySet()
) {
    val totalUnread: Int
        get() = (tasksUnread + pomodoroUnread + reportsUnread + forestUnread + settingsBackupUnread + blockerUnread)
            .coerceAtLeast(0)
}

object UnreadBadgeTracker {
    private const val PREFS_NAME = "focus_unread_badges_prefs"
    private const val KEY_TASKS_UNREAD = "tasks_unread"
    private const val KEY_POMODORO_UNREAD = "pomodoro_unread"
    private const val KEY_REPORTS_UNREAD = "reports_unread"
    private const val KEY_FOREST_UNREAD = "forest_unread"
    private const val KEY_SETTINGS_BACKUP_UNREAD = "settings_backup_unread"
    private const val KEY_BLOCKER_UNREAD = "blocker_unread"
    private const val KEY_UNREAD_TASK_IDS = "unread_task_ids"
    private const val KEY_ACKNOWLEDGED_OVERDUE_IDS = "acknowledged_overdue_ids"

    private val lock = Any()

    @Volatile
    private var initialized = false

    private val _state = MutableStateFlow(UnreadBadgesState())
    val state: StateFlow<UnreadBadgesState> = _state.asStateFlow()

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ensureInitialized(context: Context): UnreadBadgesState {
        synchronized(lock) {
            if (!initialized) {
                val loaded = readFromPrefs(context)
                _state.value = loaded
                initialized = true
            }
            return _state.value
        }
    }

    fun getState(context: Context): UnreadBadgesState = ensureInitialized(context)

    private fun readFromPrefs(context: Context): UnreadBadgesState {
        val sp = getPrefs(context)
        return UnreadBadgesState(
            tasksUnread = sp.getInt(KEY_TASKS_UNREAD, 0).coerceAtLeast(0),
            pomodoroUnread = sp.getInt(KEY_POMODORO_UNREAD, 0).coerceAtLeast(0),
            reportsUnread = sp.getInt(KEY_REPORTS_UNREAD, 0).coerceAtLeast(0),
            forestUnread = sp.getInt(KEY_FOREST_UNREAD, 0).coerceAtLeast(0),
            settingsBackupUnread = sp.getInt(KEY_SETTINGS_BACKUP_UNREAD, 0).coerceAtLeast(0),
            blockerUnread = sp.getInt(KEY_BLOCKER_UNREAD, 0).coerceAtLeast(0),
            unreadTaskIds = sp.getStringSet(KEY_UNREAD_TASK_IDS, emptySet()).orEmpty().toSet()
        )
    }

    private fun saveToPrefsAndEmit(context: Context, next: UnreadBadgesState): UnreadBadgesState {
        getPrefs(context).edit()
            .putInt(KEY_TASKS_UNREAD, next.tasksUnread.coerceAtLeast(0))
            .putInt(KEY_POMODORO_UNREAD, next.pomodoroUnread.coerceAtLeast(0))
            .putInt(KEY_REPORTS_UNREAD, next.reportsUnread.coerceAtLeast(0))
            .putInt(KEY_FOREST_UNREAD, next.forestUnread.coerceAtLeast(0))
            .putInt(KEY_SETTINGS_BACKUP_UNREAD, next.settingsBackupUnread.coerceAtLeast(0))
            .putInt(KEY_BLOCKER_UNREAD, next.blockerUnread.coerceAtLeast(0))
            .putStringSet(KEY_UNREAD_TASK_IDS, next.unreadTaskIds.toSet())
            .commit()
        initialized = true
        _state.value = next
        return next
    }

    fun recordTaskReminder(context: Context, taskId: String): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            val nextIds = if (taskId.isNotBlank()) current.unreadTaskIds + taskId else current.unreadTaskIds
            val nextCount = maxOf(current.tasksUnread + 1, nextIds.size)
            return saveToPrefsAndEmit(
                context,
                current.copy(
                    tasksUnread = nextCount,
                    unreadTaskIds = nextIds
                )
            )
        }
    }

    fun recordOverdueTasks(context: Context, overdueTaskIds: Set<String>): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            val sp = getPrefs(context)
            val acknowledged = sp.getStringSet(KEY_ACKNOWLEDGED_OVERDUE_IDS, emptySet()).orEmpty().toMutableSet()
            // Keep acknowledged set clean: drop tasks that are no longer overdue
            acknowledged.retainAll(overdueTaskIds)

            val newlyOverdue = overdueTaskIds.filter { it !in acknowledged && it !in current.unreadTaskIds }
            if (newlyOverdue.isEmpty()) {
                sp.edit().putStringSet(KEY_ACKNOWLEDGED_OVERDUE_IDS, acknowledged).commit()
                return current
            }

            acknowledged.addAll(newlyOverdue)
            sp.edit().putStringSet(KEY_ACKNOWLEDGED_OVERDUE_IDS, acknowledged).commit()

            val nextIds = current.unreadTaskIds + newlyOverdue
            val nextCount = maxOf(current.tasksUnread + newlyOverdue.size, nextIds.size)
            return saveToPrefsAndEmit(
                context,
                current.copy(
                    tasksUnread = nextCount,
                    unreadTaskIds = nextIds
                )
            )
        }
    }

    fun recordPomodoroCompleted(context: Context): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            return saveToPrefsAndEmit(
                context,
                current.copy(pomodoroUnread = current.pomodoroUnread + 1)
            )
        }
    }

    fun recordStatsAndForestActivity(
        context: Context,
        recordReports: Boolean = true,
        recordForest: Boolean = true
    ): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            return saveToPrefsAndEmit(
                context,
                current.copy(
                    reportsUnread = if (recordReports) current.reportsUnread + 1 else current.reportsUnread,
                    forestUnread = if (recordForest) current.forestUnread + 1 else current.forestUnread
                )
            )
        }
    }

    fun recordBackupCompleted(context: Context): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            return saveToPrefsAndEmit(
                context,
                current.copy(settingsBackupUnread = current.settingsBackupUnread + 1)
            )
        }
    }

    fun recordBlockerTriggered(context: Context): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            return saveToPrefsAndEmit(
                context,
                current.copy(blockerUnread = current.blockerUnread + 1)
            )
        }
    }

    fun clearTask(context: Context, taskId: String): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            val removed = current.unreadTaskIds.contains(taskId)
            val nextIds = current.unreadTaskIds - taskId
            val nextCount = when {
                nextIds.isEmpty() && current.tasksUnread <= 1 -> 0
                removed -> (current.tasksUnread - 1).coerceAtLeast(nextIds.size)
                else -> current.tasksUnread
            }
            cancelSystemNotifications(context, listOf(FocusNotificationManager.taskRequestCode(taskId)))
            return saveToPrefsAndEmit(
                context,
                current.copy(
                    tasksUnread = nextCount,
                    unreadTaskIds = nextIds
                )
            )
        }
    }

    fun clearTasksForDestination(
        context: Context,
        matchingTaskIds: Set<String>,
        clearAllIfPrimary: Boolean
    ): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            if (clearAllIfPrimary) {
                val idsToCancel = current.unreadTaskIds.map { FocusNotificationManager.taskRequestCode(it) }
                cancelSystemNotifications(context, idsToCancel)
                return saveToPrefsAndEmit(
                    context,
                    current.copy(tasksUnread = 0, unreadTaskIds = emptySet())
                )
            }
            val cleared = current.unreadTaskIds.intersect(matchingTaskIds)
            if (cleared.isEmpty()) return current

            cancelSystemNotifications(context, cleared.map { FocusNotificationManager.taskRequestCode(it) })
            val nextIds = current.unreadTaskIds - cleared
            val nextCount = if (nextIds.isEmpty()) {
                0
            } else {
                (current.tasksUnread - cleared.size).coerceAtLeast(nextIds.size)
            }
            return saveToPrefsAndEmit(
                context,
                current.copy(
                    tasksUnread = nextCount,
                    unreadTaskIds = nextIds
                )
            )
        }
    }

    fun clearSection(context: Context, section: UnreadSection): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            val next = when (section) {
                UnreadSection.TASKS -> {
                    val idsToCancel = current.unreadTaskIds.map { FocusNotificationManager.taskRequestCode(it) }
                    cancelSystemNotifications(context, idsToCancel)
                    current.copy(tasksUnread = 0, unreadTaskIds = emptySet())
                }
                UnreadSection.POMODORO -> {
                    cancelSystemNotifications(context, listOf(FocusNotificationManager.NOTIFICATION_ID_TIMER_COMPLETE))
                    current.copy(pomodoroUnread = 0)
                }
                UnreadSection.REPORTS -> {
                    current.copy(reportsUnread = 0)
                }
                UnreadSection.FOREST -> {
                    current.copy(forestUnread = 0)
                }
                UnreadSection.SETTINGS_BACKUP -> {
                    cancelSystemNotifications(
                        context,
                        listOf(
                            FocusNotificationManager.NOTIFICATION_ID_AUTO_BACKUP,
                            FocusNotificationManager.NOTIFICATION_ID_MANUAL_BACKUP,
                            9051, 9052, 9053
                        )
                    )
                    current.copy(settingsBackupUnread = 0)
                }
                UnreadSection.BLOCKER -> {
                    cancelSystemNotifications(context, listOf(FocusNotificationManager.NOTIFICATION_ID_APP_BLOCKER))
                    current.copy(blockerUnread = 0)
                }
            }
            return saveToPrefsAndEmit(context, next)
        }
    }

    fun resetAll(context: Context): UnreadBadgesState {
        synchronized(lock) {
            val current = ensureInitialized(context)
            val taskNotifs = current.unreadTaskIds.map { FocusNotificationManager.taskRequestCode(it) }
            cancelSystemNotifications(
                context,
                taskNotifs + listOf(
                    FocusNotificationManager.NOTIFICATION_ID_TIMER_COMPLETE,
                    FocusNotificationManager.NOTIFICATION_ID_AUTO_BACKUP,
                    FocusNotificationManager.NOTIFICATION_ID_MANUAL_BACKUP,
                    FocusNotificationManager.NOTIFICATION_ID_APP_BLOCKER,
                    9051, 9052, 9053
                )
            )
            getPrefs(context).edit().clear().commit()
            val clean = UnreadBadgesState()
            initialized = true
            _state.value = clean
            return clean
        }
    }

    private fun cancelSystemNotifications(context: Context, ids: List<Int>) {
        if (ids.isEmpty()) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ids.forEach { nm.cancel(it) }
        } catch (_: Exception) {
        }
    }
}
