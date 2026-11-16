package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.formatDuration
import com.example.model.AppLanguage
import com.example.model.FocusRecord
import com.example.model.ProjectItem
import com.example.model.ReportPeriod
import com.example.model.TaskItem
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

private enum class StatsTab { TASKS, POMODORO }

private fun formatMinutesHhMm(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val h = safe / 60
    val m = safe % 60
    return "%02d:%02d".format(h, m)
}

@Composable
fun ReportsDialog(
    uiState: MainUiState,
    s: Strings,
    onDismiss: () -> Unit,
    onAddManualRecord: (
        taskId: String?,
        projectId: String?,
        epochDay: Long,
        hour: Int,
        minute: Int,
        durationMinutes: Int
    ) -> Unit
) {
    BackHandler { onDismiss() }

    val data = uiState.data
    val isArabic = data.settings.language == AppLanguage.AR
    val today = remember { DateUtils.todayEpochDay() }
    var selectedTab by remember { mutableStateOf(StatsTab.POMODORO) }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.TODAY) }
    var chartPeriod by remember { mutableStateOf(ReportPeriod.TODAY) }
    var showManualDialog by remember { mutableStateOf(false) }

    // Summary calculations
    val focusTodayMins = data.focusRecords
        .filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == today }
        .sumOf { it.durationMinutes }
    val focusWeekMins = data.focusRecords
        .filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) >= today - 6 }
        .sumOf { it.durationMinutes }
    val totalFocusMins = data.focusRecords.sumOf { it.durationMinutes }

    val completedToday = data.tasks.count {
        it.isCompleted && it.completedAtEpochMillis != null &&
            DateUtils.epochDayFromMillis(it.completedAtEpochMillis) == today
    }
    val completedWeek = data.tasks.count {
        it.isCompleted && it.completedAtEpochMillis != null &&
            DateUtils.epochDayFromMillis(it.completedAtEpochMillis) >= today - 6
    }
    val totalCompleted = data.tasks.count { it.isCompleted }

    // Filter records for Donut Chart
    val filteredRecords = remember(data.focusRecords, selectedPeriod, today) {
        when (selectedPeriod) {
            ReportPeriod.TODAY -> data.focusRecords.filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == today }
            ReportPeriod.WEEK -> data.focusRecords.filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) >= today - 6 }
            ReportPeriod.MONTH -> data.focusRecords.filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) >= today - 29 }
            ReportPeriod.ALL -> data.focusRecords
        }
    }

    data class ProjectSlice(val name: String, val color: Color, val minutes: Int)
    val projectSlices = remember(filteredRecords, data.projects, s) {
        val grouped = filteredRecords.groupBy { it.projectId }
        grouped.map { (projId, recs) ->
            val proj = data.projects.find { it.id == projId }
            ProjectSlice(
                name = proj?.name ?: s.noProject,
                color = proj?.let { parseHexColor(it.colorHex) } ?: RoyalBlueChart,
                minutes = recs.sumOf { it.durationMinutes }
            )
        }.sortedByDescending { it.minutes }
    }
    val totalSliceMins = projectSlices.sumOf { it.minutes }.coerceAtLeast(1)

    // Last 7 days bar data
    val last7Days = remember(today) { (6 downTo 0).map { today - it } }
    val focusPerDay = remember(data.focusRecords, last7Days) {
        last7Days.map { day ->
            val mins = data.focusRecords
                .filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == day }
                .sumOf { it.durationMinutes }
            DateUtils.formatShortDayLabel(day, isArabic) to mins
        }
    }
    val tasksPerDay = remember(data.tasks, last7Days) {
        last7Days.map { day ->
            val count = data.tasks.count {
                it.isCompleted && it.completedAtEpochMillis != null &&
                    DateUtils.epochDayFromMillis(it.completedAtEpochMillis) == day
            }
            DateUtils.formatShortDayLabel(day, isArabic) to count
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: Right-aligned `>` + "الإحصائيات", left-aligned report/add-record icon
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
                        .testTag("close_reports_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = s.cancel,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = s.reportsTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("add_manual_record_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PostAdd,
                        contentDescription = s.addFocusRecord,
                        tint = DarkTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Full-width tabs (مهمة | بومودورو) with sharp 2dp #E05246 underline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
            ) {
                StatsTabItem(
                    label = if (isArabic) "مهمة" else "Task",
                    selected = selectedTab == StatsTab.TASKS,
                    onClick = { selectedTab = StatsTab.TASKS },
                    modifier = Modifier.weight(1f)
                )
                StatsTabItem(
                    label = if (isArabic) "بومودورو" else "Pomodoro",
                    selected = selectedTab == StatsTab.POMODORO,
                    onClick = { selectedTab = StatsTab.POMODORO },
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = DarkOutline, thickness = 1.dp)

            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Top 3-column summary card (#E05246 numbers)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedTab == StatsTab.POMODORO) {
                                ThreeColStatItem(
                                    value = formatMinutesHhMm(focusTodayMins),
                                    label = s.focusToday,
                                    modifier = Modifier.weight(1f)
                                )
                                ThreeColStatItem(
                                    value = formatMinutesHhMm(focusWeekMins),
                                    label = s.focusThisWeek,
                                    modifier = Modifier.weight(1f)
                                )
                                ThreeColStatItem(
                                    value = formatMinutesHhMm(totalFocusMins),
                                    label = s.totalFocusTime,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                ThreeColStatItem(
                                    value = completedToday.toString(),
                                    label = s.completedToday,
                                    modifier = Modifier.weight(1f)
                                )
                                ThreeColStatItem(
                                    value = completedWeek.toString(),
                                    label = s.completedThisWeek,
                                    modifier = Modifier.weight(1f)
                                )
                                ThreeColStatItem(
                                    value = totalCompleted.toString(),
                                    label = s.totalCompleted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // "سجلات بومودورو" hourly timeline grid with #3B7BF6 blocks
                item {
                    PomodoroRecordsHourlyGridCard(
                        records = data.focusRecords,
                        todayEpochDay = today,
                        isArabic = isArabic,
                        onAddRecord = { showManualDialog = true }
                    )
                }

                // "هدف التركيز اليومي" monthly calendar with #E05246 progress ring
                item {
                    DailyFocusGoalCalendarCard(
                        records = data.focusRecords,
                        todayEpochDay = today,
                        dailyGoalMinutes = (data.settings.pomodoroMinutes * 4).coerceAtLeast(60),
                        isArabic = isArabic
                    )
                }

                // Bar chart card with 4-segment pill switcher (يومي | أسبوعي | شهري | سنوي inside #25252B) and #3B7BF6 vertical bars
                item {
                    val activeBars = if (selectedTab == StatsTab.POMODORO) focusPerDay else tasksPerDay
                    val activeTitle = if (selectedTab == StatsTab.POMODORO) s.focusTrend7Days else s.tasksTrend7Days
                    val totalBarSum = activeBars.sumOf { it.second }
                    val avgBarVal = if (activeBars.isNotEmpty()) totalBarSum / activeBars.size else 0

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = activeTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isArabic) {
                                        "الإجمالي: ${if (selectedTab == StatsTab.POMODORO) formatDuration(totalBarSum, s) else totalBarSum}"
                                    } else {
                                        "Total: $totalBarSum"
                                    },
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary
                                )
                                Text(
                                    text = if (isArabic) {
                                        "المعدل: ${if (selectedTab == StatsTab.POMODORO) formatDuration(avgBarVal, s) else avgBarVal}"
                                    } else {
                                        "Avg: $avgBarVal"
                                    },
                                    fontSize = 11.sp,
                                    color = DarkTextSecondary
                                )
                            }

                            // 4-segment pill switcher (يومي | أسبوعي | شهري | سنوي inside #25252B)
                            FourSegmentPillSwitcher(
                                selectedPeriod = chartPeriod,
                                s = s,
                                onSelect = { chartPeriod = it }
                            )

                            // #3B7BF6 vertical bars
                            val maxVal = (activeBars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val count = activeBars.size.coerceAtLeast(1)
                                val slotW = size.width / count
                                val barW = slotW * 0.44f
                                activeBars.forEachIndexed { idx, (_, value) ->
                                    val ratio = value.toFloat() / maxVal.toFloat()
                                    val h = (size.height - 12.dp.toPx()) * ratio
                                    val x = idx * slotW + (slotW - barW) / 2f
                                    val y = size.height - h
                                    drawRoundRect(
                                        color = if (value > 0) RoyalBlueChart else DarkSurfaceVariant,
                                        topLeft = Offset(x, if (value > 0) y else size.height - 4.dp.toPx()),
                                        size = Size(barW, if (value > 0) h else 4.dp.toPx()),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                activeBars.forEach { (dayLabel, value) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = value.toString(),
                                            fontSize = 10.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = if (value > 0) RoyalBlueChart else DarkTextSecondary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = dayLabel,
                                            fontSize = 10.sp,
                                            color = DarkTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Project Time Distribution Card
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = s.projectDistribution,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )

                            FourSegmentPillSwitcher(
                                selectedPeriod = selectedPeriod,
                                s = s,
                                onSelect = { selectedPeriod = it }
                            )

                            if (projectSlices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = s.noFocusRecordsYet,
                                        fontSize = 13.sp,
                                        color = DarkTextSecondary
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(124.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val stroke = 20.dp.toPx()
                                            val d = size.minDimension - stroke
                                            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                                            var startAngle = -90f
                                            projectSlices.forEach { slice ->
                                                val sweep = 360f * (slice.minutes.toFloat() / totalSliceMins.toFloat())
                                                drawArc(
                                                    color = slice.color,
                                                    startAngle = startAngle,
                                                    sweepAngle = sweep.coerceAtLeast(2f),
                                                    useCenter = false,
                                                    topLeft = tl,
                                                    size = Size(d, d),
                                                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                                                )
                                                startAngle += sweep
                                            }
                                        }
                                        Text(
                                            text = formatDuration(filteredRecords.sumOf { it.durationMinutes }, s),
                                            fontSize = 13.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTextPrimary
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        projectSlices.forEach { slice ->
                                            val pct = (slice.minutes * 100) / totalSliceMins
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = slice.name,
                                                    fontSize = 12.sp,
                                                    color = DarkTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${formatDuration(slice.minutes, s)} ($pct%)",
                                                    fontSize = 11.sp,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    color = DarkTextSecondary
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
    }

    if (showManualDialog) {
        ManualFocusRecordDialog(
            tasks = data.tasks,
            projects = data.projects,
            s = s,
            onDismiss = { showManualDialog = false },
            onSave = { taskId, projectId, day, hour, minute, duration ->
                onAddManualRecord(taskId, projectId, day, hour, minute, duration)
                showManualDialog = false
            }
        )
    }
}

@Composable
private fun StatsTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) CoralPrimary else DarkTextSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) CoralPrimary else Color.Transparent)
        )
    }
}

@Composable
private fun ThreeColStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = CoralPrimary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = DarkTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FourSegmentPillSwitcher(
    selectedPeriod: ReportPeriod,
    s: Strings,
    onSelect: (ReportPeriod) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(
                ReportPeriod.TODAY to s.periodToday,
                ReportPeriod.WEEK to s.periodWeek,
                ReportPeriod.MONTH to s.periodMonth,
                ReportPeriod.ALL to s.periodAll
            ).forEach { (period, label) ->
                val active = selectedPeriod == period
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) CoralPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(period) }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else DarkTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroRecordsHourlyGridCard(
    records: List<FocusRecord>,
    todayEpochDay: Long,
    isArabic: Boolean,
    onAddRecord: () -> Unit
) {
    val days = remember(todayEpochDay) { (6 downTo 0).map { todayEpochDay - it } }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isArabic) "سجلات بومودورو" else "Pomodoro Records",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onAddRecord,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hourly heatmap rows for the last 7 days with #3B7BF6 blocks
            days.forEach { day ->
                val dayRecords = records.filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == day }
                val dayLabel = DateUtils.formatShortDayLabel(day, isArabic)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                ) {
                    Text(
                        text = dayLabel,
                        fontSize = 10.sp,
                        color = DarkTextSecondary,
                        modifier = Modifier.width(42.dp),
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            dayRecords.forEach { rec ->
                                val cal = Calendar.getInstance().apply { timeInMillis = rec.startedAtEpochMillis }
                                val startMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                                val startFraction = (startMinOfDay.coerceIn(360, 1440) - 360) / 1080f
                                val widthFraction = (rec.durationMinutes.coerceAtLeast(15) / 1080f).coerceAtLeast(0.035f)
                                val x = size.width * startFraction.coerceIn(0f, 0.96f)
                                val w = (size.width * widthFraction).coerceAtMost(size.width - x)
                                drawRoundRect(
                                    color = RoyalBlueChart,
                                    topLeft = Offset(x, 0f),
                                    size = Size(w, size.height),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Hour axis labels (06:00 .. 24:00)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 42.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("06:00", "12:00", "18:00", "24:00").forEach { hr ->
                    Text(
                        text = hr,
                        fontSize = 9.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = DarkTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyFocusGoalCalendarCard(
    records: List<FocusRecord>,
    todayEpochDay: Long,
    dailyGoalMinutes: Int,
    isArabic: Boolean
) {
    val todayCal = remember(todayEpochDay) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = todayEpochDay * 86_400_000L
        }
    }
    val year = todayCal.get(Calendar.YEAR)
    val month = todayCal.get(Calendar.MONTH)
    val daysInMonth = todayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val monthStartEpochDay = remember(year, month) {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, 1)
        }
        c.timeInMillis / 86_400_000L
    }

    val monthName = if (isArabic) {
        listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")[month]
    } else {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month]
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isArabic) "هدف التركيز اليومي" else "Daily Focus Goal",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$monthName $year",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextSecondary
                )
            }

            // Weekday headers
            val dayHeaders = if (isArabic) {
                listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")
            } else {
                listOf("M", "T", "W", "T", "F", "S", "S")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayHeaders.forEach { h ->
                    Text(
                        text = h,
                        fontSize = 11.sp,
                        color = DarkTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calendar days grid with #E05246 progress ring
            val daysList = (1..daysInMonth).toList()
            daysList.chunked(7).forEach { weekChunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (slot in 0 until 7) {
                        val dayNum = weekChunk.getOrNull(slot)
                        if (dayNum != null) {
                            val epochDay = monthStartEpochDay + (dayNum - 1)
                            val isToday = epochDay == todayEpochDay
                            val minsForDay = records
                                .filter { DateUtils.epochDayFromMillis(it.startedAtEpochMillis) == epochDay }
                                .sumOf { it.durationMinutes }
                            val progress = (minsForDay.toFloat() / dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .size(32.dp)
                            ) {
                                Canvas(modifier = Modifier.size(28.dp)) {
                                    drawCircle(
                                        color = DarkSurfaceVariant,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    if (progress > 0f) {
                                        drawArc(
                                            color = CoralPrimary,
                                            startAngle = -90f,
                                            sweepAngle = 360f * progress,
                                            useCenter = false,
                                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    if (isToday) {
                                        drawCircle(
                                            color = CoralPrimary,
                                            radius = size.minDimension / 2.3f
                                        )
                                    }
                                }
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isToday) Color.White else DarkTextPrimary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualFocusRecordDialog(
    tasks: List<TaskItem>,
    projects: List<ProjectItem>,
    s: Strings,
    onDismiss: () -> Unit,
    onSave: (String?, String?, Long, Int, Int, Int) -> Unit
) {
    val today = remember { DateUtils.todayEpochDay() }
    var selectedTaskId by remember { mutableStateOf<String?>(tasks.firstOrNull { !it.isCompleted }?.id) }
    var selectedProjectId by remember { mutableStateOf<String?>(projects.firstOrNull()?.id) }
    var selectedDay by remember { mutableStateOf(today) }
    var startHour by remember { mutableIntStateOf(10) }
    var durationText by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addFocusRecord) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.selectTaskToFocus, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTaskId == null) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTaskId = null }
                    ) {
                        Text(s.freeFocus, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                    }
                    tasks.take(5).forEach { t ->
                        val active = selectedTaskId == t.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedTaskId = t.id
                                    selectedProjectId = t.projectId
                                }
                        ) {
                            Text(t.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                // Day
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(today to s.today, today - 1 to (if (s.today == "اليوم") "أمس" else "Yesterday")).forEach { (d, lbl) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedDay == d) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDay = d }
                        ) {
                            Text(lbl, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }

                // Start Hour chips
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                    Text(s.startTime, style = MaterialTheme.typography.labelSmall)
                    listOf(9, 13, 17, 20).forEach { hr ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (startHour == hr) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { startHour = hr }
                        ) {
                            Text("$hr:00", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(s.durationMinutes) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = (durationText.toIntOrNull() ?: 25).coerceIn(1, 300)
                    onSave(selectedTaskId, selectedProjectId, selectedDay, startHour, 0, mins)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                )
            ) { Text(s.saveRecord) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = DarkTextPrimary
                )
            ) { Text(s.cancel) }
        }
    )
}
