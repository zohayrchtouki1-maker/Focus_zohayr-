package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.label
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.TagItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.ui.theme.CoralPrimary
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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.taskInspector,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (!task.isCompleted) {
                IconButton(onClick = onStartFocus, modifier = Modifier.testTag("inspector_play_button")) {
                    Icon(Icons.Default.PlayArrow, contentDescription = s.start, tint = CoralPrimary)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("inspector_close_button")) {
                Icon(Icons.Default.Close, contentDescription = s.cancel)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Complete Checkbox + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (task.isCompleted) task.priority.color() else Color.Transparent)
                        .border(2.dp, task.priority.color(), CircleShape)
                        .clickable(onClick = onToggleComplete),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                OutlinedTextField(
                    value = task.title,
                    onValueChange = { newTitle -> onUpdateTask { it.copy(title = newTitle) } },
                    label = { Text(s.taskTitle) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
            }

            // Priority Flag Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.priority, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        val isSelected = task.priority == p
                        val pColor = p.color()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) pColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, pColor) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onUpdateTask { it.copy(priority = p) } }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Flag, null, tint = pColor, modifier = Modifier.size(16.dp))
                                Text(p.label(s), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Pomodoro Progress & Estimate Editor
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = s.pomodoroProgress,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${task.completedPomodoros} / ${task.estimatedPomodoros}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CoralPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Completed adjust
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
                        // Estimated adjust
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
                    }

                    // Custom Pomodoro Duration
                    val currentDur = task.customPomodoroMinutes ?: defaultPomodoroMinutes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.customDurationMin,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onUpdateTask { it.copy(customPomodoroMinutes = (currentDur - 5).coerceAtLeast(5)) }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "$currentDur ${s.minutesShort}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                        IconButton(
                            onClick = {
                                onUpdateTask { it.copy(customPomodoroMinutes = (currentDur + 5).coerceAtMost(120)) }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Subtasks Checklist
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${s.subtasks} (${task.subtasks.count { it.isDone }}/${task.subtasks.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                task.subtasks.forEach { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable { onToggleSubtask(sub.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (sub.isDone) ForestEmerald else Color.Transparent)
                                .border(1.5.dp, if (sub.isDone) ForestEmerald else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sub.isDone) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sub.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = if (sub.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { onDeleteSubtask(sub.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        placeholder = { Text(s.addSubtaskPlaceholder) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newSubtaskTitle.isNotBlank()) {
                                onAddSubtask(newSubtaskTitle)
                                newSubtaskTitle = ""
                            }
                        }),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newSubtaskTitle.isNotBlank()) {
                                onAddSubtask(newSubtaskTitle)
                                newSubtaskTitle = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = s.add, tint = CoralPrimary)
                    }
                }
            }

            // Scheduling: Due Date, Reminder, Repeat
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.dueDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val today = DateUtils.todayEpochDay()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduleChip(
                        label = s.today,
                        selected = !task.isSomeday && task.dueDateEpochDay == today,
                        onClick = { onUpdateTask { it.copy(dueDateEpochDay = today, isSomeday = false) } }
                    )
                    ScheduleChip(
                        label = s.tomorrow,
                        selected = !task.isSomeday && task.dueDateEpochDay == today + 1,
                        onClick = { onUpdateTask { it.copy(dueDateEpochDay = today + 1, isSomeday = false) } }
                    )
                    ScheduleChip(
                        label = s.someday,
                        selected = task.isSomeday,
                        onClick = { onUpdateTask { it.copy(dueDateEpochDay = null, isSomeday = true) } }
                    )
                    ScheduleChip(
                        label = task.dueDateEpochDay?.let { DateUtils.formatEpochDay(it, isArabic) } ?: s.customDate,
                        selected = false,
                        icon = Icons.Default.CalendarToday,
                        onClick = { showDatePicker = true }
                    )
                    if (task.dueDateEpochDay != null || task.isSomeday) {
                        ScheduleChip(
                            label = s.clearDate,
                            selected = false,
                            onClick = { onUpdateTask { it.copy(dueDateEpochDay = null, isSomeday = false) } }
                        )
                    }
                }

                // Reminder Time quick chips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Alarm, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.reminder, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf("09:00", "14:00", "19:00", null).forEach { time ->
                        val active = task.reminderTime == time
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUpdateTask { it.copy(reminderTime = time) } }
                        ) {
                            Text(
                                text = time ?: s.priorityNone,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Repeat Rule
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Repeat, null, modifier = Modifier.size(16.dp), tint = ForestEmerald)
                    Text(s.repeat, style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecurrenceRule.entries.forEach { rule ->
                        ScheduleChip(
                            label = rule.label(s),
                            selected = task.recurrence == rule,
                            onClick = { onUpdateTask { it.copy(recurrence = rule) } }
                        )
                    }
                }
            }

            // Organization: Project & Tags
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.project, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduleChip(
                        label = s.noProject,
                        selected = task.projectId == null,
                        onClick = { onUpdateTask { it.copy(projectId = null) } }
                    )
                    projects.filter { !it.isArchived }.forEach { proj ->
                        ScheduleChip(
                            label = proj.name,
                            selected = task.projectId == proj.id,
                            accent = parseHexColor(proj.colorHex),
                            onClick = { onUpdateTask { it.copy(projectId = proj.id) } }
                        )
                    }
                }

                Text(s.tags, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        val selected = task.tagIds.contains(tag.id)
                        ScheduleChip(
                            label = "#${tag.name}",
                            selected = selected,
                            accent = parseHexColor(tag.colorHex),
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

            // Auto-saving Notes
            OutlinedTextField(
                value = task.notes,
                onValueChange = { notes -> onUpdateTask { it.copy(notes = notes) } },
                label = { Text(s.notes) },
                placeholder = { Text(s.notesPlaceholder) },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Footer: Creation Timestamp + Delete Button
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${s.createdAt}: ${DateUtils.formatDateTime(task.createdAtEpochMillis, isArabic)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onDeleteTask,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralPrimary),
                border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("inspector_delete_button")
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(s.deleteTask)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onUpdateTask { it.copy(dueDateEpochDay = millis / 86_400_000L, isSomeday = false) }
                        }
                        showDatePicker = false
                    }
                ) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(s.cancel) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun StepperControl(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDecrement)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onIncrement)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ScheduleChip(
    label: String,
    selected: Boolean,
    accent: Color = CoralPrimary,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) BorderStroke(1.5.dp, accent) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
