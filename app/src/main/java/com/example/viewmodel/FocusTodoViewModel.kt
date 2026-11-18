package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.TomatoAiService
import com.example.ai.TomatoChatMessage
import com.example.ai.TomatoGeminiModel
import com.example.ai.TomatoToolExecutionResult
import com.example.ai.TomatoToolExecutor
import com.example.audio.SoundSynthesizer
import com.example.blocker.StrictAppBlockerManager
import com.example.blocker.StrictBlockerPermissionStatus
import com.example.data.BackupImportMode
import com.example.data.DateUtils
import com.example.data.FocusRepository
import com.example.data.SeedData
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.FocusRecord
import com.example.model.FolderItem
import com.example.model.ManualBackupExportSummary
import com.example.model.PersistedTimerState
import com.example.model.PlantSpecies
import com.example.model.PlantedTree
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SmartListType
import com.example.model.SubtaskItem
import com.example.model.TagItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskSortOption
import com.example.model.TimerCountMode
import com.example.model.TimerPhase
import com.example.model.WhiteNoiseType
import com.example.notifications.ExternalNotificationEvent
import com.example.notifications.FocusNotificationBus
import com.example.notifications.FocusNotificationManager
import com.example.notifications.SystemPermissionSnapshot
import com.example.notifications.SystemPermissionsHelper
import com.example.notifications.UnreadBadgeTracker
import com.example.notifications.UnreadBadgesState
import com.example.notifications.UnreadSection
import com.example.ui.components.TaskFilterUtils
import com.example.ui.components.formatSecondsMmSs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

sealed class NavDestination {
    data class SmartList(val type: SmartListType) : NavDestination()
    data class Project(val projectId: String) : NavDestination()
    data class Tag(val tagId: String) : NavDestination()
}

data class TimerUiState(
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

data class MainUiState(
    val isLoaded: Boolean = false,
    val data: AppBackupState = SeedData.createInitialState(),
    val destination: NavDestination = NavDestination.SmartList(SmartListType.INBOX),
    val sortOption: TaskSortOption = TaskSortOption.PRIORITY,
    val showCompletedAccordion: Boolean = false,
    val selectedTaskId: String? = null,
    val showHomeScreenOnCompact: Boolean = true,
    val showCalendar: Boolean = false,
    val showFullScreenTimer: Boolean = false,
    val showReports: Boolean = false,
    val showForest: Boolean = false,
    val showSettings: Boolean = false,
    val showBackgroundPermissionsModal: Boolean = false,
    val showStrictBlockerSetup: Boolean = false,
    val showTomatoAi: Boolean = false,
    val tomatoAiApiKey: String = "",
    val tomatoAiSelectedModel: TomatoGeminiModel = TomatoGeminiModel.FLASH_2_5,
    val tomatoAiMessages: List<TomatoChatMessage> = emptyList(),
    val tomatoAiIsLoading: Boolean = false,
    val timer: TimerUiState = TimerUiState(),
    val systemPermissions: SystemPermissionSnapshot = SystemPermissionSnapshot(),
    val strictBlockerPermissions: StrictBlockerPermissionStatus = StrictBlockerPermissionStatus(),
    val permissionRequestTrigger: Int = 0,
    val statusBannerMessage: String? = null,
    val lastAutoBackupTimeMillis: Long = 0L,
    val unreadBadges: UnreadBadgesState = UnreadBadgesState()
)

class FocusTodoViewModel(application: Application) : AndroidViewModel(application), TomatoToolExecutor {
    private val appContext = application.applicationContext
    private val repository = FocusRepository(appContext)
    private val soundSynthesizer = SoundSynthesizer()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        FocusNotificationManager.ensureChannelsCreated(appContext)
        UnreadBadgeTracker.ensureInitialized(appContext)

        viewModelScope.launch {
            UnreadBadgeTracker.state.collect { badges ->
                _uiState.update { it.copy(unreadBadges = badges) }
            }
        }

        viewModelScope.launch {
            val rawLoaded = repository.loadState()
            val loaded = rawLoaded.copy(
                settings = rawLoaded.settings.copy(darkTheme = true)
            )
            val defaultFocusSecs = loaded.settings.pomodoroMinutes * 60
            val restoredTimer = restorePersistedTimer(loaded.persistedTimer, defaultFocusSecs).let { t ->
                t.copy(strictMode = t.strictMode || loaded.settings.strictBlockerEnabled)
            }
            val permSnapshot = SystemPermissionsHelper.getSnapshot(appContext)
            val blockerPerms = StrictAppBlockerManager.getPermissionStatus(appContext)
            val shouldPromptOnLaunch = !permSnapshot.notificationsGranted

            val lastAutoBackup = repository.getLastAutoBackupTimeMillis()
            val savedGeminiKey = TomatoAiService.getSavedApiKey(appContext)
            val savedGeminiModel = TomatoAiService.getSavedModel(appContext)

            _uiState.update {
                it.copy(
                    isLoaded = true,
                    data = loaded,
                    timer = restoredTimer,
                    systemPermissions = permSnapshot,
                    strictBlockerPermissions = blockerPerms,
                    permissionRequestTrigger = if (shouldPromptOnLaunch) it.permissionRequestTrigger + 1 else it.permissionRequestTrigger,
                    lastAutoBackupTimeMillis = lastAutoBackup,
                    tomatoAiApiKey = savedGeminiKey,
                    tomatoAiSelectedModel = savedGeminiModel
                )
            }
            syncStrictBlockerTimerState()
            checkAndRecordOverdueTasks(loaded.tasks)

            // Re-verify and schedule all upcoming task reminder alarms on app startup
            FocusNotificationManager.syncAllTaskReminders(
                context = appContext,
                tasks = loaded.tasks,
                settings = loaded.settings
            )

            // If restored timer was running, resume ticker or complete if expired
            if (restoredTimer.isRunning) {
                reconcileRunningTimerWithTimestamp()
                if (_uiState.value.timer.isRunning) {
                    syncRunningTimerNotificationsAndAlarms()
                    startTimerTicker()
                }
            } else if (restoredTimer.hasStarted) {
                updateOngoingNotificationTick()
            }
        }

        // Listen for status-bar notification action button broadcasts
        viewModelScope.launch {
            FocusNotificationBus.events.collect { event ->
                when (event) {
                    is ExternalNotificationEvent.PauseTimer -> pauseTimer()
                    is ExternalNotificationEvent.ResumeTimer -> startOrResumeTimer()
                    is ExternalNotificationEvent.StopTimer -> stopOrAbandonTimer()
                    is ExternalNotificationEvent.TimerCompletedInBackground -> {
                        reconcileRunningTimerWithTimestamp()
                    }
                    is ExternalNotificationEvent.CompleteTaskFromNotification -> {
                        val task = _uiState.value.data.tasks.find { it.id == event.taskId }
                        if (task != null && !task.isCompleted) {
                            toggleTaskCompleted(event.taskId)
                        }
                    }
                }
            }
        }

        // Listen for blocked app enforcement events from FocusBlockerAccessibilityService
        viewModelScope.launch {
            StrictAppBlockerManager.blockedEvents.collect {
                _uiState.update { state ->
                    state.copy(
                        showFullScreenTimer = true,
                        statusBannerMessage = StrictAppBlockerManager.STRICT_BLOCK_MESSAGE_AR
                    )
                }
            }
        }
    }

    private fun restorePersistedTimer(
        persisted: PersistedTimerState?,
        defaultFocusSecs: Int
    ): TimerUiState {
        if (persisted == null) {
            return TimerUiState(
                totalPhaseSeconds = defaultFocusSecs,
                remainingSeconds = defaultFocusSecs
            )
        }
        return TimerUiState(
            activeTaskId = persisted.activeTaskId,
            phase = persisted.phase,
            countMode = persisted.countMode,
            isRunning = persisted.isRunning,
            hasStarted = persisted.hasStarted,
            strictMode = persisted.strictMode,
            totalPhaseSeconds = persisted.totalPhaseSeconds,
            remainingSeconds = persisted.remainingSeconds,
            stopwatchSeconds = persisted.stopwatchSeconds,
            timerEndTimeEpochMillis = persisted.timerEndTimeEpochMillis,
            stopwatchStartTimeEpochMillis = persisted.stopwatchStartTimeEpochMillis,
            cycleCount = persisted.cycleCount
        )
    }

    private fun TimerUiState.toPersisted(): PersistedTimerState = PersistedTimerState(
        activeTaskId = activeTaskId,
        phase = phase,
        countMode = countMode,
        isRunning = isRunning,
        hasStarted = hasStarted,
        strictMode = strictMode,
        totalPhaseSeconds = totalPhaseSeconds,
        remainingSeconds = remainingSeconds,
        stopwatchSeconds = stopwatchSeconds,
        timerEndTimeEpochMillis = timerEndTimeEpochMillis,
        stopwatchStartTimeEpochMillis = stopwatchStartTimeEpochMillis,
        cycleCount = cycleCount
    )

    private fun commitData(transform: (AppBackupState) -> AppBackupState) {
        _uiState.update { state ->
            val nextData = transform(state.data).copy(
                persistedTimer = state.timer.toPersisted()
            )
            viewModelScope.launch {
                repository.saveState(nextData)
                val updatedBackupTs = repository.getLastAutoBackupTimeMillis()
                _uiState.update { it.copy(lastAutoBackupTimeMillis = updatedBackupTs) }
            }
            state.copy(data = nextData)
        }
    }

    private fun syncStrictBlockerTimerState() {
        val state = _uiState.value
        val t = state.timer
        val isStrict = t.strictMode || state.data.settings.strictBlockerEnabled
        StrictAppBlockerManager.syncTimerState(
            context = appContext,
            isRunning = t.isRunning,
            phase = t.phase,
            strictMode = isStrict,
            timerEndTimeEpochMillis = t.timerEndTimeEpochMillis,
            countMode = t.countMode,
            activeTaskTitle = activeTaskOrDefaultTitle(),
            stopwatchStartTimeEpochMillis = t.stopwatchStartTimeEpochMillis
        )
    }

    private fun saveCurrentTimerToStorage() {
        val state = _uiState.value
        val nextData = state.data.copy(persistedTimer = state.timer.toPersisted())
        _uiState.update {
            it.copy(data = nextData)
        }
        syncStrictBlockerTimerState()
        viewModelScope.launch {
            repository.saveState(nextData)
            val updatedBackupTs = repository.getLastAutoBackupTimeMillis()
            _uiState.update { it.copy(lastAutoBackupTimeMillis = updatedBackupTs) }
        }
    }

    fun refreshSystemPermissions() {
        val snapshot = SystemPermissionsHelper.getSnapshot(appContext)
        val blockerPerms = StrictAppBlockerManager.getPermissionStatus(appContext)
        _uiState.update {
            it.copy(
                systemPermissions = snapshot,
                strictBlockerPermissions = blockerPerms
            )
        }
    }

    fun refreshStrictBlockerPermissions() {
        val blockerPerms = StrictAppBlockerManager.getPermissionStatus(appContext)
        _uiState.update { it.copy(strictBlockerPermissions = blockerPerms) }
    }

    fun requestNativePermissionsIfMissing() {
        refreshSystemPermissions()
        val snap = _uiState.value.systemPermissions
        if (!snap.notificationsGranted) {
            _uiState.update { it.copy(permissionRequestTrigger = it.permissionRequestTrigger + 1) }
        }
    }

    fun onAppResumed() {
        refreshSystemPermissions()
        reconcileRunningTimerWithTimestamp()
        checkAndRecordOverdueTasks(_uiState.value.data.tasks)
    }

    private fun checkAndRecordOverdueTasks(tasks: List<TaskItem>) {
        val today = DateUtils.todayEpochDay()
        val now = System.currentTimeMillis()
        val overdueIds = tasks.filter { task ->
            if (task.isCompleted) return@filter false
            val day = task.dueDateEpochDay ?: return@filter false
            if (day < today) return@filter true
            if (day == today && !task.reminderTime.isNullOrBlank()) {
                val reminderMillis = FocusNotificationManager.computeTaskReminderEpochMillis(task)
                if (reminderMillis != null && reminderMillis <= now) return@filter true
            }
            false
        }.map { it.id }.toSet()
        UnreadBadgeTracker.recordOverdueTasks(appContext, overdueIds)
    }

    private fun clearUnreadTasksForDestination(dest: NavDestination) {
        val state = _uiState.value
        val (active, completed) = TaskFilterUtils.filterTasks(state.data.tasks, dest, state.sortOption)
        val matchingIds = (active + completed).map { it.id }.toSet()
        val isInbox = dest is NavDestination.SmartList && dest.type == SmartListType.INBOX
        UnreadBadgeTracker.clearTasksForDestination(
            context = appContext,
            matchingTaskIds = matchingIds,
            clearAllIfPrimary = isInbox
        )
    }

    fun handleNotificationSectionIntent(sectionName: String?) {
        val section = sectionName?.let {
            runCatching { UnreadSection.valueOf(it) }.getOrNull()
        } ?: return
        when (section) {
            UnreadSection.TASKS -> {
                selectDestination(NavDestination.SmartList(SmartListType.TODAY))
            }
            UnreadSection.POMODORO -> {
                setShowFullScreenTimer(true)
            }
            UnreadSection.REPORTS -> {
                setShowReports(true)
            }
            UnreadSection.FOREST -> {
                setShowForest(true)
            }
            UnreadSection.SETTINGS_BACKUP -> {
                setShowSettings(true)
            }
            UnreadSection.BLOCKER -> {
                setShowFullScreenTimer(true)
                setShowStrictBlockerSetup(true)
            }
        }
    }

    fun selectDestination(dest: NavDestination) {
        _uiState.update {
            it.copy(
                destination = dest,
                selectedTaskId = null,
                showHomeScreenOnCompact = false
            )
        }
        clearUnreadTasksForDestination(dest)
    }

    fun setShowHomeScreenOnCompact(show: Boolean) {
        _uiState.update { it.copy(showHomeScreenOnCompact = show, selectedTaskId = null) }
        if (!show) {
            clearUnreadTasksForDestination(_uiState.value.destination)
        }
    }

    fun setShowCalendar(show: Boolean) {
        _uiState.update { it.copy(showCalendar = show) }
        if (show) {
            clearUnreadTasksForDestination(NavDestination.SmartList(SmartListType.EVENTS))
        }
    }

    fun setSortOption(option: TaskSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun toggleShowCompletedAccordion() {
        _uiState.update { it.copy(showCompletedAccordion = !it.showCompletedAccordion) }
    }

    fun openTaskInspector(taskId: String?) {
        if (taskId != null) {
            UnreadBadgeTracker.clearTask(appContext, taskId)
        }
        _uiState.update { it.copy(selectedTaskId = taskId) }
    }

    fun setShowFullScreenTimer(show: Boolean) {
        if (show) {
            UnreadBadgeTracker.clearSection(appContext, UnreadSection.POMODORO)
        }
        _uiState.update { it.copy(showFullScreenTimer = show) }
    }

    fun setShowReports(show: Boolean) {
        if (show) {
            UnreadBadgeTracker.clearSection(appContext, UnreadSection.REPORTS)
        }
        _uiState.update { it.copy(showReports = show) }
    }

    fun setShowForest(show: Boolean) {
        if (show) {
            UnreadBadgeTracker.clearSection(appContext, UnreadSection.FOREST)
        }
        _uiState.update { it.copy(showForest = show) }
    }

    fun setShowSettings(show: Boolean) {
        if (show) {
            refreshSystemPermissions()
            UnreadBadgeTracker.clearSection(appContext, UnreadSection.SETTINGS_BACKUP)
        }
        _uiState.update { it.copy(showSettings = show) }
    }

    fun setShowBackgroundPermissionsModal(show: Boolean) {
        if (show) refreshSystemPermissions()
        _uiState.update { it.copy(showBackgroundPermissionsModal = show) }
    }

    fun setShowStrictBlockerSetup(show: Boolean) {
        if (show) {
            refreshStrictBlockerPermissions()
            UnreadBadgeTracker.clearSection(appContext, UnreadSection.BLOCKER)
        }
        _uiState.update { it.copy(showStrictBlockerSetup = show) }
    }

    fun notifyStrictBlockTriggered() {
        _uiState.update {
            it.copy(
                showFullScreenTimer = true,
                statusBannerMessage = StrictAppBlockerManager.STRICT_BLOCK_MESSAGE_AR
            )
        }
    }

    fun setStrictBlockerEnabled(enabled: Boolean) {
        val t = _uiState.value.timer
        if (!enabled && t.isRunning && t.phase == TimerPhase.FOCUS) {
            _uiState.update {
                it.copy(statusBannerMessage = "🔒 لا يمكن إيقاف الحظر الصارم أثناء جلسة التركيز الجارية!")
            }
            return
        }
        StrictAppBlockerManager.setStrictBlockerEnabled(appContext, enabled)
        _uiState.update { state ->
            state.copy(
                timer = state.timer.copy(strictMode = enabled),
                data = state.data.copy(
                    settings = state.data.settings.copy(strictBlockerEnabled = enabled)
                )
            )
        }
        saveCurrentTimerToStorage()
        if (_uiState.value.timer.isRunning) {
            updateOngoingNotificationTick()
        }
    }

    fun toggleWhitelistedApp(packageName: String) {
        val updatedSet = StrictAppBlockerManager.toggleWhitelistedPackage(appContext, packageName)
        val sortedList = updatedSet.sorted()
        updateSettings { it.copy(whitelistedPackages = sortedList) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Task CRUD & Exact Reminder Scheduling ---

    fun addTask(
        title: String,
        estimatedPomodoros: Int,
        dueDateEpochDay: Long?,
        isSomeday: Boolean,
        isEvent: Boolean,
        priority: TaskPriority,
        projectId: String?,
        tagIds: List<String>,
        reminderTime: String? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return

        if (reminderTime != null) {
            requestNativePermissionsIfMissing()
        }

        val task = TaskItem(
            title = cleanTitle,
            projectId = projectId,
            tagIds = tagIds,
            priority = priority,
            estimatedPomodoros = estimatedPomodoros.coerceIn(1, 20),
            dueDateEpochDay = dueDateEpochDay,
            isSomeday = isSomeday,
            isEvent = isEvent,
            reminderTime = reminderTime
        )
        commitData { data ->
            data.copy(tasks = listOf(task) + data.tasks)
        }
        FocusNotificationManager.scheduleTaskReminder(appContext, task, _uiState.value.data.settings)
    }

    fun toggleTaskCompleted(taskId: String) {
        val task = _uiState.value.data.tasks.find { it.id == taskId } ?: return
        val newCompleted = !task.isCompleted
        if (newCompleted && _uiState.value.data.settings.alarmSoundAndVibration) {
            soundSynthesizer.playCheckSound()
        }

        var newlyCreatedRecurringTask: TaskItem? = null

        commitData { data ->
            val updatedList = data.tasks.map { item ->
                if (item.id == taskId) {
                    item.copy(
                        isCompleted = newCompleted,
                        completedAtEpochMillis = if (newCompleted) System.currentTimeMillis() else null
                    )
                } else item
            }.toMutableList()

            if (newCompleted && task.recurrence != RecurrenceRule.NONE) {
                val baseDay = task.dueDateEpochDay ?: DateUtils.todayEpochDay()
                val nextDay = when (task.recurrence) {
                    RecurrenceRule.DAILY -> baseDay + 1
                    RecurrenceRule.WEEKLY -> baseDay + 7
                    RecurrenceRule.MONTHLY -> baseDay + 30
                    RecurrenceRule.NONE -> baseDay
                }
                val nextTask = task.copy(
                    id = UUID.randomUUID().toString(),
                    dueDateEpochDay = nextDay,
                    completedPomodoros = 0,
                    elapsedFocusMinutes = 0,
                    isCompleted = false,
                    completedAtEpochMillis = null,
                    subtasks = task.subtasks.map { it.copy(id = UUID.randomUUID().toString(), isDone = false) },
                    createdAtEpochMillis = System.currentTimeMillis()
                )
                newlyCreatedRecurringTask = nextTask
                updatedList.add(0, nextTask)
            }

            data.copy(tasks = updatedList)
        }

        val settings = _uiState.value.data.settings
        if (newCompleted) {
            FocusNotificationManager.cancelTaskReminder(appContext, taskId)
            UnreadBadgeTracker.clearTask(appContext, taskId)
            newlyCreatedRecurringTask?.let {
                FocusNotificationManager.scheduleTaskReminder(appContext, it, settings)
            }
        } else {
            val updatedTask = _uiState.value.data.tasks.find { it.id == taskId }
            if (updatedTask != null) {
                FocusNotificationManager.scheduleTaskReminder(appContext, updatedTask, settings)
            }
        }
    }

    fun updateTask(taskId: String, transform: (TaskItem) -> TaskItem) {
        val previousTask = _uiState.value.data.tasks.find { it.id == taskId }
        val now = System.currentTimeMillis()
        commitData { data ->
            data.copy(
                tasks = data.tasks.map {
                    if (it.id == taskId) transform(it).copy(updatedAtEpochMillis = now) else it
                }
            )
        }
        val updatedTask = _uiState.value.data.tasks.find { it.id == taskId } ?: return
        val settings = _uiState.value.data.settings

        // If user just set or changed a reminder time, prompt for notification/exact alarm permissions if missing
        if (updatedTask.reminderTime != null && updatedTask.reminderTime != previousTask?.reminderTime) {
            requestNativePermissionsIfMissing()
        }
        FocusNotificationManager.scheduleTaskReminder(appContext, updatedTask, settings)
    }

    fun deleteTask(taskId: String) {
        FocusNotificationManager.cancelTaskReminder(appContext, taskId)
        UnreadBadgeTracker.clearTask(appContext, taskId)
        commitData { data ->
            data.copy(tasks = data.tasks.filterNot { it.id == taskId })
        }
        _uiState.update { state ->
            state.copy(
                selectedTaskId = if (state.selectedTaskId == taskId) null else state.selectedTaskId,
                timer = if (state.timer.activeTaskId == taskId) {
                    state.timer.copy(activeTaskId = null)
                } else state.timer
            )
        }
    }

    fun addSubtask(taskId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        updateTask(taskId) { task ->
            task.copy(
                subtasks = task.subtasks + SubtaskItem(
                    title = clean,
                    parentTaskId = taskId,
                    sortOrder = task.subtasks.size
                )
            )
        }
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        updateTask(taskId) { task ->
            task.copy(
                subtasks = task.subtasks.map { sub ->
                    if (sub.id == subtaskId) sub.copy(isDone = !sub.isDone) else sub
                }
            )
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        updateTask(taskId) { task ->
            task.copy(subtasks = task.subtasks.filterNot { it.id == subtaskId })
        }
    }

    // --- Folders, Projects, Tags CRUD ---

    fun addFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        commitData { data ->
            data.copy(folders = data.folders + FolderItem(name = clean))
        }
    }

    fun toggleFolderExpanded(folderId: String) {
        commitData { data ->
            data.copy(
                folders = data.folders.map {
                    if (it.id == folderId) it.copy(isExpanded = !it.isExpanded) else it
                }
            )
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        commitData { data ->
            data.copy(
                folders = data.folders.map {
                    if (it.id == folderId) it.copy(name = clean) else it
                }
            )
        }
    }

    fun deleteFolder(folderId: String) {
        commitData { data ->
            data.copy(
                folders = data.folders.filterNot { it.id == folderId },
                projects = data.projects.map {
                    if (it.folderId == folderId) it.copy(folderId = null) else it
                }
            )
        }
    }

    fun saveProject(existingId: String?, name: String, colorHex: String, folderId: String?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        commitData { data ->
            if (existingId == null) {
                data.copy(
                    projects = data.projects + ProjectItem(
                        name = clean,
                        colorHex = colorHex,
                        folderId = folderId,
                        sortOrder = data.projects.size,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                data.copy(
                    projects = data.projects.map {
                        if (it.id == existingId) {
                            it.copy(name = clean, colorHex = colorHex, folderId = folderId, updatedAt = now)
                        } else it
                    }
                )
            }
        }
    }

    fun toggleArchiveProject(projectId: String) {
        commitData { data ->
            data.copy(
                projects = data.projects.map {
                    if (it.id == projectId) it.copy(isArchived = !it.isArchived) else it
                }
            )
        }
    }

    fun deleteProject(projectId: String) {
        commitData { data ->
            data.copy(
                projects = data.projects.filterNot { it.id == projectId },
                tasks = data.tasks.map {
                    if (it.projectId == projectId) it.copy(projectId = null) else it
                }
            )
        }
        val dest = _uiState.value.destination
        if (dest is NavDestination.Project && dest.projectId == projectId) {
            selectDestination(NavDestination.SmartList(SmartListType.TODAY))
        }
    }

    fun addTag(name: String, colorHex: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isEmpty()) return
        commitData { data ->
            data.copy(tags = data.tags + TagItem(name = clean, colorHex = colorHex))
        }
    }

    fun deleteTag(tagId: String) {
        commitData { data ->
            data.copy(
                tags = data.tags.filterNot { it.id == tagId },
                tasks = data.tasks.map { it.copy(tagIds = it.tagIds.filterNot { id -> id == tagId }) }
            )
        }
        val dest = _uiState.value.destination
        if (dest is NavDestination.Tag && dest.tagId == tagId) {
            selectDestination(NavDestination.SmartList(SmartListType.TODAY))
        }
    }

    // --- Drift-Free Pomodoro Timer Engine ---

    private fun getFocusDurationSeconds(taskId: String?, settings: AppSettings): Int {
        val task = _uiState.value.data.tasks.find { it.id == taskId }
        val mins = task?.customPomodoroMinutes ?: settings.pomodoroMinutes
        return mins.coerceIn(1, 120) * 60
    }

    private fun activeTaskOrDefaultTitle(): String {
        val state = _uiState.value
        val task = state.data.tasks.find { it.id == state.timer.activeTaskId }
        if (task != null) return task.title
        return if (state.data.settings.language == AppLanguage.AR) "جلسة تركيز" else "Focus Session"
    }

    fun startFocusForTask(taskId: String?, openFullScreen: Boolean = true) {
        requestNativePermissionsIfMissing()
        val settings = _uiState.value.data.settings
        val focusSecs = getFocusDurationSeconds(taskId, settings)
        val now = System.currentTimeMillis()
        val endTime = now + focusSecs * 1000L

        _uiState.update { state ->
            state.copy(
                showFullScreenTimer = openFullScreen,
                timer = state.timer.copy(
                    activeTaskId = taskId,
                    phase = TimerPhase.FOCUS,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    timerEndTimeEpochMillis = endTime,
                    stopwatchStartTimeEpochMillis = now,
                    isRunning = true,
                    hasStarted = true,
                    strictMode = state.timer.strictMode || state.data.settings.strictBlockerEnabled
                )
            )
        }
        saveCurrentTimerToStorage()
        syncRunningTimerNotificationsAndAlarms()
        startTimerTicker()
    }

    fun selectActiveFocusTask(taskId: String?) {
        val state = _uiState.value
        if (state.timer.isRunning && state.timer.phase == TimerPhase.FOCUS) {
            _uiState.update { it.copy(timer = it.timer.copy(activeTaskId = taskId)) }
            saveCurrentTimerToStorage()
            syncRunningTimerNotificationsAndAlarms()
        } else {
            val focusSecs = getFocusDurationSeconds(taskId, state.data.settings)
            _uiState.update {
                it.copy(
                    timer = it.timer.copy(
                        activeTaskId = taskId,
                        phase = TimerPhase.FOCUS,
                        totalPhaseSeconds = focusSecs,
                        remainingSeconds = if (!it.timer.hasStarted) focusSecs else it.timer.remainingSeconds
                    )
                )
            }
            saveCurrentTimerToStorage()
        }
    }

    fun startOrResumeTimer() {
        requestNativePermissionsIfMissing()
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val t = state.timer
            val endTime = now + t.remainingSeconds * 1000L
            val swStart = now - t.stopwatchSeconds * 1000L
            state.copy(
                timer = t.copy(
                    isRunning = true,
                    hasStarted = true,
                    strictMode = t.strictMode || state.data.settings.strictBlockerEnabled,
                    timerEndTimeEpochMillis = endTime,
                    stopwatchStartTimeEpochMillis = swStart
                )
            )
        }
        saveCurrentTimerToStorage()
        syncRunningTimerNotificationsAndAlarms()
        startTimerTicker()
    }

    fun pauseTimer() {
        if (_uiState.value.timer.strictMode && _uiState.value.timer.phase == TimerPhase.FOCUS) {
            return
        }
        reconcileRunningTimerWithTimestamp()
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    isRunning = false,
                    timerEndTimeEpochMillis = null,
                    stopwatchStartTimeEpochMillis = null
                )
            )
        }
        saveCurrentTimerToStorage()
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
        updateOngoingNotificationTick()
        syncWhiteNoise()
    }

    fun stopOrAbandonTimer() {
        val timer = _uiState.value.timer
        val settings = _uiState.value.data.settings
        timerJob?.cancel()

        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        if (timer.countMode == TimerCountMode.STOPWATCH &&
            timer.phase == TimerPhase.FOCUS &&
            timer.stopwatchSeconds >= 60
        ) {
            val mins = (timer.stopwatchSeconds / 60).coerceAtLeast(1)
            recordCompletedFocusSession(
                taskId = timer.activeTaskId,
                durationMinutes = mins,
                isStopwatch = true,
                incrementPomodoro = true
            )
        }

        val focusSecs = getFocusDurationSeconds(timer.activeTaskId, settings)
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    phase = TimerPhase.FOCUS,
                    isRunning = false,
                    hasStarted = false,
                    strictMode = false,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    timerEndTimeEpochMillis = null,
                    stopwatchStartTimeEpochMillis = null
                )
            )
        }
        saveCurrentTimerToStorage()
        syncWhiteNoise()
    }

    fun skipBreak() {
        val timer = _uiState.value.timer
        val settings = _uiState.value.data.settings
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        val focusSecs = getFocusDurationSeconds(timer.activeTaskId, settings)
        val autoStart = settings.autoStartNextPomodoro
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    phase = TimerPhase.FOCUS,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    timerEndTimeEpochMillis = if (autoStart) now + focusSecs * 1000L else null,
                    stopwatchStartTimeEpochMillis = if (autoStart) now else null,
                    isRunning = autoStart,
                    hasStarted = autoStart
                )
            )
        }
        saveCurrentTimerToStorage()
        if (autoStart) {
            syncRunningTimerNotificationsAndAlarms()
            startTimerTicker()
        } else {
            syncWhiteNoise()
        }
    }

    fun setTimerCountMode(mode: TimerCountMode) {
        val settings = _uiState.value.data.settings
        val timer = _uiState.value.timer
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        val focusSecs = getFocusDurationSeconds(timer.activeTaskId, settings)
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    countMode = mode,
                    phase = TimerPhase.FOCUS,
                    isRunning = false,
                    hasStarted = false,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    timerEndTimeEpochMillis = null,
                    stopwatchStartTimeEpochMillis = null
                )
            )
        }
        saveCurrentTimerToStorage()
        syncWhiteNoise()
    }

    fun toggleStrictMode() {
        val current = _uiState.value.timer.strictMode || _uiState.value.data.settings.strictBlockerEnabled
        setStrictBlockerEnabled(!current)
    }

    private fun syncRunningTimerNotificationsAndAlarms() {
        val state = _uiState.value
        val t = state.timer
        val settings = state.data.settings
        if (!t.isRunning) {
            FocusNotificationManager.cancelOngoingTimerNotification(appContext)
            FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
            return
        }

        updateOngoingNotificationTick()

        if (t.countMode == TimerCountMode.COUNTDOWN && t.timerEndTimeEpochMillis != null) {
            FocusNotificationManager.scheduleExactTimerCompletionAlarm(
                context = appContext,
                triggerAtMillis = t.timerEndTimeEpochMillis,
                taskTitle = activeTaskOrDefaultTitle(),
                phase = t.phase,
                soundAndVibration = settings.alarmSoundAndVibration && settings.alarmEnabled,
                isArabic = settings.language == AppLanguage.AR
            )
        } else {
            FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
        }
    }

    private fun updateOngoingNotificationTick() {
        val state = _uiState.value
        val t = state.timer
        if (!t.isRunning && !t.hasStarted) return
        val displaySecs = if (t.countMode == TimerCountMode.STOPWATCH && t.phase == TimerPhase.FOCUS) {
            t.stopwatchSeconds
        } else {
            t.remainingSeconds
        }
        val elapsedSecs = if (t.countMode == TimerCountMode.STOPWATCH && t.phase == TimerPhase.FOCUS) {
            t.stopwatchSeconds
        } else {
            (t.totalPhaseSeconds - t.remainingSeconds).coerceAtLeast(0)
        }
        val today = DateUtils.todayEpochDay()
        val todayCompletedPomodoros = state.data.focusRecords.count {
            DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == today
        }

        FocusNotificationManager.showOrUpdateOngoingTimerNotification(
            context = appContext,
            taskTitle = activeTaskOrDefaultTitle(),
            phase = t.phase,
            formattedTime = formatSecondsMmSs(displaySecs),
            strictMode = t.strictMode,
            settings = state.data.settings,
            elapsedSeconds = elapsedSecs,
            todayCompletedPomodoros = todayCompletedPomodoros,
            isRunning = t.isRunning
        )
    }

    private fun reconcileRunningTimerWithTimestamp() {
        val state = _uiState.value
        val t = state.timer
        if (!t.isRunning) return

        val now = System.currentTimeMillis()
        if (t.countMode == TimerCountMode.STOPWATCH && t.phase == TimerPhase.FOCUS) {
            val start = t.stopwatchStartTimeEpochMillis ?: now
            val elapsed = max(0, ((now - start) / 1000L).toInt())
            _uiState.update {
                it.copy(timer = it.timer.copy(stopwatchSeconds = elapsed))
            }
            updateOngoingNotificationTick()
        } else {
            val end = t.timerEndTimeEpochMillis ?: (now + t.remainingSeconds * 1000L)
            val rem = max(0, ceil((end - now) / 1000.0).toInt())
            if (rem <= 0) {
                onCountdownPhaseCompleted()
            } else {
                _uiState.update {
                    it.copy(timer = it.timer.copy(remainingSeconds = rem))
                }
                updateOngoingNotificationTick()
            }
        }
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        syncWhiteNoise()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (!_uiState.value.timer.isRunning) break
                reconcileRunningTimerWithTimestamp()
            }
        }
    }

    private fun onCountdownPhaseCompleted() {
        val state = _uiState.value
        val settings = state.data.settings
        val t = state.timer
        val taskTitle = activeTaskOrDefaultTitle()

        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        val playSound = settings.alarmSoundAndVibration && settings.alarmEnabled
        if (playSound) {
            soundSynthesizer.playCompletionChime()
        }

        FocusNotificationManager.showTimerCompletionNotification(
            context = appContext,
            taskTitle = taskTitle,
            phaseName = t.phase.name,
            soundAndVibration = playSound,
            isArabic = settings.language == AppLanguage.AR
        )

        val now = System.currentTimeMillis()
        if (t.phase == TimerPhase.FOCUS) {
            val mins = (t.totalPhaseSeconds / 60).coerceAtLeast(1)
            recordCompletedFocusSession(
                taskId = t.activeTaskId,
                durationMinutes = mins,
                isStopwatch = false,
                incrementPomodoro = true
            )

            val newCycle = t.cycleCount + 1
            if (settings.disableBreaks) {
                val focusSecs = getFocusDurationSeconds(t.activeTaskId, settings)
                val autoNext = settings.autoStartNextPomodoro
                _uiState.update {
                    it.copy(
                        timer = it.timer.copy(
                            phase = TimerPhase.FOCUS,
                            cycleCount = newCycle,
                            totalPhaseSeconds = focusSecs,
                            remainingSeconds = focusSecs,
                            timerEndTimeEpochMillis = if (autoNext) now + focusSecs * 1000L else null,
                            isRunning = autoNext,
                            hasStarted = autoNext,
                            strictMode = false
                        )
                    )
                }
                saveCurrentTimerToStorage()
                if (autoNext) {
                    syncRunningTimerNotificationsAndAlarms()
                } else {
                    timerJob?.cancel()
                    syncWhiteNoise()
                }
            } else {
                val isLong = newCycle % settings.longBreakInterval.coerceAtLeast(1) == 0
                val nextPhase = if (isLong) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK
                val breakSecs = (if (isLong) settings.longBreakMinutes else settings.shortBreakMinutes) * 60
                val autoStartBreak = settings.autoStartBreaks
                _uiState.update {
                    it.copy(
                        timer = it.timer.copy(
                            phase = nextPhase,
                            cycleCount = newCycle,
                            totalPhaseSeconds = breakSecs,
                            remainingSeconds = breakSecs,
                            timerEndTimeEpochMillis = if (autoStartBreak) now + breakSecs * 1000L else null,
                            isRunning = autoStartBreak,
                            hasStarted = autoStartBreak,
                            strictMode = false
                        )
                    )
                }
                saveCurrentTimerToStorage()
                if (autoStartBreak) {
                    syncRunningTimerNotificationsAndAlarms()
                } else {
                    timerJob?.cancel()
                    syncWhiteNoise()
                }
            }
        } else {
            val focusSecs = getFocusDurationSeconds(t.activeTaskId, settings)
            val autoNext = settings.autoStartNextPomodoro
            _uiState.update {
                it.copy(
                    timer = it.timer.copy(
                        phase = TimerPhase.FOCUS,
                        totalPhaseSeconds = focusSecs,
                        remainingSeconds = focusSecs,
                        timerEndTimeEpochMillis = if (autoNext) now + focusSecs * 1000L else null,
                        isRunning = autoNext,
                        hasStarted = autoNext
                    )
                )
            }
            saveCurrentTimerToStorage()
            if (autoNext) {
                syncRunningTimerNotificationsAndAlarms()
            } else {
                timerJob?.cancel()
                syncWhiteNoise()
            }
        }
    }

    private fun recordCompletedFocusSession(
        taskId: String?,
        durationMinutes: Int,
        isStopwatch: Boolean,
        incrementPomodoro: Boolean
    ) {
        val now = System.currentTimeMillis()
        commitData { data ->
            val task = data.tasks.find { it.id == taskId }
            val lang = data.settings.language
            val fallbackTitle = if (lang == AppLanguage.AR) "تركيز حر" else "Free Focus"
            val record = FocusRecord(
                taskId = taskId,
                taskTitle = task?.title ?: fallbackTitle,
                projectId = task?.projectId,
                startedAtEpochMillis = now - durationMinutes * 60_000L,
                durationMinutes = durationMinutes,
                isStopwatch = isStopwatch
            )

            val updatedTasks = if (taskId != null) {
                data.tasks.map { item ->
                    if (item.id == taskId) {
                        item.copy(
                            completedPomodoros = if (incrementPomodoro) item.completedPomodoros + 1 else item.completedPomodoros,
                            elapsedFocusMinutes = item.elapsedFocusMinutes + durationMinutes
                        )
                    } else item
                }
            } else data.tasks

            val earnedPoints = durationMinutes * 10
            val totalGrowth = data.forestState.currentGrowthMinutes + durationMinutes
            val newTrees = if (durationMinutes >= 5) {
                listOf(
                    PlantedTree(
                        species = data.forestState.activeSpecies,
                        plantedAtEpochMillis = now,
                        durationMinutes = durationMinutes
                    )
                ) + data.forestState.plantedHistory
            } else data.forestState.plantedHistory

            val updatedForest = data.forestState.copy(
                sunlightPoints = data.forestState.sunlightPoints + earnedPoints,
                currentGrowthMinutes = totalGrowth % 120,
                plantedHistory = newTrees
            )

            data.copy(
                tasks = updatedTasks,
                focusRecords = listOf(record) + data.focusRecords,
                forestState = updatedForest
            )
        }
        UnreadBadgeTracker.recordStatsAndForestActivity(
            context = appContext,
            recordReports = true,
            recordForest = durationMinutes >= 5
        )
    }

    fun addManualFocusRecord(
        taskId: String?,
        projectId: String?,
        epochDay: Long,
        hour: Int,
        minute: Int,
        durationMinutes: Int
    ) {
        val mins = durationMinutes.coerceIn(1, 300)
        val startedAt = DateUtils.millisFromEpochDay(epochDay, hour, minute)
        commitData { data ->
            val task = data.tasks.find { it.id == taskId }
            val fallbackTitle = if (data.settings.language == AppLanguage.AR) "جلسة تركيز يدوية" else "Manual Focus Session"
            val record = FocusRecord(
                taskId = taskId,
                taskTitle = task?.title ?: fallbackTitle,
                projectId = task?.projectId ?: projectId,
                startedAtEpochMillis = startedAt,
                durationMinutes = mins
            )
            val updatedTasks = if (taskId != null) {
                data.tasks.map {
                    if (it.id == taskId) {
                        it.copy(
                            elapsedFocusMinutes = it.elapsedFocusMinutes + mins,
                            completedPomodoros = it.completedPomodoros + (mins / data.settings.pomodoroMinutes.coerceAtLeast(1))
                        )
                    } else it
                }
            } else data.tasks

            val updatedForest = data.forestState.copy(
                sunlightPoints = data.forestState.sunlightPoints + (mins * 10)
            )

            data.copy(
                tasks = updatedTasks,
                focusRecords = (listOf(record) + data.focusRecords).sortedByDescending { it.startedAtEpochMillis },
                forestState = updatedForest
            )
        }
        UnreadBadgeTracker.recordStatsAndForestActivity(
            context = appContext,
            recordReports = true,
            recordForest = false
        )
    }

    // --- White Noise ---

    fun setWhiteNoise(type: WhiteNoiseType) {
        updateSettings { it.copy(whiteNoise = type) }
    }

    fun setWhiteNoiseVolume(volume: Float) {
        soundSynthesizer.setVolume(volume)
        updateSettings { it.copy(whiteNoiseVolume = volume.coerceIn(0f, 1f)) }
    }

    private fun syncWhiteNoise() {
        val state = _uiState.value
        val settings = state.data.settings
        val shouldPlay = state.timer.isRunning && state.timer.phase == TimerPhase.FOCUS
        soundSynthesizer.updateWhiteNoise(settings.whiteNoise, shouldPlay, settings.whiteNoiseVolume)
    }

    // --- Forest Gamification ---

    fun unlockOrSelectSpecies(species: PlantSpecies) {
        commitData { data ->
            val forest = data.forestState
            if (forest.unlockedSpecies.contains(species)) {
                data.copy(forestState = forest.copy(activeSpecies = species))
            } else if (forest.sunlightPoints >= species.costPoints) {
                data.copy(
                    forestState = forest.copy(
                        sunlightPoints = forest.sunlightPoints - species.costPoints,
                        unlockedSpecies = forest.unlockedSpecies + species,
                        activeSpecies = species
                    )
                )
            } else {
                data
            }
        }
    }

    // --- Settings & Backup ---

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val prevSettings = _uiState.value.data.settings
        commitData { data ->
            data.copy(settings = transform(data.settings))
        }
        val newSettings = _uiState.value.data.settings
        val timer = _uiState.value.timer

        if (!timer.hasStarted && timer.phase == TimerPhase.FOCUS &&
            prevSettings.pomodoroMinutes != newSettings.pomodoroMinutes
        ) {
            val secs = getFocusDurationSeconds(timer.activeTaskId, newSettings)
            _uiState.update {
                it.copy(timer = it.timer.copy(totalPhaseSeconds = secs, remainingSeconds = secs))
            }
        }

        // Handle Task Reminders Toggle
        if (prevSettings.enableTaskReminders != newSettings.enableTaskReminders) {
            if (newSettings.enableTaskReminders) {
                requestNativePermissionsIfMissing()
                FocusNotificationManager.syncAllTaskReminders(
                    context = appContext,
                    tasks = _uiState.value.data.tasks,
                    settings = newSettings
                )
            } else {
                FocusNotificationManager.cancelAllTaskReminders(
                    context = appContext,
                    tasks = _uiState.value.data.tasks
                )
            }
        }

        // Handle Persistent Background Timer Notification Toggle
        if (prevSettings.persistentTimerNotification != newSettings.persistentTimerNotification) {
            if (newSettings.persistentTimerNotification) {
                requestNativePermissionsIfMissing()
                if (timer.isRunning) updateOngoingNotificationTick()
            } else {
                FocusNotificationManager.cancelOngoingTimerNotification(appContext)
            }
        }

        // Handle Alarm Sound & Vibration Toggle
        if (prevSettings.alarmSoundAndVibration != newSettings.alarmSoundAndVibration ||
            prevSettings.alarmEnabled != newSettings.alarmEnabled
        ) {
            if (timer.isRunning && timer.countMode == TimerCountMode.COUNTDOWN) {
                syncRunningTimerNotificationsAndAlarms()
            }
            if (newSettings.enableTaskReminders) {
                FocusNotificationManager.syncAllTaskReminders(
                    context = appContext,
                    tasks = _uiState.value.data.tasks,
                    settings = newSettings
                )
            }
        }

        syncWhiteNoise()
    }

    fun toggleDarkTheme() {
        updateSettings { it.copy(darkTheme = !it.darkTheme) }
    }

    fun toggleLanguage() {
        updateSettings {
            it.copy(language = if (it.language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR)
        }
    }

    fun exportBackupJson(): String {
        return repository.exportJson(_uiState.value.data)
    }

    fun exportManualBackupToDocuments(onResult: (Result<String>) -> Unit) {
        exportManualBackupWithSummary { res ->
            onResult(res.map { it.fileName })
        }
    }

    fun exportManualBackupWithSummary(onResult: (Result<ManualBackupExportSummary>) -> Unit) {
        viewModelScope.launch {
            val res = try {
                repository.exportManualBackupWithSummary(_uiState.value.data)
            } catch (e: Exception) {
                Result.failure(e)
            }
            val updatedBackupTs = repository.getLastAutoBackupTimeMillis()
            _uiState.update { it.copy(lastAutoBackupTimeMillis = updatedBackupTs) }
            onResult(res)
        }
    }

    fun createShareableBackupFile(): java.io.File {
        return repository.createShareableBackupFile(_uiState.value.data)
    }

    fun parseBackupPreview(json: String): AppBackupState? {
        return repository.parseBackupJson(json)
    }

    fun importBackupJson(json: String, mode: BackupImportMode = BackupImportMode.REPLACE): Boolean {
        val parsed = repository.parseBackupJson(json) ?: return false
        importBackupState(parsed, mode)
        return true
    }

    fun importBackupState(parsed: AppBackupState, mode: BackupImportMode) {
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        val currentData = _uiState.value.data
        val previewTarget = when (mode) {
            BackupImportMode.MERGE -> repository.mergeBackupStates(currentData, parsed)
            BackupImportMode.REPLACE -> parsed
        }

        val secs = previewTarget.settings.pomodoroMinutes * 60
        _uiState.update {
            it.copy(
                data = previewTarget,
                selectedTaskId = null,
                timer = TimerUiState(
                    totalPhaseSeconds = secs,
                    remainingSeconds = secs
                )
            )
        }
        viewModelScope.launch {
            val restored = repository.restoreFullStateAtomically(
                current = currentData,
                imported = parsed,
                mode = mode
            )
            val updatedBackupTs = repository.getLastAutoBackupTimeMillis()
            val restoredSecs = restored.settings.pomodoroMinutes * 60
            _uiState.update {
                it.copy(
                    data = restored,
                    selectedTaskId = null,
                    timer = TimerUiState(
                        totalPhaseSeconds = restoredSecs,
                        remainingSeconds = restoredSecs,
                        strictMode = restored.settings.strictBlockerEnabled
                    ),
                    lastAutoBackupTimeMillis = updatedBackupTs
                )
            }
            StrictAppBlockerManager.setStrictBlockerEnabled(appContext, restored.settings.strictBlockerEnabled)
            StrictAppBlockerManager.setWhitelistedPackages(appContext, restored.settings.whitelistedPackages.toSet())
            syncStrictBlockerTimerState()
            FocusNotificationManager.syncAllTaskReminders(appContext, restored.tasks, restored.settings)
            syncWhiteNoise()
        }
    }

    fun resetDemoData() {
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
        UnreadBadgeTracker.resetAll(appContext)

        val demo = SeedData.createInitialState()
        val secs = demo.settings.pomodoroMinutes * 60
        _uiState.update {
            it.copy(
                data = demo,
                destination = NavDestination.SmartList(SmartListType.TODAY),
                selectedTaskId = null,
                timer = TimerUiState(
                    totalPhaseSeconds = secs,
                    remainingSeconds = secs
                )
            )
        }
        viewModelScope.launch {
            repository.saveState(demo)
            val updatedBackupTs = repository.getLastAutoBackupTimeMillis()
            _uiState.update { it.copy(lastAutoBackupTimeMillis = updatedBackupTs) }
        }
        FocusNotificationManager.syncAllTaskReminders(appContext, demo.tasks, demo.settings)
        syncWhiteNoise()
    }

    // --- Tomato AI Autonomous Chat & Tool Execution ---

    fun setShowTomatoAi(show: Boolean) {
        if (show) {
            val currentKey = TomatoAiService.getSavedApiKey(appContext)
            val currentModel = TomatoAiService.getSavedModel(appContext)
            _uiState.update {
                it.copy(
                    showTomatoAi = true,
                    tomatoAiApiKey = currentKey,
                    tomatoAiSelectedModel = currentModel
                )
            }
        } else {
            _uiState.update { it.copy(showTomatoAi = false) }
        }
    }

    fun saveTomatoAiApiKey(apiKey: String) {
        val clean = apiKey.trim()
        TomatoAiService.saveApiKey(appContext, clean)
        _uiState.update { it.copy(tomatoAiApiKey = clean) }
        // Trigger a state save so the API key in SharedPreferences is also included in local backup
        commitData { it }
    }

    fun selectTomatoAiModel(model: TomatoGeminiModel) {
        TomatoAiService.saveSelectedModel(appContext, model)
        _uiState.update { it.copy(tomatoAiSelectedModel = model) }
        commitData { it }
    }

    fun clearTomatoAiChat() {
        _uiState.update { it.copy(tomatoAiMessages = emptyList()) }
    }

    fun sendTomatoAiMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isEmpty() || _uiState.value.tomatoAiIsLoading) return

        val apiKey = _uiState.value.tomatoAiApiKey.ifBlank {
            TomatoAiService.getSavedApiKey(appContext)
        }
        val selectedModel = _uiState.value.tomatoAiSelectedModel
        val historyBefore = _uiState.value.tomatoAiMessages
        val userMsg = TomatoChatMessage(
            isUser = true,
            text = cleanText
        )

        _uiState.update {
            it.copy(
                tomatoAiMessages = it.tomatoAiMessages + userMsg,
                tomatoAiIsLoading = true
            )
        }

        viewModelScope.launch {
            try {
                val result = TomatoAiService.sendMessageWithAutonomousTools(
                    apiKey = apiKey,
                    selectedModelId = selectedModel.modelId,
                    conversationHistory = historyBefore,
                    userMessage = cleanText,
                    executor = this@FocusTodoViewModel
                )
                result.fold(
                    onSuccess = { replyMsg ->
                        _uiState.update {
                            it.copy(
                                tomatoAiMessages = it.tomatoAiMessages + replyMsg,
                                tomatoAiIsLoading = false
                            )
                        }
                    },
                    onFailure = { err ->
                        val errMsg = TomatoChatMessage(
                            isUser = false,
                            text = err.message ?: "حدث خطأ أثناء الاتصال بـ Gemini.",
                            isError = true
                        )
                        _uiState.update {
                            it.copy(
                                tomatoAiMessages = it.tomatoAiMessages + errMsg,
                                tomatoAiIsLoading = false
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errMsg = TomatoChatMessage(
                    isUser = false,
                    text = e.message ?: "حدث خطأ غير متوقع.",
                    isError = true
                )
                _uiState.update {
                    it.copy(
                        tomatoAiMessages = it.tomatoAiMessages + errMsg,
                        tomatoAiIsLoading = false
                    )
                }
            }
        }
    }

    override fun getCurrentStateSnapshot(): Pair<AppBackupState, TimerUiState> {
        val s = _uiState.value
        return s.data to s.timer
    }

    private fun resolveOrCreateProjectId(projectQuery: String?): Pair<String?, String?> {
        val q = projectQuery?.trim().orEmpty()
        if (q.isEmpty() || q.equals("null", ignoreCase = true) || q.equals("inbox", ignoreCase = true)) {
            return null to null
        }

        val currentData = _uiState.value.data
        // 1. Exact ID or Name match
        currentData.projects.find {
            it.id.equals(q, ignoreCase = true) || it.name.equals(q, ignoreCase = true)
        }?.let { return it.id to it.name }

        // 2. Substring / stripped-emoji match on Project Name
        val strippedQ = q.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (strippedQ.isNotEmpty()) {
            currentData.projects.find { proj ->
                val strippedProj = proj.name.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
                strippedProj.equals(strippedQ, ignoreCase = true) ||
                    strippedProj.contains(strippedQ, ignoreCase = true) ||
                    strippedQ.contains(strippedProj, ignoreCase = true)
            }?.let { return it.id to it.name }
        }

        // 3. Match on Folder Name or Folder ID -> pick first project in that folder
        val matchedFolder = currentData.folders.find { folder ->
            val strippedFolder = folder.name.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
            folder.id.equals(q, ignoreCase = true) ||
                folder.name.equals(q, ignoreCase = true) ||
                (strippedQ.isNotEmpty() && (
                    strippedFolder.contains(strippedQ, ignoreCase = true) ||
                        strippedQ.contains(strippedFolder, ignoreCase = true)
                    ))
        }
        if (matchedFolder != null) {
            val childProj = currentData.projects.firstOrNull { it.folderId == matchedFolder.id }
            if (childProj != null) {
                return childProj.id to childProj.name
            }
        }

        // 4. Create new project if not found
        val now = System.currentTimeMillis()
        val newProj = ProjectItem(
            name = q,
            colorHex = "#E05246",
            folderId = matchedFolder?.id,
            sortOrder = currentData.projects.size,
            createdAt = now,
            updatedAt = now
        )
        commitData { data ->
            data.copy(projects = data.projects + newProj)
        }
        return newProj.id to newProj.name
    }

    private fun resolveOrCreateTagIds(tagQuery: String?): List<String> {
        val q = tagQuery?.trim()?.removePrefix("#").orEmpty()
        if (q.isEmpty() || q.equals("null", ignoreCase = true) || q.equals("none", ignoreCase = true)) {
            return emptyList()
        }
        val currentData = _uiState.value.data
        val existing = currentData.tags.find {
            it.id.equals(q, ignoreCase = true) ||
                it.name.equals(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true)
        }
        if (existing != null) {
            return listOf(existing.id)
        }
        val newTag = TagItem(name = q, colorHex = "#3B7BF6")
        commitData { data ->
            data.copy(tags = data.tags + newTag)
        }
        return listOf(newTag.id)
    }

    private fun findTaskByIdOrTitle(taskId: String?, targetTitle: String?): TaskItem? {
        val tasks = _uiState.value.data.tasks
        if (!taskId.isNullOrBlank()) {
            tasks.find { it.id == taskId }?.let { return it }
        }
        if (!targetTitle.isNullOrBlank()) {
            val clean = targetTitle.trim()
            tasks.find { it.title.equals(clean, ignoreCase = true) }?.let { return it }
            tasks.find {
                it.title.contains(clean, ignoreCase = true) ||
                    clean.contains(it.title, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    override fun executeAddTask(
        title: String,
        estimatedPomodoros: Int,
        reminderTime: String?,
        projectQuery: String?,
        tagQuery: String?,
        dueDateEpochDay: Long?,
        priority: TaskPriority,
        notes: String
    ): TomatoToolExecutionResult {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return TomatoToolExecutionResult(
                functionName = "addTask",
                resultJson = JSONObject().put("status", "error").put("message", "Task title is empty"),
                humanSummary = "⚠️ لم يتم تحديد عنوان المهمة"
            )
        }

        if (reminderTime != null) {
            requestNativePermissionsIfMissing()
        }

        val (resolvedProjId, resolvedProjName) = resolveOrCreateProjectId(projectQuery)
        val resolvedTagIds = resolveOrCreateTagIds(tagQuery)
        val safePoms = estimatedPomodoros.coerceIn(1, 20)

        val task = TaskItem(
            title = cleanTitle,
            projectId = resolvedProjId,
            tagIds = resolvedTagIds,
            priority = priority,
            estimatedPomodoros = safePoms,
            dueDateEpochDay = dueDateEpochDay ?: DateUtils.todayEpochDay(),
            isSomeday = false,
            isEvent = false,
            reminderTime = reminderTime,
            notes = notes
        )

        commitData { data ->
            data.copy(tasks = listOf(task) + data.tasks)
        }
        FocusNotificationManager.scheduleTaskReminder(appContext, task, _uiState.value.data.settings)

        val alarmPart = if (reminderTime != null) " • ⏰ $reminderTime" else ""
        val projPart = if (resolvedProjName != null) " • 📁 $resolvedProjName" else ""
        val summary = "✅ تمت إضافة مهمة: \"$cleanTitle\" (🍅 $safePoms$alarmPart$projPart)"

        return TomatoToolExecutionResult(
            functionName = "addTask",
            resultJson = JSONObject().apply {
                put("status", "success")
                put("taskId", task.id)
                put("title", task.title)
                put("estimatedPomodoros", safePoms)
                put("reminderTime", reminderTime ?: "NONE")
                put("project", resolvedProjName ?: "Inbox")
            },
            humanSummary = summary
        )
    }

    override fun executeUpdateTask(
        taskId: String?,
        targetTitle: String?,
        newTitle: String?,
        estimatedPomodoros: Int?,
        reminderTime: String?,
        projectQuery: String?,
        tagQuery: String?,
        dueDateEpochDay: Long?,
        priority: TaskPriority?,
        notes: String?
    ): TomatoToolExecutionResult {
        val target = findTaskByIdOrTitle(taskId, targetTitle)
            ?: return TomatoToolExecutionResult(
                functionName = "updateTask",
                resultJson = JSONObject().put("status", "error").put("message", "Task not found"),
                humanSummary = "⚠️ لم يتم العثور على المهمة المطلوبة للتعديل"
            )

        val (resolvedProjId, resolvedProjName) = if (projectQuery != null) {
            resolveOrCreateProjectId(projectQuery)
        } else {
            target.projectId to null
        }
        val resolvedTags = if (tagQuery != null) {
            resolveOrCreateTagIds(tagQuery)
        } else {
            target.tagIds
        }

        val updatedReminder = when (reminderTime) {
            null -> target.reminderTime
            "NONE" -> null
            else -> reminderTime
        }

        updateTask(target.id) { existing ->
            existing.copy(
                title = newTitle?.takeIf { it.isNotBlank() } ?: existing.title,
                estimatedPomodoros = estimatedPomodoros?.coerceIn(1, 20) ?: existing.estimatedPomodoros,
                reminderTime = updatedReminder,
                projectId = resolvedProjId,
                tagIds = resolvedTags,
                dueDateEpochDay = dueDateEpochDay ?: existing.dueDateEpochDay,
                priority = priority ?: existing.priority,
                notes = notes ?: existing.notes
            )
        }

        val finalTitle = newTitle?.takeIf { it.isNotBlank() } ?: target.title
        val alarmNote = if (updatedReminder != null) " • ⏰ $updatedReminder" else ""
        val projNote = if (resolvedProjName != null) " • 📁 $resolvedProjName" else ""

        return TomatoToolExecutionResult(
            functionName = "updateTask",
            resultJson = JSONObject().apply {
                put("status", "success")
                put("taskId", target.id)
                put("title", finalTitle)
                put("reminderTime", updatedReminder ?: "NONE")
            },
            humanSummary = "✏️ تم تحديث المهمة: \"$finalTitle\"$alarmNote$projNote"
        )
    }

    override fun executeCompleteTask(
        taskId: String?,
        targetTitle: String?,
        completed: Boolean
    ): TomatoToolExecutionResult {
        val target = findTaskByIdOrTitle(taskId, targetTitle)
            ?: return TomatoToolExecutionResult(
                functionName = "completeTask",
                resultJson = JSONObject().put("status", "error").put("message", "Task not found"),
                humanSummary = "⚠️ لم يتم العثور على المهمة"
            )

        if (target.isCompleted != completed) {
            toggleTaskCompleted(target.id)
        }

        return TomatoToolExecutionResult(
            functionName = "completeTask",
            resultJson = JSONObject().apply {
                put("status", "success")
                put("taskId", target.id)
                put("title", target.title)
                put("completed", completed)
            },
            humanSummary = if (completed) {
                "☑️ تم إنجاز المهمة: \"${target.title}\""
            } else {
                "🔄 تمت إعادة فتح المهمة: \"${target.title}\""
            }
        )
    }

    override fun executeDeleteTask(
        taskId: String?,
        targetTitle: String?
    ): TomatoToolExecutionResult {
        val target = findTaskByIdOrTitle(taskId, targetTitle)
            ?: return TomatoToolExecutionResult(
                functionName = "deleteTask",
                resultJson = JSONObject().put("status", "error").put("message", "Task not found"),
                humanSummary = "⚠️ لم يتم العثور على المهمة لحذفها"
            )

        deleteTask(target.id)

        return TomatoToolExecutionResult(
            functionName = "deleteTask",
            resultJson = JSONObject().apply {
                put("status", "success")
                put("deletedTaskId", target.id)
                put("deletedTitle", target.title)
            },
            humanSummary = "🗑️ تم حذف المهمة: \"${target.title}\""
        )
    }

    override fun executeCreateProject(
        name: String,
        colorHex: String?,
        folderName: String?
    ): TomatoToolExecutionResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            return TomatoToolExecutionResult(
                functionName = "createProject",
                resultJson = JSONObject().put("status", "error").put("message", "Project name is empty"),
                humanSummary = "⚠️ اسم المشروع فارغ"
            )
        }

        val currentData = _uiState.value.data
        var targetFolderId: String? = null
        if (!folderName.isNullOrBlank()) {
            val existingFolder = currentData.folders.find {
                it.name.equals(folderName.trim(), ignoreCase = true) ||
                    it.name.contains(folderName.trim(), ignoreCase = true)
            }
            if (existingFolder != null) {
                targetFolderId = existingFolder.id
            } else {
                val newFolder = FolderItem(name = folderName.trim())
                targetFolderId = newFolder.id
                commitData { data ->
                    data.copy(folders = data.folders + newFolder)
                }
            }
        }

        val safeColor = if (!colorHex.isNullOrBlank() && colorHex.startsWith("#")) colorHex else "#E05246"
        saveProject(null, cleanName, safeColor, targetFolderId)

        return TomatoToolExecutionResult(
            functionName = "createProject",
            resultJson = JSONObject().apply {
                put("status", "success")
                put("projectName", cleanName)
                put("folderName", folderName ?: "Root")
            },
            humanSummary = "📁 تم إنشاء المشروع: \"$cleanName\""
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        soundSynthesizer.release()
    }
}
