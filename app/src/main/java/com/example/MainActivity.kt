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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.ui.components.AccountAuthDialog
import com.example.ui.components.BackgroundTimerPermissionsDialog
import com.example.ui.components.FloatingMiniTimerBar
import com.example.ui.components.ForestDialog
import com.example.ui.components.FullScreenTimerOverlay
import com.example.ui.components.MainTaskWorkspace
import com.example.ui.components.ReportsDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SidebarPanel
import com.example.ui.components.TaskInspectorPanel
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.FocusTodoTheme
import com.example.viewmodel.FocusTodoViewModel
import com.example.viewmodel.MainUiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: FocusTodoViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

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
                                onOpenAuth = { viewModel.setShowAuthScreen(true) },
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
                                modifier = Modifier.width(275.dp)
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
                                    onStartFocusForTask = { viewModel.startFocusForTask(it, openFullScreen = true) },
                                    onOpenAuth = { viewModel.setShowAuthScreen(true) },
                                    onManualSync = { viewModel.manualCloudSync() }
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
                    // Compact / Medium Phone Layout with Navigation Drawer + Slide-over Inspector
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                                SidebarPanel(
                                    uiState = uiState,
                                    s = s,
                                    onSelectDestination = {
                                        viewModel.selectDestination(it)
                                        scope.launch { drawerState.close() }
                                    },
                                    onOpenReports = {
                                        scope.launch { drawerState.close() }
                                        viewModel.setShowReports(true)
                                    },
                                    onOpenForest = {
                                        scope.launch { drawerState.close() }
                                        viewModel.setShowForest(true)
                                    },
                                    onOpenSettings = {
                                        scope.launch { drawerState.close() }
                                        viewModel.setShowSettings(true)
                                    },
                                    onOpenAuth = {
                                        scope.launch { drawerState.close() }
                                        viewModel.setShowAuthScreen(true)
                                    },
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
                                    onDeleteTag = { viewModel.deleteTag(it) }
                                )
                            }
                        }
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                MainTaskWorkspace(
                                    uiState = uiState,
                                    s = s,
                                    isCompact = true,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
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
                                    onStartFocusForTask = { viewModel.startFocusForTask(it, openFullScreen = true) },
                                    onOpenAuth = { viewModel.setShowAuthScreen(true) },
                                    onManualSync = { viewModel.manualCloudSync() }
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
                        }
                    }

                    // Slide-Over Right Task Inspector for Compact Screens
                    AnimatedVisibility(
                        visible = selectedTask != null,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                    ) {
                        selectedTask?.let { task ->
                            BackHandler { viewModel.openTaskInspector(null) }
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surface
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
                        onSetWhiteNoiseVolume = { viewModel.setWhiteNoiseVolume(it) }
                    )
                }

                // Reports & Analytics Modal
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

                // Settings Modal
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
                        onImportJson = { viewModel.importBackupJson(it) },
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

                // Account Sign In / Create Account / Cloud Sync Screen
                AnimatedVisibility(
                    visible = uiState.showAuthScreen,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 })
                ) {
                    AccountAuthDialog(
                        currentUserEmail = uiState.currentUserEmail,
                        currentUserId = uiState.currentUserId,
                        cloudSyncStatus = uiState.cloudSyncStatus,
                        cloudSyncErrorMessage = uiState.cloudSyncErrorMessage,
                        isAuthLoading = uiState.isAuthLoading,
                        authErrorMessage = uiState.authErrorMessage,
                        firebaseProjectId = uiState.firebaseProjectId,
                        language = settings.language,
                        onDismiss = { viewModel.setShowAuthScreen(false) },
                        onSignIn = { email, password -> viewModel.signIn(email, password) },
                        onSignUp = { email, password -> viewModel.signUp(email, password) },
                        onSignOut = { viewModel.signOut() },
                        onManualSync = { viewModel.manualCloudSync() },
                        onClearError = { viewModel.clearAuthError() }
                    )
                }
            }
        }
    }
}
