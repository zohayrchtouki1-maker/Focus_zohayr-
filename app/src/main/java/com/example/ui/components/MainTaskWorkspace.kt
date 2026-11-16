package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CloudSyncStatus
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.formatDuration
import com.example.i18n.label
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SmartListType
import com.example.model.TagItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskSortOption
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState
import com.example.viewmodel.NavDestination

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainTaskWorkspace(
    uiState: MainUiState,
    s: Strings,
    isCompact: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenForest: () -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onSortChange: (TaskSortOption) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onAddTask: (
        title: String,
        estimatedPomodoros: Int,
        dueDateEpochDay: Long?,
        isSomeday: Boolean,
        isEvent: Boolean,
        priority: TaskPriority,
        projectId: String?,
        tagIds: List<String>,
        reminderTime: String?
    ) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onInspectTask: (String) -> Unit,
    onStartFocusForTask: (String) -> Unit,
    onOpenAuth: () -> Unit = {},
    onManualSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = uiState.data.settings.language == AppLanguage.AR
    val today = remember { DateUtils.todayEpochDay() }
    val (activeTasks, completedTasks) = remember(
        uiState.data.tasks,
        uiState.destination,
        uiState.sortOption
    ) {
        TaskFilterUtils.filterTasks(uiState.data.tasks, uiState.destination, uiState.sortOption)
    }

    val destinationTitle = when (val dest = uiState.destination) {
        is NavDestination.SmartList -> dest.type.label(s)
        is NavDestination.Project -> uiState.data.projects.find { it.id == dest.projectId }?.name ?: s.project
        is NavDestination.Tag -> "#${uiState.data.tags.find { it.id == dest.tagId }?.name ?: ""}"
    }

    // 4-Metric Dashboard values
    val estimatedMinutes = activeTasks.sumOf { task ->
        val rem = (task.estimatedPomodoros - task.completedPomodoros).coerceAtLeast(0)
        rem * (task.customPomodoroMinutes ?: uiState.data.settings.pomodoroMinutes)
    }
    val remainingCount = activeTasks.size
    val elapsedMinutes = (activeTasks + completedTasks).sumOf { it.elapsedFocusMinutes }
    val completedCount = completedTasks.size

    // Quick-Add Bar State
    var quickTitle by remember { mutableStateOf("") }
    var quickPomodoros by remember { mutableIntStateOf(1) }
    var quickPriority by remember { mutableStateOf(TaskPriority.NONE) }
    var quickDueEpochDay by remember(uiState.destination) {
        mutableStateOf(
            when (val dest = uiState.destination) {
                is NavDestination.SmartList -> when (dest.type) {
                    SmartListType.TODAY, SmartListType.EVENTS -> today
                    SmartListType.TOMORROW -> today + 1
                    SmartListType.UPCOMING -> today + 2
                    else -> null
                }
                else -> today
            }
        )
    }
    var quickIsSomeday by remember(uiState.destination) {
        mutableStateOf(uiState.destination == NavDestination.SmartList(SmartListType.SOMEDAY))
    }
    var quickProjectId by remember(uiState.destination) {
        mutableStateOf((uiState.destination as? NavDestination.Project)?.projectId)
    }
    var quickTagIds by remember(uiState.destination) {
        mutableStateOf(
            (uiState.destination as? NavDestination.Tag)?.let { listOf(it.tagId) } ?: emptyList()
        )
    }
    var quickReminderTime by remember { mutableStateOf<String?>(null) }

    var sortMenuOpen by remember { mutableStateOf(false) }
    var dateMenuOpen by remember { mutableStateOf(false) }
    var priorityMenuOpen by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }
    var tagMenuOpen by remember { mutableStateOf(false) }
    var showCustomDatePicker by remember { mutableStateOf(false) }

    fun submitQuickTask() {
        if (quickTitle.isBlank()) return
        onAddTask(
            quickTitle,
            quickPomodoros,
            if (quickIsSomeday) null else quickDueEpochDay,
            quickIsSomeday,
            uiState.destination == NavDestination.SmartList(SmartListType.EVENTS),
            quickPriority,
            quickProjectId,
            quickTagIds,
            if (quickIsSomeday) null else quickReminderTime
        )
        quickTitle = ""
        quickPomodoros = 1
        quickPriority = TaskPriority.NONE
        quickReminderTime = null
    }

    val topBarContrastColor = if (uiState.data.settings.darkTheme) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header Row (TopAppBar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompact) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("open_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = topBarContrastColor
                    )
                }
            }
            Text(
                text = destinationTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = topBarContrastColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isCompact) {
                IconButton(onClick = onOpenReports) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = s.reports,
                        tint = topBarContrastColor
                    )
                }
                IconButton(onClick = onOpenForest) {
                    Icon(
                        imageVector = Icons.Default.Park,
                        contentDescription = s.forest,
                        tint = topBarContrastColor
                    )
                }
            }

            // Language Toggle Chip ("EN" / "AR")
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, topBarContrastColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleLanguage)
                    .testTag("language_toggle_button")
            ) {
                Text(
                    text = if (isArabic) "EN" else "AR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = topBarContrastColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            IconButton(onClick = onToggleTheme, modifier = Modifier.testTag("theme_toggle_button")) {
                Icon(
                    imageVector = if (uiState.data.settings.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = s.darkMode,
                    tint = topBarContrastColor
                )
            }

            IconButton(onClick = onToggleShowCompleted, modifier = Modifier.testTag("toggle_completed_button")) {
                Icon(
                    imageVector = if (uiState.showCompletedAccordion) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (uiState.showCompletedAccordion) s.hideCompleted else s.showCompleted,
                    tint = topBarContrastColor
                )
            }

            Box {
                IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.testTag("sort_menu_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = s.sortBy,
                        tint = topBarContrastColor
                    )
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    TaskSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label(s)) },
                            trailingIcon = {
                                if (uiState.sortOption == option) {
                                    Icon(Icons.Default.Check, null, tint = CoralPrimary)
                                }
                            },
                            onClick = {
                                onSortChange(option)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // Top Bar Cloud Sync Status Bar + Manual "Sync Now" Button + Account Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusColor = when (uiState.cloudSyncStatus) {
                CloudSyncStatus.SYNCED -> ForestEmerald
                CloudSyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                CloudSyncStatus.SYNC_FAILED -> Color(0xFFEF4444)
                CloudSyncStatus.UNAUTHENTICATED -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val statusText = when (uiState.cloudSyncStatus) {
                CloudSyncStatus.SYNCED -> "Synced"
                CloudSyncStatus.SYNCING -> "Syncing..."
                CloudSyncStatus.SYNC_FAILED -> "Sync Failed"
                CloudSyncStatus.UNAUTHENTICATED -> if (isArabic) "سجل الدخول للمزامنة (Sign In)" else "Sign In to Sync"
            }

            // Cloud Sync Status Indicator Pill
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenAuth)
                    .testTag("top_bar_cloud_sync_status")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    when (uiState.cloudSyncStatus) {
                        CloudSyncStatus.SYNCED -> {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Synced",
                                tint = ForestEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        CloudSyncStatus.SYNCING -> {
                            CircularProgressIndicator(
                                color = statusColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        CloudSyncStatus.SYNC_FAILED -> {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Sync Failed",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        CloudSyncStatus.UNAUTHENTICATED -> {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Unauthenticated",
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (uiState.currentUserEmail != null) {
                        Text(
                            text = "• ${uiState.currentUserEmail}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Manual "Sync Now" Button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onManualSync)
                    .testTag("top_bar_sync_now_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Now",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isArabic) "مزامنة الآن (Sync Now)" else "Sync Now",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Account Profile Button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenAuth)
                    .testTag("open_auth_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isArabic) "حسابي" else "Account",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Exact Error Message Banner when Cloud Sync Fails
        if (uiState.cloudSyncStatus == CloudSyncStatus.SYNC_FAILED && !uiState.cloudSyncErrorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .testTag("top_bar_sync_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Sync Failed",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sync Failed: ${uiState.cloudSyncErrorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Scrollable Workspace Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 4-Metric Summary Dashboard Bar (Symmetrical 2x2 Grid on Compact / 4-Card Horizontal Grid on Expanded)
            item {
                if (isCompact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMetricCard(
                                label = s.estimatedTime,
                                value = formatDuration(estimatedMinutes, s),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCard(
                                label = s.tasksRemaining,
                                value = remainingCount.toString(),
                                accent = SunlightGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMetricCard(
                                label = s.elapsedFocusTime,
                                value = formatDuration(elapsedMinutes, s),
                                accent = PriorityLowColor,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricCard(
                                label = s.completedTasks,
                                value = completedCount.toString(),
                                accent = ForestEmerald,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            label = s.estimatedTime,
                            value = formatDuration(estimatedMinutes, s),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            label = s.tasksRemaining,
                            value = remainingCount.toString(),
                            accent = SunlightGold,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            label = s.elapsedFocusTime,
                            value = formatDuration(elapsedMinutes, s),
                            accent = PriorityLowColor,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            label = s.completedTasks,
                            value = completedCount.toString(),
                            accent = ForestEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick-Add Task Input Bar
            if (uiState.destination != NavDestination.SmartList(SmartListType.COMPLETED)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quickTitle,
                                    onValueChange = { quickTitle = it },
                                    placeholder = {
                                        Text(
                                            text = "${s.addTaskPlaceholder} $destinationTitle...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitQuickTask() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_add_input")
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (quickTitle.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .clickable(enabled = quickTitle.isNotBlank()) { submitQuickTask() }
                                        .testTag("quick_add_submit_button")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = s.add,
                                            tint = if (quickTitle.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Inline Quick Selectors (Tomatoes 1-5, Due Date, Priority, Project, Tags)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1-5 Tomato selector
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        for (i in 1..5) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable { quickPomodoros = i }
                                                    .padding(2.dp)
                                            ) {
                                                TomatoIcon(
                                                    filled = i <= quickPomodoros,
                                                    size = 16.dp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Due Date & Time Picker Chip
                                Box {
                                    val dateText = when {
                                        quickIsSomeday -> s.someday
                                        quickDueEpochDay != null -> {
                                            val d = DateUtils.formatEpochDay(quickDueEpochDay!!, isArabic)
                                            if (quickReminderTime != null) "$d • $quickReminderTime" else d
                                        }
                                        else -> s.dueDate
                                    }
                                    QuickSelectorPill(
                                        icon = Icons.Default.CalendarToday,
                                        label = dateText,
                                        tint = MaterialTheme.colorScheme.primary,
                                        onClick = { dateMenuOpen = true }
                                    )
                                    DropdownMenu(expanded = dateMenuOpen, onDismissRequest = { dateMenuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(s.today) },
                                            onClick = {
                                                quickIsSomeday = false
                                                quickDueEpochDay = today
                                                dateMenuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.tomorrow) },
                                            onClick = {
                                                quickIsSomeday = false
                                                quickDueEpochDay = today + 1
                                                dateMenuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.someday) },
                                            onClick = {
                                                quickIsSomeday = true
                                                quickDueEpochDay = null
                                                quickReminderTime = null
                                                dateMenuOpen = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(s.customDate) },
                                            onClick = {
                                                dateMenuOpen = false
                                                showCustomDatePicker = true
                                            }
                                        )
                                    }
                                }

                                // Priority Picker Chip
                                Box {
                                    QuickSelectorPill(
                                        icon = Icons.Default.Flag,
                                        label = quickPriority.label(s),
                                        tint = quickPriority.color(),
                                        onClick = { priorityMenuOpen = true }
                                    )
                                    DropdownMenu(expanded = priorityMenuOpen, onDismissRequest = { priorityMenuOpen = false }) {
                                        TaskPriority.entries.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.label(s)) },
                                                leadingIcon = { Icon(Icons.Default.Flag, null, tint = p.color()) },
                                                onClick = {
                                                    quickPriority = p
                                                    priorityMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Project Picker Chip
                                Box {
                                    val chosenProject = uiState.data.projects.find { it.id == quickProjectId }
                                    QuickSelectorPill(
                                        icon = Icons.Default.FolderOpen,
                                        label = chosenProject?.name ?: s.noProject,
                                        tint = chosenProject?.let { parseHexColor(it.colorHex) }
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        onClick = { projectMenuOpen = true }
                                    )
                                    DropdownMenu(expanded = projectMenuOpen, onDismissRequest = { projectMenuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(s.noProject) },
                                            onClick = {
                                                quickProjectId = null
                                                projectMenuOpen = false
                                            }
                                        )
                                        uiState.data.projects.filter { !it.isArchived }.forEach { proj ->
                                            DropdownMenuItem(
                                                text = { Text(proj.name) },
                                                onClick = {
                                                    quickProjectId = proj.id
                                                    projectMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Tag Picker Chip
                                Box {
                                    val tagLabel = if (quickTagIds.isEmpty()) {
                                        s.tags
                                    } else {
                                        uiState.data.tags
                                            .filter { quickTagIds.contains(it.id) }
                                            .joinToString(", ") { "#${it.name}" }
                                    }
                                    QuickSelectorPill(
                                        icon = Icons.Default.Tag,
                                        label = tagLabel,
                                        tint = PriorityLowColor,
                                        onClick = { tagMenuOpen = true }
                                    )
                                    DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                                        uiState.data.tags.forEach { tag ->
                                            val selected = quickTagIds.contains(tag.id)
                                            DropdownMenuItem(
                                                text = { Text("#${tag.name}") },
                                                trailingIcon = {
                                                    if (selected) Icon(Icons.Default.Check, null, tint = CoralPrimary)
                                                },
                                                onClick = {
                                                    quickTagIds = if (selected) {
                                                        quickTagIds - tag.id
                                                    } else {
                                                        quickTagIds + tag.id
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Empty State
            if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = s.emptyTasksTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = s.emptyTasksSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Active Task Rows
            items(activeTasks, key = { it.id }) { task ->
                TaskRowCard(
                    task = task,
                    project = uiState.data.projects.find { it.id == task.projectId },
                    tags = uiState.data.tags.filter { task.tagIds.contains(it.id) },
                    isSelectedForInspect = uiState.selectedTaskId == task.id,
                    isActiveFocusTask = uiState.timer.activeTaskId == task.id && uiState.timer.isRunning,
                    isArabic = isArabic,
                    s = s,
                    onToggleComplete = { onToggleTaskCompleted(task.id) },
                    onClickInspect = { onInspectTask(task.id) },
                    onClickPlay = { onStartFocusForTask(task.id) }
                )
            }

            // Completed Tasks Accordion
            if (completedTasks.isNotEmpty() && uiState.showCompletedAccordion) {
                item {
                    var expanded by remember { mutableStateOf(true) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { expanded = !expanded }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${s.completedTasks} (${completedTasks.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                completedTasks.forEach { task ->
                                    TaskRowCard(
                                        task = task,
                                        project = uiState.data.projects.find { it.id == task.projectId },
                                        tags = uiState.data.tags.filter { task.tagIds.contains(it.id) },
                                        isSelectedForInspect = uiState.selectedTaskId == task.id,
                                        isActiveFocusTask = false,
                                        isArabic = isArabic,
                                        s = s,
                                        onToggleComplete = { onToggleTaskCompleted(task.id) },
                                        onClickInspect = { onInspectTask(task.id) },
                                        onClickPlay = { onStartFocusForTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDatePicker) {
        UnifiedDateTimePickerDialog(
            initialEpochDay = quickDueEpochDay ?: today,
            initialReminderTime = quickReminderTime,
            isArabic = isArabic,
            onDismiss = { showCustomDatePicker = false },
            onConfirm = { pickedDay, pickedTime ->
                quickIsSomeday = false
                quickDueEpochDay = pickedDay
                quickReminderTime = pickedTime
                showCustomDatePicker = false
            }
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickSelectorPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRowCard(
    task: TaskItem,
    project: ProjectItem?,
    tags: List<TagItem>,
    isSelectedForInspect: Boolean,
    isActiveFocusTask: Boolean,
    isArabic: Boolean,
    s: Strings,
    onToggleComplete: () -> Unit,
    onClickInspect: () -> Unit,
    onClickPlay: () -> Unit
) {
    val priorityColor = task.priority.color()
    val borderColor by animateColorAsState(
        targetValue = when {
            isActiveFocusTask -> MaterialTheme.colorScheme.primary
            isSelectedForInspect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            else -> MaterialTheme.colorScheme.outline
        },
        label = "taskBorder"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (task.isCompleted) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (isSelectedForInspect || isActiveFocusTask) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isSelectedForInspect) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClickInspect)
            .testTag("task_item_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Smooth Circular Checkbox (48dp touch area)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleComplete)
                    .testTag("task_checkbox_${task.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (task.isCompleted) priorityColor.copy(alpha = 0.85f)
                            else Color.Transparent
                        )
                        .border(2.dp, priorityColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Main Task Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (task.priority != TaskPriority.NONE) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = task.priority.label(s),
                            tint = priorityColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Metadata & Tags Row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tomato progress row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val totalIcons = task.estimatedPomodoros.coerceAtMost(6)
                        for (i in 1..totalIcons) {
                            TomatoIcon(
                                filled = i <= task.completedPomodoros,
                                size = 13.dp
                            )
                        }
                        if (task.estimatedPomodoros > 6) {
                            Text(
                                text = "+${task.estimatedPomodoros - 6}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (task.elapsedFocusMinutes > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDuration(task.elapsedFocusMinutes, s),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Subtasks count
                    if (task.subtasks.isNotEmpty()) {
                        val done = task.subtasks.count { it.isDone }
                        Text(
                            text = "☑ $done/${task.subtasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Note indicator
                    if (task.notes.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = s.notes,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Recurring indicator
                    if (task.recurrence != RecurrenceRule.NONE) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = s.repeat,
                            tint = ForestEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Due Date & Exact Reminder Time (HH:mm) Badge
                    if (task.dueDateEpochDay != null || !task.reminderTime.isNullOrBlank()) {
                        val isOverdue = !task.isCompleted &&
                            task.dueDateEpochDay != null &&
                            task.dueDateEpochDay < DateUtils.todayEpochDay()
                        val datePart = task.dueDateEpochDay?.let { DateUtils.formatEpochDay(it, isArabic) }
                        val timePart = task.reminderTime?.takeIf { it.isNotBlank() }
                        val badgeLabel = when {
                            datePart != null && timePart != null -> "$datePart $timePart"
                            datePart != null -> datePart
                            else -> timePart ?: ""
                        }
                        val badgeColor = when {
                            isOverdue -> MaterialTheme.colorScheme.error
                            timePart != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.14f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = badgeColor,
                                    fontWeight = if (isOverdue || timePart != null) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Project Pill
                    if (project != null) {
                        val pColor = parseHexColor(project.colorHex)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = pColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Tag Badges
                    tags.forEach { tag ->
                        val tColor = parseHexColor(tag.colorHex)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Direct Play Button (Start Pomodoro for this task)
            if (!task.isCompleted) {
                Surface(
                    shape = CircleShape,
                    color = if (isActiveFocusTask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClickPlay)
                        .testTag("play_task_button_${task.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = s.start,
                            tint = if (isActiveFocusTask) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
