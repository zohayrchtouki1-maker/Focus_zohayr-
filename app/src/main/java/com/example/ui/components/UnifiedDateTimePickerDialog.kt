package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DateUtils
import com.example.notifications.SystemPermissionsHelper
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun UnifiedDateTimePickerDialog(
    initialEpochDay: Long?,
    initialReminderTime: String?,
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (epochDay: Long, reminderTime: String) -> Unit
) {
    val context = LocalContext.current
    val exactAlarmGranted = remember { SystemPermissionsHelper.isExactAlarmPermissionGranted(context) }
    val todayEpochDay = remember { DateUtils.todayEpochDay() }
    var selectedEpochDay by remember { mutableLongStateOf(initialEpochDay ?: todayEpochDay) }

    val defaultCal = remember {
        Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
    }
    val parsedHour = remember(initialReminderTime) {
        initialReminderTime?.split(":")?.getOrNull(0)?.toIntOrNull()
            ?: defaultCal.get(Calendar.HOUR_OF_DAY)
    }
    val parsedMinute = remember(initialReminderTime) {
        initialReminderTime?.split(":")?.getOrNull(1)?.toIntOrNull()
            ?: defaultCal.get(Calendar.MINUTE)
    }

    var selectedHour by remember { mutableIntStateOf(parsedHour.coerceIn(0, 23)) }
    var selectedMinute by remember { mutableIntStateOf(parsedMinute.coerceIn(0, 59)) }

    // Displayed month/year state for the Calendar grid
    val initialMonthCal = remember(selectedEpochDay) {
        Calendar.getInstance().apply {
            timeInMillis = DateUtils.millisFromEpochDay(selectedEpochDay)
        }
    }
    var displayedYear by remember { mutableIntStateOf(initialMonthCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(initialMonthCal.get(Calendar.MONTH)) }

    val primaryRoyalBlue = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline
    val secondaryBtnBg = MaterialTheme.colorScheme.secondaryContainer
    val secondaryBtnText = MaterialTheme.colorScheme.onSecondaryContainer

    fun syncDisplayedMonthToDay(epochDay: Long) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = DateUtils.millisFromEpochDay(epochDay)
        }
        displayedYear = cal.get(Calendar.YEAR)
        displayedMonth = cal.get(Calendar.MONTH)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardBg,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 12.dp)
                .testTag("unified_date_time_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dialog Title ("التذكير") & Top Live Preview Badge (e.g., "اليوم 18:34")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isArabic) "التذكير" else "Reminder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = primaryRoyalBlue.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, primaryRoyalBlue.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("reminder_live_preview_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = primaryRoyalBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${DateUtils.formatEpochDay(selectedEpochDay, isArabic)} %02d:%02d".format(
                                    selectedHour,
                                    selectedMinute
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = primaryRoyalBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Graceful Exact Alarm Permission Prompt if not yet granted
                if (!exactAlarmGranted) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = primaryRoyalBlue.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryRoyalBlue.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { SystemPermissionsHelper.openExactAlarmSettings(context) }
                            .testTag("exact_alarm_permission_prompt")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = primaryRoyalBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isArabic) {
                                    "اضغط لتفعيل إذن المنبه الدقيق للرنين في الوقت المحدد"
                                } else {
                                    "Tap to allow Exact Alarms so reminders ring right on time"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 1. Calendar Month Grid in the middle
                CalendarMonthGrid(
                    displayedYear = displayedYear,
                    displayedMonth = displayedMonth,
                    selectedEpochDay = selectedEpochDay,
                    todayEpochDay = todayEpochDay,
                    isArabic = isArabic,
                    primaryColor = primaryRoyalBlue,
                    onPreviousMonth = {
                        if (displayedMonth == 0) {
                            displayedMonth = 11
                            displayedYear -= 1
                        } else {
                            displayedMonth -= 1
                        }
                    },
                    onNextMonth = {
                        if (displayedMonth == 11) {
                            displayedMonth = 0
                            displayedYear += 1
                        } else {
                            displayedMonth += 1
                        }
                    },
                    onSelectDay = { day ->
                        selectedEpochDay = day
                    }
                )

                HorizontalDivider(color = borderCol)

                // 2. Quick Preset Chips ("بعد 15 د", "بعد ساعة", "غداً 9:00")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickTimePresetChip(
                        label = if (isArabic) "بعد 15 د" else "In 15m",
                        modifier = Modifier.weight(1f).testTag("preset_15m_chip"),
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
                            val day = DateUtils.epochDayFromMillis(cal.timeInMillis)
                            selectedEpochDay = day
                            syncDisplayedMonthToDay(day)
                            selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                            selectedMinute = cal.get(Calendar.MINUTE)
                        }
                    )
                    QuickTimePresetChip(
                        label = if (isArabic) "بعد ساعة" else "In 1h",
                        modifier = Modifier.weight(1f).testTag("preset_1h_chip"),
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                            val day = DateUtils.epochDayFromMillis(cal.timeInMillis)
                            selectedEpochDay = day
                            syncDisplayedMonthToDay(day)
                            selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                            selectedMinute = cal.get(Calendar.MINUTE)
                        }
                    )
                    QuickTimePresetChip(
                        label = if (isArabic) "غداً 9:00" else "Tomorrow 9:00",
                        modifier = Modifier.weight(1f).testTag("preset_tomorrow_9am_chip"),
                        onClick = {
                            val day = todayEpochDay + 1
                            selectedEpochDay = day
                            syncDisplayedMonthToDay(day)
                            selectedHour = 9
                            selectedMinute = 0
                        }
                    )
                }

                // 3. Side-by-side Scrolling Wheel Picker for Hours (00-23) and Minutes (00-59) separated by ':'
                // Keep wheel order LTR (Hours : Minutes) so digital time reads HH:MM naturally
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, borderCol),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .padding(horizontal = 24.dp)
                        ) {
                            // Center selection highlight bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primaryRoyalBlue.copy(alpha = 0.15f))
                                    .border(1.dp, primaryRoyalBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                WheelNumberPicker(
                                    range = 0..23,
                                    selectedValue = selectedHour,
                                    onValueSelected = { selectedHour = it },
                                    primaryColor = primaryRoyalBlue,
                                    modifier = Modifier.width(90.dp).testTag("wheel_hour_picker")
                                )

                                Text(
                                    text = ":",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = primaryRoyalBlue,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                WheelNumberPicker(
                                    range = 0..59,
                                    selectedValue = selectedMinute,
                                    onValueSelected = { selectedMinute = it },
                                    primaryColor = primaryRoyalBlue,
                                    modifier = Modifier.width(90.dp).testTag("wheel_minute_picker")
                                )
                            }
                        }
                    }
                }

                // 4. Bottom Pill Buttons: Royal-Blue "تم" (Save) and Dark-Gray "إلغاء" (Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = secondaryBtnBg,
                            contentColor = secondaryBtnText
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("picker_cancel_button")
                    ) {
                        Text(
                            text = if (isArabic) "إلغاء" else "Cancel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val formattedTime = "%02d:%02d".format(selectedHour, selectedMinute)
                            onConfirm(selectedEpochDay, formattedTime)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryRoyalBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("picker_confirm_button")
                    ) {
                        Text(
                            text = if (isArabic) "تم" else "Save",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    displayedYear: Int,
    displayedMonth: Int,
    selectedEpochDay: Long,
    todayEpochDay: Long,
    isArabic: Boolean,
    primaryColor: Color,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit
) {
    val monthCal = remember(displayedYear, displayedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val monthTitle = remember(displayedYear, displayedMonth, isArabic) {
        val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
        SimpleDateFormat("MMMM yyyy", locale).format(monthCal.time)
    }

    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Sunday = 0 .. Saturday = 6
    val firstDayOffset = (monthCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)

    val dayHeaders = if (isArabic) {
        listOf("ح", "ن", "ث", "ر", "خ", "ج", "س")
    } else {
        listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Month navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            dayHeaders.forEach { hdr ->
                Text(
                    text = hdr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Day cells (up to 6 weeks)
        val totalSlots = ((firstDayOffset + daysInMonth + 6) / 7) * 7
        for (week in 0 until (totalSlots / 7)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dow in 0..6) {
                    val slot = week * 7 + dow
                    val dayNum = slot - firstDayOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val dayCal = Calendar.getInstance().apply {
                            set(displayedYear, displayedMonth, dayNum, 12, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val cellEpochDay = DateUtils.epochDayFromMillis(dayCal.timeInMillis)
                        val isSelected = cellEpochDay == selectedEpochDay
                        val isToday = cellEpochDay == todayEpochDay

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> primaryColor
                                        isToday -> primaryColor.copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelectDay(cellEpochDay) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNum.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    isToday -> primaryColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).height(34.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTimePresetChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberPicker(
    range: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val values = remember(range) { range.toList() }
    val itemHeight = 42.dp
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    // Sync external preset changes to the wheel scroll position
    LaunchedEffect(selectedValue) {
        val targetIndex = values.indexOf(selectedValue).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != targetIndex && !listState.isScrollInProgress) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Detect centered item when user scrolls the wheel
    val centeredIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val base = listState.firstVisibleItemIndex
            if (offset > 60) (base + 1).coerceAtMost(values.lastIndex) else base
        }
    }

    LaunchedEffect(centeredIndex, listState.isScrollInProgress) {
        val v = values.getOrNull(centeredIndex)
        if (v != null && v != selectedValue) {
            onValueSelected(v)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(itemHeight * 3)
    ) {
        items(values.size) { index ->
            val number = values[index]
            val isSelected = number == selectedValue
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        onValueSelected(number)
                        scope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
            ) {
                Text(
                    text = "%02d".format(number),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = if (isSelected) 22.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        primaryColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    }
                )
            }
        }
    }
}
