package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundSynthesizer
import com.example.data.DateUtils
import com.example.data.FocusRepository
import com.example.data.SeedData
import com.example.model.AppBackupState
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.FocusRecord
import com.example.model.FolderItem
import com.example.model.ForestState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

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
    val timer: TimerUiState = TimerUiState(),
    val statusBannerMessage: String? = null
)

class FocusTodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FocusRepository(application.applicationContext)
    private val soundSynthesizer = SoundSynthesizer()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = repository.loadState()
            val defaultFocusSecs = loaded.settings.pomodoroMinutes * 60
            _uiState.update {
                it.copy(
                    isLoaded = true,
                    data = loaded,
                    timer = it.timer.copy(
                        totalPhaseSeconds = defaultFocusSecs,
                        remainingSeconds = defaultFocusSecs
                    )
                )
            }
        }
    }

    private fun commitData(transform: (AppBackupState) -> AppBackupState) {
        _uiState.update { state ->
            val nextData = transform(state.data)
            viewModelScope.launch {
                repository.saveState(nextData)
            }
            state.copy(data = nextData)
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
        _uiState.update { it.copy(showSettings = show) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Task CRUD ---

    fun addTask(
        title: String,
        estimatedPomodoros: Int,
        dueDateEpochDay: Long?,
        isSomeday: Boolean,
        isEvent: Boolean,
        priority: TaskPriority,
        projectId: String?,
        tagIds: List<String>
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return

        val task = TaskItem(
            title = cleanTitle,
            projectId = projectId,
            tagIds = tagIds,
            priority = priority,
            estimatedPomodoros = estimatedPomodoros.coerceIn(1, 20),
            dueDateEpochDay = dueDateEpochDay,
            isSomeday = isSomeday,
            isEvent = isEvent
        )
        commitData { data ->
            data.copy(tasks = listOf(task) + data.tasks)
        }
    }

    fun toggleTaskCompleted(taskId: String) {
        val task = _uiState.value.data.tasks.find { it.id == taskId } ?: return
        val newCompleted = !task.isCompleted
        if (newCompleted) {
            soundSynthesizer.playCheckSound()
        }

        commitData { data ->
            val updatedList = data.tasks.map { item ->
                if (item.id == taskId) {
                    item.copy(
                        isCompleted = newCompleted,
                        completedAtEpochMillis = if (newCompleted) System.currentTimeMillis() else null
                    )
                } else item
            }.toMutableList()

            // If completing a recurring task, automatically schedule the next occurrence
            if (newCompleted && task.recurrence != RecurrenceRule.NONE) {
                val baseDay = task.dueDateEpochDay ?: DateUtils.todayEpochDay()
                val nextDay = when (task.recurrence) {
                    RecurrenceRule.DAILY -> baseDay + 1
                    RecurrenceRule.WEEKLY -> baseDay + 7
                    RecurrenceRule.MONTHLY -> baseDay + 30
                    RecurrenceRule.NONE -> baseDay
                }
                updatedList.add(
                    0,
                    task.copy(
                        id = UUID.randomUUID().toString(),
                        dueDateEpochDay = nextDay,
                        completedPomodoros = 0,
                        elapsedFocusMinutes = 0,
                        isCompleted = false,
                        completedAtEpochMillis = null,
                        subtasks = task.subtasks.map { it.copy(id = UUID.randomUUID().toString(), isDone = false) },
                        createdAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }

            data.copy(tasks = updatedList)
        }
    }

    fun updateTask(taskId: String, transform: (TaskItem) -> TaskItem) {
        commitData { data ->
            data.copy(
                tasks = data.tasks.map { if (it.id == taskId) transform(it) else it }
            )
        }
    }

    fun deleteTask(taskId: String) {
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

    // --- Pomodoro Timer Engine ---

    private fun getFocusDurationSeconds(taskId: String?, settings: AppSettings): Int {
        val task = _uiState.value.data.tasks.find { it.id == taskId }
        val mins = task?.customPomodoroMinutes ?: settings.pomodoroMinutes
        return mins.coerceIn(1, 120) * 60
    }

    fun startFocusForTask(taskId: String?, openFullScreen: Boolean = true) {
        val settings = _uiState.value.data.settings
        val focusSecs = getFocusDurationSeconds(taskId, settings)
        _uiState.update { state ->
            state.copy(
                showFullScreenTimer = openFullScreen,
                timer = state.timer.copy(
                    activeTaskId = taskId,
                    phase = TimerPhase.FOCUS,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    isRunning = true,
                    hasStarted = true
                )
            )
        }
        startTimerTicker()
    }

    fun selectActiveFocusTask(taskId: String?) {
        val state = _uiState.value
        if (state.timer.isRunning && state.timer.phase == TimerPhase.FOCUS) {
            // Switch task cleanly without losing running timer
            _uiState.update { it.copy(timer = it.timer.copy(activeTaskId = taskId)) }
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
        }
    }

    fun startOrResumeTimer() {
        _uiState.update { state ->
            state.copy(
                timer = state.timer.copy(isRunning = true, hasStarted = true)
            )
        }
        startTimerTicker()
    }

    fun pauseTimer() {
        if (_uiState.value.timer.strictMode && _uiState.value.timer.phase == TimerPhase.FOCUS) {
            return // Disabled in strict mode
        }
        timerJob?.cancel()
        _uiState.update { it.copy(timer = it.timer.copy(isRunning = false)) }
        syncWhiteNoise()
    }

    fun stopOrAbandonTimer() {
        val timer = _uiState.value.timer
        val settings = _uiState.value.data.settings
        timerJob?.cancel()

        // If Stopwatch mode was used in Focus phase and >= 60s elapsed, log it!
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
                    stopwatchSeconds = 0
                )
            )
        }
        syncWhiteNoise()
    }

    fun skipBreak() {
        val timer = _uiState.value.timer
        val settings = _uiState.value.data.settings
        timerJob?.cancel()
        val focusSecs = getFocusDurationSeconds(timer.activeTaskId, settings)
        val autoStart = settings.autoStartNextPomodoro
        _uiState.update {
            it.copy(
                timer = it.timer.copy(
                    phase = TimerPhase.FOCUS,
                    totalPhaseSeconds = focusSecs,
                    remainingSeconds = focusSecs,
                    stopwatchSeconds = 0,
                    isRunning = autoStart,
                    hasStarted = autoStart
                )
            )
        }
        if (autoStart) startTimerTicker() else syncWhiteNoise()
    }

    fun setTimerCountMode(mode: TimerCountMode) {
        val settings = _uiState.value.data.settings
        val timer = _uiState.value.timer
        timerJob?.cancel()
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
                    stopwatchSeconds = 0
                )
            )
        }
        syncWhiteNoise()
    }

    fun toggleStrictMode() {
        _uiState.update { state ->
            state.copy(timer = state.timer.copy(strictMode = !state.timer.strictMode))
        }
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        syncWhiteNoise()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val state = _uiState.value
                val t = state.timer
                if (!t.isRunning) break

                if (t.countMode == TimerCountMode.STOPWATCH && t.phase == TimerPhase.FOCUS) {
                    _uiState.update {
                        it.copy(timer = it.timer.copy(stopwatchSeconds = it.timer.stopwatchSeconds + 1))
                    }
                } else {
                    val nextRem = t.remainingSeconds - 1
                    if (nextRem <= 0) {
                        onCountdownPhaseCompleted()
                    } else {
                        _uiState.update {
                            it.copy(timer = it.timer.copy(remainingSeconds = nextRem))
                        }
                    }
                }
            }
        }
    }

    private fun onCountdownPhaseCompleted() {
        val state = _uiState.value
        val settings = state.data.settings
        val t = state.timer

        if (settings.alarmEnabled) {
            soundSynthesizer.playCompletionChime()
        }

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
                            isRunning = autoNext,
                            hasStarted = autoNext,
                            strictMode = false
                        )
                    )
                }
                if (!autoNext) {
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
                            isRunning = autoStartBreak,
                            hasStarted = autoStartBreak,
                            strictMode = false
                        )
                    )
                }
                if (!autoStartBreak) {
                    timerJob?.cancel()
                    syncWhiteNoise()
                }
            }
        } else {
            // Break finished -> back to Focus
            val focusSecs = getFocusDurationSeconds(t.activeTaskId, settings)
            val autoNext = settings.autoStartNextPomodoro
            _uiState.update {
                it.copy(
                    timer = it.timer.copy(
                        phase = TimerPhase.FOCUS,
                        totalPhaseSeconds = focusSecs,
                        remainingSeconds = focusSecs,
                        isRunning = autoNext,
                        hasStarted = autoNext
                    )
                )
            }
            if (!autoNext) {
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

            // Forest Gamification: 10 Sunlight Points per minute + plant tree
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
        // If timer hasn't started yet and Pomodoro length changed, update display immediately
        if (!timer.hasStarted && timer.phase == TimerPhase.FOCUS &&
            prevSettings.pomodoroMinutes != newSettings.pomodoroMinutes
        ) {
            val secs = getFocusDurationSeconds(timer.activeTaskId, newSettings)
            _uiState.update {
                it.copy(timer = it.timer.copy(totalPhaseSeconds = secs, remainingSeconds = secs))
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
        syncWhiteNoise()
        return true
    }

    fun resetDemoData() {
        timerJob?.cancel()
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
        syncWhiteNoise()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        soundSynthesizer.release()
    }
}
