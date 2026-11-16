package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.label
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.TagItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.ui.theme.CatTasksInboxBlue
import com.example.ui.theme.CatTomorrowOrange
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkIconMuted
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskInspectorPanel(
    task: TaskItem,
    projects: List<ProjectItem>,
    tags: List<TagItem>,
    defaultPomodoroMinutes: Int,
    language: AppLanguage,
    s: Strings,
    onClose: () -> Unit,
    onToggleComplete: () -> Unit,
    onUpdateTask: ((TaskItem) -> TaskItem) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (String) -> Unit,
    onDeleteSubtask: (String) -> Unit,
    onStartFocus: () -> Unit,
    onDeleteTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.AR
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPomodoroDialog by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }
    var priorityMenuOpen by remember { mutableStateOf(false) }
    var tagsMenuOpen by remember { mutableStateOf(false) }
    var repeatMenuOpen by remember { mutableStateOf(false) }
    var reminderMenuOpen by remember { mutableStateOf(false) }
    var expandPomodoroEditor by remember { mutableStateOf(false) }

    val primaryTextColor = DarkTextPrimary
    val secondaryTextColor = DarkTextSecondary
    val cardBgColor = DarkSurface
    val cardBorderColor = DarkOutline
    val iconMutedColor = DarkIconMuted

    val currentProject = projects.find { it.id == task.projectId }
    val currentProjectLabel = currentProject?.name ?: s.noProject
    val currentProjectColor = currentProject?.let { parseHexColor(it.colorHex) } ?: CatTasksInboxBlue

    val unitPomodoroMins = task.customPomodoroMinutes ?: defaultPomodoroMinutes
    val totalEstimatedMins = task.estimatedPomodoros * unitPomodoroMins

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar: Right-aligned `>` back chevron + project dropdown "المهام ▾";
        // left-aligned Play `▶`, Timer-plus, and Trash icons (20dp, #9E9EA9)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("inspector_close_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = s.cancel,
                    tint = primaryTextColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Project dropdown "المهام ▾"
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { projectMenuOpen = true }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(currentProjectColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentProjectLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = projectMenuOpen,
                    onDismissRequest = { projectMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(s.noProject) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CatTasksInboxBlue)
                            )
                        },
                        onClick = {
                            onUpdateTask { it.copy(projectId = null) }
                            projectMenuOpen = false
                        }
                    )
                    projects.filter { !it.isArchived }.forEach { proj ->
                        DropdownMenuItem(
                            text = { Text(proj.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(proj.colorHex))
                                )
                            },
                            onClick = {
                                onUpdateTask { it.copy(projectId = proj.id) }
                                projectMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Left-aligned Play ▶, Timer-plus, and Trash icons (20dp, #9E9EA9)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!task.isCompleted) {
                    IconButton(
                        onClick = onStartFocus,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("inspector_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = s.start,
                            tint = iconMutedColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showPomodoroDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreTime,
                        contentDescription = s.pomodoroProgress,
                        tint = iconMutedColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteTask,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("inspector_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = s.deleteTask,
                        tint = iconMutedColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1 (#1C1C21, 12dp radius): Task title next to completion circle,
            // priority flag icon on left, dashed-border pill button "+ الوسوم" below title
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val checkboxColor = if (task.priority != TaskPriority.NONE) {
                            task.priority.color()
                        } else {
                            secondaryTextColor
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (task.isCompleted) checkboxColor.copy(alpha = 0.85f) else Color.Transparent
                                )
                                .border(1.6.dp, checkboxColor, CircleShape)
                                .clickable(onClick = onToggleComplete),
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

                        Spacer(modifier = Modifier.width(12.dp))

                        BasicTextField(
                            value = task.title,
                            onValueChange = { newTitle -> onUpdateTask { it.copy(title = newTitle) } },
                            textStyle = TextStyle(
                                color = if (task.isCompleted) secondaryTextColor else primaryTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            cursorBrush = SolidColor(CoralPrimary),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Priority flag icon on left (end in RTL)
                        Box {
                            IconButton(
                                onClick = { priorityMenuOpen = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (task.priority == TaskPriority.NONE) Icons.Outlined.Flag else Icons.Default.Flag,
                                    contentDescription = s.priority,
                                    tint = if (task.priority == TaskPriority.NONE) secondaryTextColor else task.priority.color(),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = priorityMenuOpen,
                                onDismissRequest = { priorityMenuOpen = false }
                            ) {
                                TaskPriority.entries.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.label(s)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Flag, null, tint = p.color())
                                        },
                                        onClick = {
                                            onUpdateTask { it.copy(priority = p) }
                                            priorityMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dashed-border pill button "+ الوسوم" below title + assigned tags
                    Box {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 34.dp)
                        ) {
                            val assignedTags = tags.filter { task.tagIds.contains(it.id) }
                            assignedTags.forEach { tag ->
                                val tColor = parseHexColor(tag.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = tColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            onUpdateTask { it.copy(tagIds = it.tagIds - tag.id) }
                                        }
                                ) {
                                    Text(
                                        text = "#${tag.name} ✕",
                                        fontSize = 11.sp,
                                        color = tColor,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Dashed-border pill button "+ الوسوم"
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { tagsMenuOpen = true }
                            ) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    drawRoundRect(
                                        color = secondaryTextColor.copy(alpha = 0.55f),
                                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                        style = Stroke(
                                            width = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                        )
                                    )
                                }
                                Text(
                                    text = if (isArabic) "+ الوسوم" else "+ ${s.tags}",
                                    fontSize = 11.sp,
                                    color = secondaryTextColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = tagsMenuOpen,
                            onDismissRequest = { tagsMenuOpen = false }
                        ) {
                            tags.forEach { tag ->
                                val selected = task.tagIds.contains(tag.id)
                                DropdownMenuItem(
                                    text = { Text("#${tag.name}") },
                                    trailingIcon = {
                                        if (selected) Icon(Icons.Default.Check, null, tint = CoralPrimary)
                                    },
                                    onClick = {
                                        onUpdateTask {
                                            val next = if (selected) it.tagIds - tag.id else it.tagIds + tag.id
                                            it.copy(tagIds = next)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Card 2 (#1C1C21, 12dp radius): Structured rows for
            // "بومودورو" (tomato icons + "= 60 د"), "تاريخ الاستحقاق" ("غدًا ⊗" pill badge),
            // "التذكير" ("لا يوجد"), "التكرار" ("لا يوجد")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: بومودورو
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandPomodoroEditor = !expandPomodoroEditor }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TomatoIcon(filled = false, size = 18.dp, tint = secondaryTextColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = s.pomodoroProgress,
                                fontSize = 15.sp,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )

                            // Tomato icons + "= 60 د" on left
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "${task.completedPomodoros}/",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                                val countIcons = task.estimatedPomodoros.coerceIn(1, 5)
                                for (i in 1..countIcons) {
                                    TomatoIcon(
                                        filled = i <= task.completedPomodoros || task.completedPomodoros == 0,
                                        size = 13.dp,
                                        tint = CoralPrimary
                                    )
                                }
                                if (task.estimatedPomodoros > 5) {
                                    Text(
                                        text = "+${task.estimatedPomodoros - 5}",
                                        fontSize = 11.sp,
                                        color = CoralPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "= $totalEstimatedMins ${s.minutesShort}",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        AnimatedVisibility(visible = expandPomodoroEditor) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StepperControl(
                                        label = s.tomatoesLabel,
                                        value = task.estimatedPomodoros,
                                        onDecrement = {
                                            onUpdateTask { it.copy(estimatedPomodoros = (it.estimatedPomodoros - 1).coerceAtLeast(1)) }
                                        },
                                        onIncrement = {
                                            onUpdateTask { it.copy(estimatedPomodoros = (it.estimatedPomodoros + 1).coerceAtMost(20)) }
                                        }
                                    )
                                    StepperControl(
                                        label = s.completed,
                                        value = task.completedPomodoros,
                                        onDecrement = {
                                            onUpdateTask { it.copy(completedPomodoros = (it.completedPomodoros - 1).coerceAtLeast(0)) }
                                        },
                                        onIncrement = {
                                            onUpdateTask { it.copy(completedPomodoros = it.completedPomodoros + 1) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = cardBorderColor, thickness = 0.8.dp)

                    // Row 2: تاريخ الاستحقاق
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = s.dueDate,
                            fontSize = 15.sp,
                            color = primaryTextColor,
                            modifier = Modifier.weight(1f)
                        )

                        if (task.dueDateEpochDay != null || task.isSomeday) {
                            val dateStr = if (task.isSomeday) {
                                s.someday
                            } else {
                                DateUtils.formatEpochDay(task.dueDateEpochDay!!, isArabic)
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = CatTomorrowOrange
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = dateStr,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = s.clearDate,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable {
                                                onUpdateTask {
                                                    it.copy(
                                                        dueDateEpochDay = null,
                                                        reminderTime = null,
                                                        isSomeday = false
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = s.repeatNone,
                                fontSize = 13.sp,
                                color = secondaryTextColor
                            )
                        }
                    }

                    HorizontalDivider(color = cardBorderColor, thickness = 0.8.dp)

                    // Row 3: التذكير
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reminderMenuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = s.reminder,
                                fontSize = 15.sp,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = task.reminderTime ?: s.repeatNone,
                                fontSize = 13.sp,
                                color = if (task.reminderTime != null) CoralPrimary else secondaryTextColor
                            )
                        }

                        DropdownMenu(
                            expanded = reminderMenuOpen,
                            onDismissRequest = { reminderMenuOpen = false }
                        ) {
                            val today = DateUtils.todayEpochDay()
                            DropdownMenuItem(
                                text = { Text(if (isArabic) "بعد 15 دقيقة" else "In 15 minutes") },
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 15) }
                                    val d = DateUtils.epochDayFromMillis(cal.timeInMillis)
                                    val t = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                                    onUpdateTask { it.copy(dueDateEpochDay = d, reminderTime = t, isSomeday = false) }
                                    reminderMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArabic) "غدًا 09:00" else "Tomorrow 09:00") },
                                onClick = {
                                    onUpdateTask { it.copy(dueDateEpochDay = today + 1, reminderTime = "09:00", isSomeday = false) }
                                    reminderMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArabic) "تحديد وقت مخصص..." else "Pick custom time...") },
                                onClick = {
                                    reminderMenuOpen = false
                                    showDatePicker = true
                                }
                            )
                            if (task.reminderTime != null) {
                                DropdownMenuItem(
                                    text = { Text(s.repeatNone) },
                                    onClick = {
                                        onUpdateTask { it.copy(reminderTime = null) }
                                        reminderMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = cardBorderColor, thickness = 0.8.dp)

                    // Row 4: التكرار
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repeatMenuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Repeat,
                                contentDescription = null,
                                tint = secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = s.repeat,
                                fontSize = 15.sp,
                                color = primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = task.recurrence.label(s),
                                fontSize = 13.sp,
                                color = if (task.recurrence != RecurrenceRule.NONE) ForestEmerald else secondaryTextColor
                            )
                        }

                        DropdownMenu(
                            expanded = repeatMenuOpen,
                            onDismissRequest = { repeatMenuOpen = false }
                        ) {
                            RecurrenceRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.label(s)) },
                                    onClick = {
                                        onUpdateTask { it.copy(recurrence = rule) }
                                        repeatMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Card 3 (#1C1C21, 12dp radius): "+ إضافة مهمة فرعية..." row + existing subtasks
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    task.subtasks.forEach { sub ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSubtask(sub.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (sub.isDone) ForestEmerald else Color.Transparent)
                                    .border(
                                        1.5.dp,
                                        if (sub.isDone) ForestEmerald else secondaryTextColor,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sub.isDone) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = sub.title,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                color = if (sub.isDone) secondaryTextColor else primaryTextColor,
                                textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                            IconButton(
                                onClick = { onDeleteSubtask(sub.id) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = cardBorderColor, thickness = 0.8.dp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = s.add,
                            tint = secondaryTextColor,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        onAddSubtask(newSubtaskTitle)
                                        newSubtaskTitle = ""
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (newSubtaskTitle.isEmpty()) {
                                Text(
                                    text = if (isArabic) "إضافة مهمة فرعية..." else "Add a subtask...",
                                    fontSize = 14.sp,
                                    color = secondaryTextColor
                                )
                            }
                            BasicTextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = primaryTextColor,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(CoralPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        onAddSubtask(newSubtaskTitle)
                                        newSubtaskTitle = ""
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Card 4 (#1C1C21, 12dp radius): Multiline "إضافة ملاحظة..." card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    if (task.notes.isEmpty()) {
                        Text(
                            text = s.notesPlaceholder,
                            fontSize = 14.sp,
                            color = secondaryTextColor
                        )
                    }
                    BasicTextField(
                        value = task.notes,
                        onValueChange = { notes -> onUpdateTask { it.copy(notes = notes) } },
                        textStyle = TextStyle(
                            color = primaryTextColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(CoralPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        UnifiedDateTimePickerDialog(
            initialEpochDay = task.dueDateEpochDay ?: DateUtils.todayEpochDay(),
            initialReminderTime = task.reminderTime,
            isArabic = isArabic,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedDay, pickedTime ->
                onUpdateTask {
                    it.copy(
                        dueDateEpochDay = pickedDay,
                        reminderTime = pickedTime,
                        isSomeday = false
                    )
                }
                showDatePicker = false
            }
        )
    }

    if (showPomodoroDialog) {
        val currentDur = task.customPomodoroMinutes ?: defaultPomodoroMinutes
        AlertDialog(
            onDismissRequest = { showPomodoroDialog = false },
            title = { Text(s.pomodoroProgress) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepperControl(
                        label = s.tomatoesLabel,
                        value = task.estimatedPomodoros,
                        onDecrement = {
                            onUpdateTask { it.copy(estimatedPomodoros = (it.estimatedPomodoros - 1).coerceAtLeast(1)) }
                        },
                        onIncrement = {
                            onUpdateTask { it.copy(estimatedPomodoros = (it.estimatedPomodoros + 1).coerceAtMost(20)) }
                        }
                    )
                    StepperControl(
                        label = s.completed,
                        value = task.completedPomodoros,
                        onDecrement = {
                            onUpdateTask { it.copy(completedPomodoros = (it.completedPomodoros - 1).coerceAtLeast(0)) }
                        },
                        onIncrement = {
                            onUpdateTask { it.copy(completedPomodoros = it.completedPomodoros + 1) }
                        }
                    )
                    StepperControl(
                        label = s.customDurationMin,
                        value = currentDur,
                        step = 5,
                        onDecrement = {
                            onUpdateTask { it.copy(customPomodoroMinutes = (currentDur - 5).coerceAtLeast(5)) }
                        },
                        onIncrement = {
                            onUpdateTask { it.copy(customPomodoroMinutes = (currentDur + 5).coerceAtMost(120)) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPomodoroDialog = false }) {
                    Text(s.save, color = CoralPrimary)
                }
            }
        )
    }
}

@Composable
private fun StepperControl(
    label: String,
    value: Int,
    step: Int = 1,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = DarkTextSecondary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDecrement)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Remove, null, tint = DarkTextPrimary, modifier = Modifier.size(15.dp))
            }
        }
        Text(
            text = value.toString(),
            fontSize = 14.sp,
            color = CoralPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onIncrement)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = DarkTextPrimary, modifier = Modifier.size(15.dp))
            }
        }
    }
}
