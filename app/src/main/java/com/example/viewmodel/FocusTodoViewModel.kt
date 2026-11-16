package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundSynthesizer
import com.example.data.CloudSyncStatus
import com.example.data.DateUtils
import com.example.data.FocusRepository
import com.example.data.SeedData
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.FocusRecord
import com.example.model.FolderItem
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
import com.example.ui.components.formatSecondsMmSs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val data: AppBackupState = AppBackupState(),
    val destination: NavDestination = NavDestination.SmartList(SmartListType.TODAY),
    val sortOption: TaskSortOption = TaskSortOption.PRIORITY,
    val showCompletedAccordion: Boolean = true,
    val selectedTaskId: String? = null,
    val showFullScreenTimer: Boolean = false,
    val showReports: Boolean = false,
    val showForest: Boolean = false,
    val showSettings: Boolean = false,
    val showBackgroundPermissionsModal: Boolean = false,
    val showAuthScreen: Boolean = false,
    val currentUserEmail: String? = null,
    val currentUserId: String? = null,
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.UNAUTHENTICATED,
    val firebaseProjectId: String = "focus-zohayr",
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val timer: TimerUiState = TimerUiState(),
    val systemPermissions: SystemPermissionSnapshot = SystemPermissionSnapshot(),
    val permissionRequestTrigger: Int = 0,
    val statusBannerMessage: String? = null
)

class FocusTodoViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = FocusRepository(appContext)
    private val soundSynthesizer = SoundSynthesizer()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var cloudSyncJob: Job? = null

    init {
        FocusNotificationManager.ensureChannelsCreated(appContext)

        viewModelScope.launch {
            val activeSession = repository.getActiveSessionSync()
            val loaded = repository.loadState()
            val defaultFocusSecs = loaded.settings.pomodoroMinutes * 60
            val restoredTimer = restorePersistedTimer(loaded.persistedTimer, defaultFocusSecs)
            val permSnapshot = SystemPermissionsHelper.getSnapshot(appContext)
            val shouldPromptOnLaunch = !permSnapshot.notificationsGranted
            val initialSyncStatus = when {
                activeSession == null -> CloudSyncStatus.UNAUTHENTICATED
                activeSession.pendingCloudPush || activeSession.idToken.isNullOrBlank() -> CloudSyncStatus.OFFLINE_CACHED
                else -> CloudSyncStatus.SYNCED
            }

            _uiState.update {
                it.copy(
                    isLoaded = true,
                    data = loaded,
                    currentUserEmail = activeSession?.email,
                    currentUserId = activeSession?.userId,
                    cloudSyncStatus = initialSyncStatus,
                    firebaseProjectId = repository.firebaseProjectId,
                    timer = restoredTimer,
                    systemPermissions = permSnapshot,
                    permissionRequestTrigger = if (shouldPromptOnLaunch) it.permissionRequestTrigger + 1 else it.permissionRequestTrigger
                )
            }

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
                    startTimerTicker()
                }
            }

            if (activeSession != null) {
                startBackgroundCloudSyncLoop()
            }
        }

        // Listen for status-bar notification action button broadcasts
        viewModelScope.launch {
            FocusNotificationBus.events.collect { event ->
                when (event) {
                    is ExternalNotificationEvent.PauseTimer -> pauseTimer()
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
                val pushed = repository.saveState(nextData)
                if (_uiState.value.currentUserEmail != null) {
                    _uiState.update {
                        it.copy(
                            cloudSyncStatus = if (pushed) CloudSyncStatus.SYNCED else CloudSyncStatus.OFFLINE_CACHED
                        )
                    }
                }
            }
            state.copy(data = nextData)
        }
    }

    private fun saveCurrentTimerToStorage() {
        val state = _uiState.value
        val nextData = state.data.copy(persistedTimer = state.timer.toPersisted())
        _uiState.update { it.copy(data = nextData) }
        viewModelScope.launch {
            val pushed = repository.saveState(nextData)
            if (_uiState.value.currentUserEmail != null) {
                _uiState.update {
                    it.copy(
                        cloudSyncStatus = if (pushed) CloudSyncStatus.SYNCED else CloudSyncStatus.OFFLINE_CACHED
                    )
                }
            }
        }
    }

    fun refreshSystemPermissions() {
        val snapshot = SystemPermissionsHelper.getSnapshot(appContext)
        _uiState.update { it.copy(systemPermissions = snapshot) }
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
        if (_uiState.value.currentUserEmail != null) {
            manualCloudSync()
        }
    }

    fun selectDestination(dest: NavDestination) {
        _uiState.update { it.copy(destination = dest, selectedTaskId = null) }
    }

    fun setSortOption(option: TaskSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun toggleShowCompletedAccordion() {
        _uiState.update { it.copy(showCompletedAccordion = !it.showCompletedAccordion) }
    }

    fun openTaskInspector(taskId: String?) {
        _uiState.update { it.copy(selectedTaskId = taskId) }
    }

    fun setShowFullScreenTimer(show: Boolean) {
        _uiState.update { it.copy(showFullScreenTimer = show) }
    }

    fun setShowReports(show: Boolean) {
        _uiState.update { it.copy(showReports = show) }
    }

    fun setShowForest(show: Boolean) {
        _uiState.update { it.copy(showForest = show) }
    }

    fun setShowSettings(show: Boolean) {
        if (show) refreshSystemPermissions()
        _uiState.update { it.copy(showSettings = show) }
    }

    fun setShowBackgroundPermissionsModal(show: Boolean) {
        if (show) refreshSystemPermissions()
        _uiState.update { it.copy(showBackgroundPermissionsModal = show) }
    }

    fun setShowAuthScreen(show: Boolean) {
        _uiState.update { it.copy(showAuthScreen = show, authErrorMessage = null) }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun signUp(email: String, password: String) {
        val isArabic = _uiState.value.data.settings.language == AppLanguage.AR
        _uiState.update {
            it.copy(
                isAuthLoading = true,
                authErrorMessage = null,
                cloudSyncStatus = CloudSyncStatus.SYNCING
            )
        }
        viewModelScope.launch {
            val result = repository.signUp(email, password)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                val userState = repository.loadState()
                val secs = userState.settings.pomodoroMinutes * 60
                val restoredTimer = restorePersistedTimer(userState.persistedTimer, secs)
                val status = if (session.pendingCloudPush || session.idToken.isNullOrBlank()) {
                    CloudSyncStatus.OFFLINE_CACHED
                } else {
                    CloudSyncStatus.SYNCED
                }
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        showAuthScreen = false,
                        currentUserEmail = session.email,
                        currentUserId = session.userId,
                        cloudSyncStatus = status,
                        data = userState,
                        timer = restoredTimer
                    )
                }
                FocusNotificationManager.syncAllTaskReminders(appContext, userState.tasks, userState.settings)
                startBackgroundCloudSyncLoop()
            } else {
                val code = result.exceptionOrNull()?.message ?: "AUTH_FAILED"
                val localizedError = localizeAuthError(code, isArabic)
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authErrorMessage = localizedError,
                        cloudSyncStatus = CloudSyncStatus.UNAUTHENTICATED
                    )
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        val isArabic = _uiState.value.data.settings.language == AppLanguage.AR
        _uiState.update {
            it.copy(
                isAuthLoading = true,
                authErrorMessage = null,
                cloudSyncStatus = CloudSyncStatus.SYNCING
            )
        }
        viewModelScope.launch {
            val result = repository.signIn(email, password)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                val userState = repository.loadState()
                val secs = userState.settings.pomodoroMinutes * 60
                val restoredTimer = restorePersistedTimer(userState.persistedTimer, secs)
                val status = if (session.pendingCloudPush || session.idToken.isNullOrBlank()) {
                    CloudSyncStatus.OFFLINE_CACHED
                } else {
                    CloudSyncStatus.SYNCED
                }
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        showAuthScreen = false,
                        currentUserEmail = session.email,
                        currentUserId = session.userId,
                        cloudSyncStatus = status,
                        data = userState,
                        timer = restoredTimer
                    )
                }
                FocusNotificationManager.syncAllTaskReminders(appContext, userState.tasks, userState.settings)
                startBackgroundCloudSyncLoop()
            } else {
                val code = result.exceptionOrNull()?.message ?: "AUTH_FAILED"
                val localizedError = localizeAuthError(code, isArabic)
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authErrorMessage = localizedError,
                        cloudSyncStatus = CloudSyncStatus.UNAUTHENTICATED
                    )
                }
            }
        }
    }

    fun signOut() {
        cloudSyncJob?.cancel()
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
        viewModelScope.launch {
            repository.signOut()
            val anonymousState = repository.loadState()
            val secs = anonymousState.settings.pomodoroMinutes * 60
            _uiState.update {
                it.copy(
                    currentUserEmail = null,
                    currentUserId = null,
                    cloudSyncStatus = CloudSyncStatus.UNAUTHENTICATED,
                    showAuthScreen = false,
                    selectedTaskId = null,
                    data = anonymousState,
                    timer = TimerUiState(
                        totalPhaseSeconds = secs,
                        remainingSeconds = secs
                    )
                )
            }
            FocusNotificationManager.syncAllTaskReminders(appContext, anonymousState.tasks, anonymousState.settings)
            syncWhiteNoise()
        }
    }

    fun manualCloudSync() {
        if (_uiState.value.currentUserEmail == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSyncStatus = CloudSyncStatus.SYNCING) }
            val (remoteUpdatedState, newStatus) = repository.syncWithCloudIfSignedIn(_uiState.value.data)
            _uiState.update { state ->
                if (remoteUpdatedState != null) {
                    FocusNotificationManager.syncAllTaskReminders(
                        appContext,
                        remoteUpdatedState.tasks,
                        remoteUpdatedState.settings
                    )
                    state.copy(
                        data = remoteUpdatedState,
                        cloudSyncStatus = newStatus
                    )
                } else {
                    state.copy(cloudSyncStatus = newStatus)
                }
            }
        }
    }

    private fun startBackgroundCloudSyncLoop() {
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            while (isActive && _uiState.value.currentUserEmail != null) {
                delay(15_000L)
                if (_uiState.value.currentUserEmail == null) break
                val (remoteUpdatedState, newStatus) = repository.syncWithCloudIfSignedIn(_uiState.value.data)
                _uiState.update { state ->
                    if (remoteUpdatedState != null) {
                        FocusNotificationManager.syncAllTaskReminders(
                            appContext,
                            remoteUpdatedState.tasks,
                            remoteUpdatedState.settings
                        )
                        state.copy(
                            data = remoteUpdatedState,
                            cloudSyncStatus = newStatus
                        )
                    } else {
                        state.copy(cloudSyncStatus = newStatus)
                    }
                }
            }
        }
    }

    private fun localizeAuthError(code: String, isArabic: Boolean): String = when (code) {
        "INVALID_EMAIL" -> if (isArabic) "يرجى إدخال بريد إلكتروني صحيح." else "Please enter a valid email address."
        "WEAK_PASSWORD" -> if (isArabic) "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل." else "Password must be at least 6 characters."
        "EMPTY_PASSWORD" -> if (isArabic) "يرجى إدخال كلمة المرور." else "Please enter your password."
        "EMAIL_EXISTS" -> if (isArabic) "هذا البريد الإلكتروني مسجل بالفعل. قم بتسجيل الدخول." else "This email is already registered. Please sign in."
        "ACCOUNT_NOT_FOUND" -> if (isArabic) "لم يتم العثور على حساب بهذا البريد. أنشئ حساباً جديداً." else "No account found with this email. Please create an account."
        "WRONG_PASSWORD" -> if (isArabic) "البريد الإلكتروني أو كلمة المرور غير صحيحة." else "Invalid email or password."
        else -> if (isArabic) "تعذر الاتصال بالخادم ($code)." else "Authentication failed ($code)."
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
        commitData { data ->
            data.copy(
                tasks = data.tasks.map { if (it.id == taskId) transform(it) else it }
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
            task.copy(subtasks = task.subtasks + SubtaskItem(title = clean))
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
        commitData { data ->
            if (existingId == null) {
                data.copy(
                    projects = data.projects + ProjectItem(
                        name = clean,
                        colorHex = colorHex,
                        folderId = folderId
                    )
                )
            } else {
                data.copy(
                    projects = data.projects.map {
                        if (it.id == existingId) {
                            it.copy(name = clean, colorHex = colorHex, folderId = folderId)
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
        return if (state.data.settings.language == AppLanguage.AR) "تركيز حر" else "Free Focus"
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
                    hasStarted = true
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
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
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
        _uiState.update { state ->
            state.copy(timer = state.timer.copy(strictMode = !state.timer.strictMode))
        }
        saveCurrentTimerToStorage()
        if (_uiState.value.timer.isRunning) {
            updateOngoingNotificationTick()
        }
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
        if (!t.isRunning) return
        val displaySecs = if (t.countMode == TimerCountMode.STOPWATCH && t.phase == TimerPhase.FOCUS) {
            t.stopwatchSeconds
        } else {
            t.remainingSeconds
        }
        FocusNotificationManager.showOrUpdateOngoingTimerNotification(
            context = appContext,
            taskTitle = activeTaskOrDefaultTitle(),
            phase = t.phase,
            formattedTime = formatSecondsMmSs(displaySecs),
            strictMode = t.strictMode,
            settings = state.data.settings
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

    fun importBackupJson(json: String): Boolean {
        val parsed = repository.parseBackupJson(json) ?: return false
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

        val secs = parsed.settings.pomodoroMinutes * 60
        _uiState.update {
            it.copy(
                data = parsed,
                selectedTaskId = null,
                timer = TimerUiState(
                    totalPhaseSeconds = secs,
                    remainingSeconds = secs
                )
            )
        }
        viewModelScope.launch { repository.saveState(parsed) }
        FocusNotificationManager.syncAllTaskReminders(appContext, parsed.tasks, parsed.settings)
        syncWhiteNoise()
        return true
    }

    fun resetDemoData() {
        timerJob?.cancel()
        FocusNotificationManager.cancelOngoingTimerNotification(appContext)
        FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)

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
        viewModelScope.launch { repository.saveState(demo) }
        FocusNotificationManager.syncAllTaskReminders(appContext, demo.tasks, demo.settings)
        syncWhiteNoise()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        cloudSyncJob?.cancel()
        soundSynthesizer.release()
    }
}
