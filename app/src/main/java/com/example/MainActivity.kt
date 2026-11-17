package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blocker.StrictAppBlockerManager
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.TaskPriority
import com.example.notifications.FocusNotificationManager
import com.example.ui.components.BackgroundTimerPermissionsDialog
import com.example.ui.components.CalendarDialog
import com.example.ui.components.FloatingMiniTimerBar
import com.example.ui.components.ForestDialog
import com.example.ui.components.FullScreenTimerOverlay
import com.example.ui.components.MainTaskWorkspace
import com.example.ui.components.ReportsDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SidebarPanel
import com.example.ui.components.StrictBlockerSetupDialog
import com.example.ui.components.TaskInspectorPanel
import com.example.ui.components.TomatoAiDialog
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.FocusTodoTheme
import com.example.viewmodel.FocusTodoViewModel
import com.example.viewmodel.MainUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: FocusTodoViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (intent?.getBooleanExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED, false) == true) {
                    viewModel.notifyStrictBlockTriggered()
                    intent?.removeExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED)
                }
                if (intent?.getBooleanExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER, false) == true) {
                    viewModel.setShowFullScreenTimer(true)
                    intent?.removeExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER)
                }
            }

            DisposableEffect(Unit) {
                val listener = androidx.core.util.Consumer<android.content.Intent> { newIntent ->
                    if (newIntent.getBooleanExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED, false)) {
                        viewModel.notifyStrictBlockTriggered()
                        newIntent.removeExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED)
                    }
                    if (newIntent.getBooleanExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER, false)) {
                        viewModel.setShowFullScreenTimer(true)
                        newIntent.removeExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER)
                    }
                }
                addOnNewIntentListener(listener)
                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            FocusTodoAppRoot(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun FocusTodoAppRoot(
    uiState: MainUiState,
    viewModel: FocusTodoViewModel
) {
    val settings = uiState.data.settings
    val isArabic = settings.language == AppLanguage.AR
    val s = AppStrings.get(settings.language)
    val layoutDir = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshSystemPermissions()
    }

    LaunchedEffect(uiState.permissionRequestTrigger) {
        if (uiState.permissionRequestTrigger > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!uiState.systemPermissions.notificationsGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        FocusTodoTheme(darkTheme = settings.darkTheme) {
            if (!uiState.isLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CoralPrimary)
                }
                return@FocusTodoTheme
            }

            val selectedTask = uiState.data.tasks.find { it.id == uiState.selectedTaskId }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpanded = maxWidth >= 900.dp

                if (isExpanded) {
                    // 3-Pane Desktop / Tablet Layout
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            SidebarPanel(
                                uiState = uiState,
                                s = s,
                                onSelectDestination = { viewModel.selectDestination(it) },
                                onOpenReports = { viewModel.setShowReports(true) },
                                onOpenForest = { viewModel.setShowForest(true) },
                                onOpenSettings = { viewModel.setShowSettings(true) },
                                onAddFolder = { viewModel.addFolder(it) },
                                onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                                onDeleteFolder = { viewModel.deleteFolder(it) },
                                onToggleFolderExpanded = { viewModel.toggleFolderExpanded(it) },
                                onSaveProject = { id, name, color, folderId ->
                                    viewModel.saveProject(id, name, color, folderId)
                                },
                                onToggleArchiveProject = { viewModel.toggleArchiveProject(it) },
                                onDeleteProject = { viewModel.deleteProject(it) },
                                onAddTag = { name, color -> viewModel.addTag(name, color) },
                                onDeleteTag = { viewModel.deleteTag(it) },
                                onOpenCalendar = { viewModel.setShowCalendar(true) },
                                onOpenTomatoAi = { viewModel.setShowTomatoAi(true) },
                                modifier = Modifier.width(290.dp)
                            )

                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                MainTaskWorkspace(
                                    uiState = uiState,
                                    s = s,
                                    isCompact = false,
                                    onOpenDrawer = {},
                                    onOpenReports = { viewModel.setShowReports(true) },
                                    onOpenForest = { viewModel.setShowForest(true) },
                                    onToggleLanguage = { viewModel.toggleLanguage() },
                                    onToggleTheme = { viewModel.toggleDarkTheme() },
                                    onSortChange = { viewModel.setSortOption(it) },
                                    onToggleShowCompleted = { viewModel.toggleShowCompletedAccordion() },
                                    onAddTask = { title, poms, due, someday, event, prio, projId, tags, reminderTime ->
                                        viewModel.addTask(title, poms, due, someday, event, prio, projId, tags, reminderTime)
                                    },
                                    onToggleTaskCompleted = { viewModel.toggleTaskCompleted(it) },
                                    onInspectTask = { viewModel.openTaskInspector(it) },
                                    onStartFocusForTask = { viewModel.startFocusForTask(it, openFullScreen = true) }
                                )

                                FloatingMiniTimerBar(
                                    uiState = uiState,
                                    s = s,
                                    onExpand = { viewModel.setShowFullScreenTimer(true) },
                                    onStartOrResume = { viewModel.startOrResumeTimer() },
                                    onPause = { viewModel.pauseTimer() },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            if (selectedTask != null) {
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                TaskInspectorPanel(
                                    task = selectedTask,
                                    projects = uiState.data.projects,
                                    tags = uiState.data.tags,
                                    defaultPomodoroMinutes = settings.pomodoroMinutes,
                                    language = settings.language,
                                    s = s,
                                    onClose = { viewModel.openTaskInspector(null) },
                                    onToggleComplete = { viewModel.toggleTaskCompleted(selectedTask.id) },
                                    onUpdateTask = { transform -> viewModel.updateTask(selectedTask.id, transform) },
                                    onAddSubtask = { viewModel.addSubtask(selectedTask.id, it) },
                                    onToggleSubtask = { viewModel.toggleSubtask(selectedTask.id, it) },
                                    onDeleteSubtask = { viewModel.deleteSubtask(selectedTask.id, it) },
                                    onStartFocus = { viewModel.startFocusForTask(selectedTask.id, openFullScreen = true) },
                                    onDeleteTask = { viewModel.deleteTask(selectedTask.id) },
                                    modifier = Modifier.width(340.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Compact / Medium Phone Layout:
                    // Main Home Screen (الرئيسية) <-> Task List Screen (المهام) + Slide-over Task Detail (تفاصيل المهمة)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (uiState.showHomeScreenOnCompact) {
                                SidebarPanel(
                                    uiState = uiState,
                                    s = s,
                                    onSelectDestination = { dest ->
                                        viewModel.selectDestination(dest)
                                    },
                                    onOpenReports = { viewModel.setShowReports(true) },
                                    onOpenForest = { viewModel.setShowForest(true) },
                                    onOpenSettings = { viewModel.setShowSettings(true) },
                                    onAddFolder = { viewModel.addFolder(it) },
                                    onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                                    onDeleteFolder = { viewModel.deleteFolder(it) },
                                    onToggleFolderExpanded = { viewModel.toggleFolderExpanded(it) },
                                    onSaveProject = { id, name, color, folderId ->
                                        viewModel.saveProject(id, name, color, folderId)
                                    },
                                    onToggleArchiveProject = { viewModel.toggleArchiveProject(it) },
                                    onDeleteProject = { viewModel.deleteProject(it) },
                                    onAddTag = { name, color -> viewModel.addTag(name, color) },
                                    onDeleteTag = { viewModel.deleteTag(it) },
                                    onOpenCalendar = { viewModel.setShowCalendar(true) },
                                    onOpenTomatoAi = { viewModel.setShowTomatoAi(true) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                BackHandler {
                                    viewModel.setShowHomeScreenOnCompact(true)
                                }
                                MainTaskWorkspace(
                                    uiState = uiState,
                                    s = s,
                                    isCompact = true,
                                    onOpenDrawer = { viewModel.setShowHomeScreenOnCompact(true) },
                                    onOpenReports = { viewModel.setShowReports(true) },
                                    onOpenForest = { viewModel.setShowForest(true) },
                                    onToggleLanguage = { viewModel.toggleLanguage() },
                                    onToggleTheme = { viewModel.toggleDarkTheme() },
                                    onSortChange = { viewModel.setSortOption(it) },
                                    onToggleShowCompleted = { viewModel.toggleShowCompletedAccordion() },
                                    onAddTask = { title, poms, due, someday, event, prio, projId, tags, reminderTime ->
                                        viewModel.addTask(title, poms, due, someday, event, prio, projId, tags, reminderTime)
                                    },
                                    onToggleTaskCompleted = { viewModel.toggleTaskCompleted(it) },
                                    onInspectTask = { viewModel.openTaskInspector(it) },
                                    onStartFocusForTask = { viewModel.startFocusForTask(it, openFullScreen = true) }
                                )
                            }

                            FloatingMiniTimerBar(
                                uiState = uiState,
                                s = s,
                                onExpand = { viewModel.setShowFullScreenTimer(true) },
                                onStartOrResume = { viewModel.startOrResumeTimer() },
                                onPause = { viewModel.pauseTimer() },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    // Slide-Over Task Detail Screen (تفاصيل المهمة) for Compact Screens
                    AnimatedVisibility(
                        visible = selectedTask != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        selectedTask?.let { task ->
                            BackHandler { viewModel.openTaskInspector(null) }
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                TaskInspectorPanel(
                                    task = task,
                                    projects = uiState.data.projects,
                                    tags = uiState.data.tags,
                                    defaultPomodoroMinutes = settings.pomodoroMinutes,
                                    language = settings.language,
                                    s = s,
                                    onClose = { viewModel.openTaskInspector(null) },
                                    onToggleComplete = { viewModel.toggleTaskCompleted(task.id) },
                                    onUpdateTask = { transform -> viewModel.updateTask(task.id, transform) },
                                    onAddSubtask = { viewModel.addSubtask(task.id, it) },
                                    onToggleSubtask = { viewModel.toggleSubtask(task.id, it) },
                                    onDeleteSubtask = { viewModel.deleteSubtask(task.id, it) },
                                    onStartFocus = { viewModel.startFocusForTask(task.id, openFullScreen = true) },
                                    onDeleteTask = { viewModel.deleteTask(task.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Calendar Screen (التقويم)
                if (uiState.showCalendar) {
                    CalendarDialog(
                        uiState = uiState,
                        s = s,
                        onDismiss = { viewModel.setShowCalendar(false) },
                        onAddTaskOnDate = { title, epochDay, reminderTime ->
                            viewModel.addTask(
                                title = title,
                                estimatedPomodoros = 1,
                                dueDateEpochDay = epochDay,
                                isSomeday = false,
                                isEvent = true,
                                priority = TaskPriority.NONE,
                                projectId = null,
                                tagIds = emptyList(),
                                reminderTime = reminderTime
                            )
                        },
                        onInspectTask = { taskId -> viewModel.openTaskInspector(taskId) },
                        onToggleTaskCompleted = { taskId -> viewModel.toggleTaskCompleted(taskId) }
                    )
                }

                // Full-Screen Pomodoro Timer Overlay
                AnimatedVisibility(
                    visible = uiState.showFullScreenTimer,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    FullScreenTimerOverlay(
                        uiState = uiState,
                        s = s,
                        onCollapse = { viewModel.setShowFullScreenTimer(false) },
                        onStartOrResume = { viewModel.startOrResumeTimer() },
                        onPause = { viewModel.pauseTimer() },
                        onStop = { viewModel.stopOrAbandonTimer() },
                        onSkipBreak = { viewModel.skipBreak() },
                        onSetCountMode = { viewModel.setTimerCountMode(it) },
                        onToggleStrictMode = { viewModel.toggleStrictMode() },
                        onSelectTask = { viewModel.selectActiveFocusTask(it) },
                        onCompleteActiveTask = { viewModel.toggleTaskCompleted(it) },
                        onSetWhiteNoise = { viewModel.setWhiteNoise(it) },
                        onSetWhiteNoiseVolume = { viewModel.setWhiteNoiseVolume(it) },
                        onOpenStrictBlockerSetup = { viewModel.setShowStrictBlockerSetup(true) },
                        onDismissStatusBanner = { viewModel.clearStatusBanner() }
                    )
                }

                // Strict App Blocker Setup & Whitelist Screen (🛡️ الحظر الصارم)
                AnimatedVisibility(
                    visible = uiState.showStrictBlockerSetup,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    StrictBlockerSetupDialog(
                        uiState = uiState,
                        onDismiss = { viewModel.setShowStrictBlockerSetup(false) },
                        onSetStrictBlockerEnabled = { viewModel.setStrictBlockerEnabled(it) },
                        onToggleWhitelistedApp = { viewModel.toggleWhitelistedApp(it) },
                        onRefreshBlockerPermissions = { viewModel.refreshStrictBlockerPermissions() }
                    )
                }

                // Statistics Screen (الإحصائيات)
                AnimatedVisibility(
                    visible = uiState.showReports,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    ReportsDialog(
                        uiState = uiState,
                        s = s,
                        onDismiss = { viewModel.setShowReports(false) },
                        onAddManualRecord = { taskId, projectId, day, hr, min, dur ->
                            viewModel.addManualFocusRecord(taskId, projectId, day, hr, min, dur)
                        }
                    )
                }

                // Forest Gamification Modal
                AnimatedVisibility(
                    visible = uiState.showForest,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    ForestDialog(
                        uiState = uiState,
                        s = s,
                        onDismiss = { viewModel.setShowForest(false) },
                        onUnlockOrSelectSpecies = { viewModel.unlockOrSelectSpecies(it) }
                    )
                }

                // Settings Screen (الإعدادات)
                AnimatedVisibility(
                    visible = uiState.showSettings,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    SettingsDialog(
                        uiState = uiState,
                        s = s,
                        onDismiss = { viewModel.setShowSettings(false) },
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onRequestPermissions = { viewModel.requestNativePermissionsIfMissing() },
                        onOpenBackgroundPermissionsModal = { viewModel.setShowBackgroundPermissionsModal(true) },
                        onExportJson = { viewModel.exportBackupJson() },
                        onExportManualBackupToDocuments = { onResult ->
                            viewModel.exportManualBackupToDocuments(onResult)
                        },
                        onExportManualBackupWithSummary = { onResult ->
                            viewModel.exportManualBackupWithSummary(onResult)
                        },
                        onCreateShareableBackupFile = { viewModel.createShareableBackupFile() },
                        onParseBackupPreview = { viewModel.parseBackupPreview(it) },
                        onImportBackupState = { parsed, mode -> viewModel.importBackupState(parsed, mode) },
                        onResetDemoData = { viewModel.resetDemoData() }
                    )
                }

                // Background Timer Permissions Modal (أذونات المؤقت في الخلفية)
                AnimatedVisibility(
                    visible = uiState.showBackgroundPermissionsModal,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    BackgroundTimerPermissionsDialog(
                        uiState = uiState,
                        s = s,
                        onDismiss = { viewModel.setShowBackgroundPermissionsModal(false) },
                        onToggleLockedInRecents = { confirmed ->
                            viewModel.updateSettings { it.copy(lockedInRecentsConfirmed = confirmed) }
                        }
                    )
                }

                // Tomato AI Chat Assistant Screen (Tomato AI 🍅)
                AnimatedVisibility(
                    visible = uiState.showTomatoAi,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    TomatoAiDialog(
                        apiKey = uiState.tomatoAiApiKey,
                        selectedModel = uiState.tomatoAiSelectedModel,
                        messages = uiState.tomatoAiMessages,
                        isLoading = uiState.tomatoAiIsLoading,
                        language = settings.language,
                        onSaveApiKey = { viewModel.saveTomatoAiApiKey(it) },
                        onSelectModel = { viewModel.selectTomatoAiModel(it) },
                        onSendMessage = { viewModel.sendTomatoAiMessage(it) },
                        onClearChat = { viewModel.clearTomatoAiChat() },
                        onDismiss = { viewModel.setShowTomatoAi(false) }
                    )
                }
            }
        }
    }
}
