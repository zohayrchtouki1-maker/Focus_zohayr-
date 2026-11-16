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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.CatTomorrowOrange
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkIconMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkPillText
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PriorityLowColor
import com.example.viewmodel.MainUiState
import com.example.viewmodel.NavDestination

private fun formatHoursMinutesColon(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return "%02d:%02d".format(h, m)
}

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
    modifier: Modifier = Modifier
) {
    val isArabic = uiState.data.settings.language == AppLanguage.AR
    val isDark = uiState.data.settings.darkTheme
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
    var quickInputFocused by remember { mutableStateOf(false) }
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

    val primaryTextColor = if (isDark) DarkTextPrimary else MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = if (isDark) DarkTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = if (isDark) DarkSurface else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (isDark) DarkOutline else MaterialTheme.colorScheme.outline
    val pillSurfaceColor = if (isDark) DarkSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top bar: Right-aligned `>` back chevron + "المهام" (18sp Bold #F2F2F5); left-aligned `•••` menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompact) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("open_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = primaryTextColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = destinationTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("sort_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = s.sortBy,
                        tint = if (isDark) DarkIconMuted else primaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
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
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (uiState.showCompletedAccordion) s.hideCompleted else s.showCompleted) },
                        leadingIcon = {
                            Icon(
                                if (uiState.showCompletedAccordion) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onToggleShowCompleted()
                            sortMenuOpen = false
                        },
                        modifier = Modifier.testTag("toggle_completed_button")
                    )
                    DropdownMenuItem(
                        text = { Text(if (isDark) (if (isArabic) "الوضع الفاتح" else "Light Mode") else s.darkMode) },
                        leadingIcon = {
                            Icon(
                                if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onToggleTheme()
                            sortMenuOpen = false
                        },
                        modifier = Modifier.testTag("theme_toggle_button")
                    )
                    DropdownMenuItem(
                        text = { Text(if (isArabic) "English" else "العربية") },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        },
                        onClick = {
                            onToggleLanguage()
                            sortMenuOpen = false
                        },
                        modifier = Modifier.testTag("language_toggle_button")
                    )
                }
            }
        }

        // Scrollable Workspace Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Summary Card (#1C1C21, 12dp radius): 4-column horizontal metric bar (right to left)
            // الوقت المقدر (00:00), مهام غير مكتملة (0), الوقت المستغرق (00:00), المهام المكتملة (0)
            // with numbers in #E05246 20sp Bold
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SummaryMetricColumn(
                            value = formatHoursMinutesColon(estimatedMinutes),
                            label = s.estimatedTime,
                            secondaryColor = secondaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricColumn(
                            value = remainingCount.toString(),
                            label = s.tasksRemaining,
                            secondaryColor = secondaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricColumn(
                            value = formatHoursMinutesColon(elapsedMinutes),
                            label = s.elapsedFocusTime,
                            secondaryColor = secondaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricColumn(
                            value = completedCount.toString(),
                            label = s.completedTasks,
                            secondaryColor = secondaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Add Task Bar (#1C1C21, 12dp radius): Right-aligned `+` icon and "إضافة مهمة..." placeholder in #7E7E8A
            if (uiState.destination != NavDestination.SmartList(SmartListType.COMPLETED)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = s.add,
                                    tint = secondaryTextColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (quickTitle.isNotBlank()) submitQuickTask()
                                        }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (quickTitle.isEmpty()) {
                                        Text(
                                            text = s.addTaskPlaceholder,
                                            fontSize = 14.sp,
                                            color = secondaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = quickTitle,
                                        onValueChange = { quickTitle = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = primaryTextColor,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(CoralPrimary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { submitQuickTask() }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { quickInputFocused = it.isFocused }
                                            .testTag("quick_add_input")
                                    )
                                }

                                if (quickTitle.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CoralPrimary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { submitQuickTask() }
                                            .testTag("quick_add_submit_button")
                                    ) {
                                        Text(
                                            text = s.add,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = quickInputFocused || quickTitle.isNotBlank()) {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // 1-5 Tomato selector
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = pillSurfaceColor
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
                                                            size = 15.dp,
                                                            tint = CoralPrimary
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
                                                tint = CatTomorrowOrange,
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
                                                    ?: secondaryTextColor,
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
                    isDark = isDark,
                    s = s,
                    onToggleComplete = { onToggleTaskCompleted(task.id) },
                    onClickInspect = { onInspectTask(task.id) },
                    onClickPlay = { onStartFocusForTask(task.id) }
                )
            }

            // Empty State when no tasks at all
            if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = secondaryTextColor.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = s.emptyTasksTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor
                        )
                    }
                }
            }

            // Centered pill button below tasks: "عرض المهام المكتملة ▾" inside #25252B capsule (11sp #A0A0AB)
            if (completedTasks.isNotEmpty() || uiState.destination != NavDestination.SmartList(SmartListType.COMPLETED)) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = pillSurfaceColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onToggleShowCompleted() }
                                .testTag("completed_tasks_pill_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (uiState.showCompletedAccordion && completedTasks.isNotEmpty()) {
                                        s.hideCompleted
                                    } else {
                                        s.showCompleted
                                    },
                                    fontSize = 11.sp,
                                    color = if (isDark) DarkPillText else secondaryTextColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (uiState.showCompletedAccordion && completedTasks.isNotEmpty()) {
                                        Icons.Default.ArrowDropUp
                                    } else {
                                        Icons.Default.ArrowDropDown
                                    },
                                    contentDescription = null,
                                    tint = if (isDark) DarkPillText else secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Completed Tasks List when toggled visible
            if (completedTasks.isNotEmpty() && uiState.showCompletedAccordion) {
                items(completedTasks, key = { "done_${it.id}" }) { task ->
                    TaskRowCard(
                        task = task,
                        project = uiState.data.projects.find { it.id == task.projectId },
                        tags = uiState.data.tags.filter { task.tagIds.contains(it.id) },
                        isSelectedForInspect = uiState.selectedTaskId == task.id,
                        isActiveFocusTask = false,
                        isArabic = isArabic,
                        isDark = isDark,
                        s = s,
                        onToggleComplete = { onToggleTaskCompleted(task.id) },
                        onClickInspect = { onInspectTask(task.id) },
                        onClickPlay = { onStartFocusForTask(task.id) }
                    )
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
private fun SummaryMetricColumn(
    value: String,
    label: String,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = CoralPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
        color = tint.copy(alpha = 0.14f),
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
                fontSize = 11.sp,
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
    isDark: Boolean = true,
    s: Strings,
    onToggleComplete: () -> Unit,
    onClickInspect: () -> Unit,
    onClickPlay: () -> Unit
) {
    val cardBg = if (isDark) DarkSurface else MaterialTheme.colorScheme.surface
    val cardBorder by animateColorAsState(
        targetValue = when {
            isActiveFocusTask -> CoralPrimary
            isSelectedForInspect -> CoralPrimary.copy(alpha = 0.75f)
            isDark -> DarkOutline
            else -> MaterialTheme.colorScheme.outline
        },
        label = "taskBorder"
    )
    val checkboxOutlineColor = if (task.priority != TaskPriority.NONE) {
        task.priority.color()
    } else {
        if (isDark) DarkTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val primaryText = if (isDark) DarkTextPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryText = if (isDark) DarkTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    // Task Cards (#1C1C21, 12dp radius): Right circular checkbox (22dp outline #7E7E8A),
    // white title #F2F2F5, subtitle row with tiny tomato Pomodoro icons + orange due-date badge (#F39C12),
    // and far-left circular Coral Red play button (28dp, #E05246 with translucent outer ring and white ▶).
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (task.isCompleted) cardBg.copy(alpha = 0.68f) else cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClickInspect)
            .testTag("task_item_card_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right circular checkbox (22dp outline #7E7E8A, inside 40dp touch target)
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                            if (task.isCompleted) checkboxOutlineColor.copy(alpha = 0.85f)
                            else Color.Transparent
                        )
                        .border(1.6.dp, checkboxOutlineColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Main Task Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) secondaryText else primaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Subtitle row with tiny tomato Pomodoro icons + orange due-date badge (#F39C12)
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
                                size = 12.dp,
                                tint = CoralPrimary
                            )
                        }
                        if (task.estimatedPomodoros > 6) {
                            Text(
                                text = "+${task.estimatedPomodoros - 6}",
                                fontSize = 11.sp,
                                color = CoralPrimary
                            )
                        }
                        if (task.elapsedFocusMinutes > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = formatDuration(task.elapsedFocusMinutes, s),
                                fontSize = 11.sp,
                                color = secondaryText
                            )
                        }
                    }

                    // Due Date & Exact Reminder Time Badge in Orange (#F39C12)
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
                        val badgeColor = if (isOverdue) CoralPrimary else CatTomorrowOrange

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = badgeLabel,
                                fontSize = 11.sp,
                                color = badgeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Subtasks count
                    if (task.subtasks.isNotEmpty()) {
                        val done = task.subtasks.count { it.isDone }
                        Text(
                            text = "☑ $done/${task.subtasks.size}",
                            fontSize = 11.sp,
                            color = secondaryText
                        )
                    }

                    // Note indicator
                    if (task.notes.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = s.notes,
                            tint = secondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Recurring indicator
                    if (task.recurrence != RecurrenceRule.NONE) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = s.repeat,
                            tint = ForestEmerald,
                            modifier = Modifier.size(12.dp)
                        )
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
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Text(
                                text = project.name,
                                fontSize = 11.sp,
                                color = pColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Tag Badges
                    tags.forEach { tag ->
                        val tColor = parseHexColor(tag.colorHex)
                        Text(
                            text = "#${tag.name}",
                            fontSize = 11.sp,
                            color = tColor
                        )
                    }
                }
            }

            // Far-left circular Coral Red play button (28dp, #E05246 with translucent outer ring and white ▶)
            if (!task.isCompleted) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClickPlay)
                        .testTag("play_task_button_${task.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.22f))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(CoralPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = s.start,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
