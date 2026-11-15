package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DateUtils
import com.example.i18n.Strings
import com.example.i18n.formatDuration
import com.example.model.AppLanguage
import com.example.model.ProjectItem
import com.example.model.ReportPeriod
import com.example.model.TaskItem
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LongBreakColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.SunlightGold
import com.example.viewmodel.MainUiState

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
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.WEEK) }
    var showManualDialog by remember { mutableStateOf(false) }

    // Top 6 Summary Cards calculations
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

    // Group by Project
    data class ProjectSlice(val name: String, val color: Color, val minutes: Int)
    val projectSlices = remember(filteredRecords, data.projects, s) {
        val grouped = filteredRecords.groupBy { it.projectId }
        grouped.map { (projId, recs) ->
            val proj = data.projects.find { it.id == projId }
            ProjectSlice(
                name = proj?.name ?: s.noProject,
                color = proj?.let { parseHexColor(it.colorHex) } ?: Color(0xFF64748B),
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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.reportsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showManualDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_manual_record_button")
                ) {
                    Text(s.addFocusRecord, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_reports_button")) {
                    Icon(Icons.Default.Close, contentDescription = s.cancel)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top 6 Summary Cards (2 rows of 3)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportStatCard(s.focusToday, formatDuration(focusTodayMins, s), CoralPrimary, Modifier.weight(1f))
                            ReportStatCard(s.focusThisWeek, formatDuration(focusWeekMins, s), PriorityLowColor, Modifier.weight(1f))
                            ReportStatCard(s.totalFocusTime, formatDuration(totalFocusMins, s), LongBreakColor, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportStatCard(s.completedToday, completedToday.toString(), ForestEmerald, Modifier.weight(1f))
                            ReportStatCard(s.completedThisWeek, completedWeek.toString(), SunlightGold, Modifier.weight(1f))
                            ReportStatCard(s.totalCompleted, totalCompleted.toString(), Color(0xFF0EA5E9), Modifier.weight(1f))
                        }
                    }
                }

                // Project Time Distribution (Donut Chart + Legend)
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = s.projectDistribution,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        ReportPeriod.TODAY to s.periodToday,
                                        ReportPeriod.WEEK to s.periodWeek,
                                        ReportPeriod.MONTH to s.periodMonth,
                                        ReportPeriod.ALL to s.periodAll
                                    ).forEach { (period, label) ->
                                        val active = selectedPeriod == period
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (active) CoralPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { selectedPeriod = period }
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (projectSlices.isEmpty()) {
                                Text(
                                    text = s.noFocusRecordsYet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Donut Chart
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(136.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val stroke = 24.dp.toPx()
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
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Legend Table
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        projectSlices.forEach { slice ->
                                            val pct = (slice.minutes * 100) / totalSliceMins
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(slice.color)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = slice.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${formatDuration(slice.minutes, s)} ($pct%)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 7-Day Bar Charts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BarChartCard(
                            title = s.focusTrend7Days,
                            bars = focusPerDay,
                            barColor = CoralPrimary,
                            unitSuffix = s.minutesShort,
                            modifier = Modifier.weight(1f)
                        )
                        BarChartCard(
                            title = s.tasksTrend7Days,
                            bars = tasksPerDay,
                            barColor = ForestEmerald,
                            unitSuffix = "",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Focus Timeline Log Header
                item {
                    Text(
                        text = s.focusTimelineLog,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (data.focusRecords.isEmpty()) {
                    item {
                        Text(
                            text = s.noFocusRecordsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(data.focusRecords.take(30), key = { it.id }) { rec ->
                        val proj = data.projects.find { it.id == rec.projectId }
                        val pColor = proj?.let { parseHexColor(it.colorHex) } ?: CoralPrimary
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(pColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.taskTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${proj?.name ?: s.noProject} • ${DateUtils.formatDateTime(rec.startedAtEpochMillis, isArabic)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = pColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = formatDuration(rec.durationMinutes, s),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = pColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
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
private fun ReportStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BarChartCard(
    title: String,
    bars: List<Pair<String, Int>>,
    barColor: Color,
    unitSuffix: String,
    modifier: Modifier = Modifier
) {
    val maxVal = (bars.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val count = bars.size.coerceAtLeast(1)
                val slotW = size.width / count
                val barW = slotW * 0.52f
                bars.forEachIndexed { idx, (_, value) ->
                    val ratio = value.toFloat() / maxVal.toFloat()
                    val h = (size.height - 10.dp.toPx()) * ratio
                    val x = idx * slotW + (slotW - barW) / 2f
                    val y = size.height - h
                    drawRoundRect(
                        color = if (value > 0) barColor else barColor.copy(alpha = 0.15f),
                        topLeft = Offset(x, if (value > 0) y else size.height - 4.dp.toPx()),
                        size = Size(barW, if (value > 0) h else 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                bars.forEach { (dayLabel, value) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$value$unitSuffix",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedTaskId = null }
                    ) {
                        Text(s.freeFocus, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                    }
                    tasks.take(5).forEach { t ->
                        val active = selectedTaskId == t.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
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
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { selectedDay = d }
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
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { startHour = hr }
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) { Text(s.saveRecord) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text(s.cancel) }
        }
    )
}
