package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.model.AppLanguage
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoyalBlueChart
import com.example.viewmodel.MainUiState
import java.util.Calendar
import java.util.TimeZone

private val ArabicDayHeaders = listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")
private val EnglishDayHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val ArabicMonthNames = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
)
private val EnglishMonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun CalendarDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onAddTaskOnDate: (title: String, epochDay: Long, reminderTime: String) -> Unit,
    onInspectTask: (String) -> Unit,
    onToggleTaskCompleted: (String) -> Unit
) {
    val isArabic = uiState.data.settings.language == AppLanguage.AR
    val todayEpochDay = remember { DateUtils.todayEpochDay() }
    var selectedEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var monthMenuOpen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var preselectedHour by remember { mutableIntStateOf(9) }

    BackHandler { onDismiss() }

    // Compute the 7 days of the week (Monday..Sunday) containing (todayEpochDay + weekOffset * 7)
    val anchorDay = todayEpochDay + (weekOffset * 7L)
    val weekDays = remember(anchorDay) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = anchorDay * 86_400_000L
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ..., 7=Sat
        val offsetFromMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dayOfWeek)
        val mondayEpochDay = anchorDay + offsetFromMonday
        (0..6).map { idx -> mondayEpochDay + idx }
    }

    val selectedCal = remember(selectedEpochDay) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedEpochDay * 86_400_000L
        }
    }
    val monthIdx = selectedCal.get(Calendar.MONTH).coerceIn(0, 11)
    val yearNum = selectedCal.get(Calendar.YEAR)
    val monthYearLabel = if (isArabic) {
        "${ArabicMonthNames[monthIdx]} $yearNum"
    } else {
        "${EnglishMonthNames[monthIdx]} $yearNum"
    }

    // Tasks for the selected day
    val dayTasks = remember(uiState.data.tasks, selectedEpochDay) {
        uiState.data.tasks.filter { task ->
            val matchesDue = task.dueDateEpochDay == selectedEpochDay
            val matchesCompletedDay = task.isCompleted &&
                task.completedAtEpochMillis != null &&
                DateUtils.epochDayFromMillis(task.completedAtEpochMillis) == selectedEpochDay
            matchesDue || matchesCompletedDay
        }
    }

    val scheduledTasks = remember(dayTasks) { dayTasks.filter { !it.isCompleted } }
    val completedTasks = remember(dayTasks) { dayTasks.filter { it.isCompleted } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Right-aligned `>` + "التقويم", left-aligned `+` add button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("calendar_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = s.cancel,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = if (isArabic) "التقويم" else "Calendar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        preselectedHour = 9
                        showAddDialog = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("calendar_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = s.add,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Centered month selector ("مارس 2026 ▾") above 7-day strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { monthMenuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = monthYearLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = monthMenuOpen,
                    onDismissRequest = { monthMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isArabic) "الأسبوع السابق" else "Previous Week") },
                        onClick = {
                            weekOffset -= 1
                            selectedEpochDay = todayEpochDay + (weekOffset * 7L)
                            monthMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isArabic) "هذا الأسبوع (اليوم)" else "This Week (Today)") },
                        onClick = {
                            weekOffset = 0
                            selectedEpochDay = todayEpochDay
                            monthMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isArabic) "الأسبوع القادم" else "Next Week") },
                        onClick = {
                            weekOffset += 1
                            selectedEpochDay = todayEpochDay + (weekOffset * 7L)
                            monthMenuOpen = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7-day strip (إث، ثل، أر، خم، جم، سب، أح) with active day in solid Coral Red circle #E05246 and task dot indicator
            val headers = if (isArabic) ArabicDayHeaders else EnglishDayHeaders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEachIndexed { index, epochDay ->
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = epochDay * 86_400_000L
                    }
                    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                    val isSelected = epochDay == selectedEpochDay
                    val hasTasks = uiState.data.tasks.any {
                        it.dueDateEpochDay == epochDay ||
                            (it.completedAtEpochMillis != null && DateUtils.epochDayFromMillis(it.completedAtEpochMillis) == epochDay)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedEpochDay = epochDay }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = headers[index],
                            fontSize = 12.sp,
                            color = DarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) CoralPrimary else Color.Transparent)
                        ) {
                            Text(
                                text = dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasTasks && isSelected -> CoralPrimary
                                        hasTasks -> CoralPrimary.copy(alpha = 0.75f)
                                        else -> Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkOutline, thickness = 1.dp)

            // Two-column header: "مجدول" on right, "مكتملة" on left
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(top = 8.dp, bottom = 8.dp, start = 52.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "مجدول" else "Scheduled",
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isArabic) "مكتملة" else "Completed",
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)

            // Two-column hourly grid from 08:00 upward
            val hoursList = remember { (8..22).toList() }
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(hoursList, key = { it }) { hour ->
                    val hourLabel = "%02d:00".format(hour)
                    val scheduledInHour = scheduledTasks.filter { task ->
                        val parsedHour = task.reminderTime?.substringBefore(":")?.toIntOrNull() ?: 8
                        parsedHour.coerceIn(8, 22) == hour
                    }
                    val completedInHour = completedTasks.filter { task ->
                        val parsedHour = if (task.completedAtEpochMillis != null) {
                            val c = Calendar.getInstance().apply { timeInMillis = task.completedAtEpochMillis }
                            c.get(Calendar.HOUR_OF_DAY)
                        } else {
                            task.reminderTime?.substringBefore(":")?.toIntOrNull() ?: 9
                        }
                        parsedHour.coerceIn(8, 22) == hour
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Hour label column (on right in RTL)
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight()
                                    .padding(top = 6.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = hourLabel,
                                    fontSize = 11.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = DarkTextSecondary
                                )
                            }

                            // Vertical divider line
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .fillMaxHeight()
                                    .background(DarkOutline)
                            )

                            // Right column: مجدول (Scheduled)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        preselectedHour = hour
                                        showAddDialog = true
                                    }
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                scheduledInHour.take(2).forEach { task ->
                                    CalendarTaskBlock(
                                        task = task,
                                        accentColor = CoralPrimary,
                                        onClick = {
                                            onDismiss()
                                            onInspectTask(task.id)
                                        }
                                    )
                                }
                            }

                            // Middle vertical divider between مجدول and مكتملة
                            Box(
                                modifier = Modifier
                                    .width(0.8.dp)
                                    .fillMaxHeight()
                                    .background(DarkOutline)
                            )

                            // Left column: مكتملة (Completed)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                completedInHour.take(2).forEach { task ->
                                    CalendarTaskBlock(
                                        task = task,
                                        accentColor = RoyalBlueChart,
                                        onClick = {
                                            onDismiss()
                                            onInspectTask(task.id)
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = DarkOutline, thickness = 0.8.dp)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newTaskTitle by remember { mutableStateOf("") }
        val timeFormatted = "%02d:00".format(preselectedHour)
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (isArabic) "إضافة مهمة مجدولة ($timeFormatted)" else "Add Scheduled Task ($timeFormatted)"
                )
            },
            text = {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text(s.addTaskPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTaskOnDate(newTaskTitle, selectedEpochDay, timeFormatted)
                            showAddDialog = false
                        }
                    },
                    enabled = newTaskTitle.isNotBlank()
                ) {
                    Text(s.add, color = CoralPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(s.cancel)
                }
            }
        )
    }
}

@Composable
private fun CalendarTaskBlock(
    task: TaskItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = task.title,
                fontSize = 11.sp,
                color = DarkTextPrimary,
                fontWeight = FontWeight.Medium,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
