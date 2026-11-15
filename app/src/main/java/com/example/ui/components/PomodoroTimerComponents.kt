package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.Strings
import com.example.i18n.label
import com.example.model.TaskItem
import com.example.model.TimerCountMode
import com.example.model.TimerPhase
import com.example.model.WhiteNoiseType
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.FocusGradientEnd
import com.example.ui.theme.FocusGradientStart
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LongBreakColor
import com.example.ui.theme.LongBreakGradientEnd
import com.example.ui.theme.LongBreakGradientStart
import com.example.ui.theme.ShortBreakColor
import com.example.ui.theme.ShortBreakGradientEnd
import com.example.ui.theme.ShortBreakGradientStart
import com.example.viewmodel.MainUiState

fun formatSecondsMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(mins, secs)
}

@Composable
fun FloatingMiniTimerBar(
    uiState: MainUiState,
    s: Strings,
    onExpand: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timer = uiState.timer
    val activeTask = uiState.data.tasks.find { it.id == timer.activeTaskId }
    val displaySecs = if (timer.countMode == TimerCountMode.STOPWATCH && timer.phase == TimerPhase.FOCUS) {
        timer.stopwatchSeconds
    } else {
        timer.remainingSeconds
    }
    val accent = when (timer.phase) {
        TimerPhase.FOCUS -> CoralPrimary
        TimerPhase.SHORT_BREAK -> ShortBreakColor
        TimerPhase.LONG_BREAK -> LongBreakColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onExpand)
                .testTag("mini_timer_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (timer.phase == TimerPhase.FOCUS) {
                        TomatoIcon(filled = true, size = 20.dp, tint = accent)
                    } else {
                        Icon(Icons.Default.Timer, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeTask?.title ?: s.freeFocus,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timer.phase.label(s) + if (uiState.data.settings.whiteNoise != WhiteNoiseType.NONE && timer.isRunning) {
                            " • ${uiState.data.settings.whiteNoise.label(s)}"
                        } else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Text(
                    text = formatSecondsMmSs(displaySecs),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                val pauseBlocked = timer.strictMode && timer.phase == TimerPhase.FOCUS && timer.isRunning
                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !pauseBlocked) {
                            if (timer.isRunning) onPause() else onStartOrResume()
                        }
                        .testTag("mini_timer_play_pause")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                pauseBlocked -> Icons.Default.Lock
                                timer.isRunning -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (timer.isRunning) s.pause else s.start,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenTimerOverlay(
    uiState: MainUiState,
    s: Strings,
    onCollapse: () -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkipBreak: () -> Unit,
    onSetCountMode: (TimerCountMode) -> Unit,
    onToggleStrictMode: () -> Unit,
    onSelectTask: (String?) -> Unit,
    onCompleteActiveTask: (String) -> Unit,
    onSetWhiteNoise: (WhiteNoiseType) -> Unit,
    onSetWhiteNoiseVolume: (Float) -> Unit
) {
    val timer = uiState.timer
    val settings = uiState.data.settings
    val activeTask = uiState.data.tasks.find { it.id == timer.activeTaskId }

    var showTaskPicker by remember { mutableStateOf(false) }
    var showNoiseMixer by remember { mutableStateOf(false) }
    var showStrictExitWarning by remember { mutableStateOf(false) }

    fun attemptCloseOrAbandon() {
        if (timer.strictMode && timer.isRunning && timer.phase == TimerPhase.FOCUS) {
            showStrictExitWarning = true
        } else {
            onCollapse()
        }
    }

    BackHandler {
        attemptCloseOrAbandon()
    }

    val gradTop by animateColorAsState(
        targetValue = when (timer.phase) {
            TimerPhase.FOCUS -> FocusGradientStart
            TimerPhase.SHORT_BREAK -> ShortBreakGradientStart
            TimerPhase.LONG_BREAK -> LongBreakGradientStart
        },
        label = "gradTop"
    )
    val gradBottom by animateColorAsState(
        targetValue = when (timer.phase) {
            TimerPhase.FOCUS -> FocusGradientEnd
            TimerPhase.SHORT_BREAK -> ShortBreakGradientEnd
            TimerPhase.LONG_BREAK -> LongBreakGradientEnd
        },
        label = "gradBottom"
    )
    val ringAccent = when (timer.phase) {
        TimerPhase.FOCUS -> Color(0xFFFF6B61)
        TimerPhase.SHORT_BREAK -> Color(0xFF2DD4BF)
        TimerPhase.LONG_BREAK -> Color(0xFF818CF8)
    }

    val isStopwatch = timer.countMode == TimerCountMode.STOPWATCH && timer.phase == TimerPhase.FOCUS
    val displaySecs = if (isStopwatch) timer.stopwatchSeconds else timer.remainingSeconds
    val targetProgress = if (isStopwatch) {
        ((timer.stopwatchSeconds % 60) / 60f)
    } else {
        if (timer.totalPhaseSeconds > 0) {
            timer.remainingSeconds.toFloat() / timer.totalPhaseSeconds.toFloat()
        } else 1f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 450),
        label = "ringProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(gradTop, gradBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Collapse + Task Switcher Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { attemptCloseOrAbandon() },
                    modifier = Modifier.testTag("timer_collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Task Switcher Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showTaskPicker = true }
                        .testTag("timer_task_switcher_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (activeTask != null) {
                            TomatoIcon(filled = true, size = 16.dp, tint = ringAccent)
                            Text(
                                text = activeTask.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeTask.completedPomodoros}/${activeTask.estimatedPomodoros}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (!activeTask.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = s.completeTask,
                                    tint = ForestEmerald,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onCompleteActiveTask(activeTask.id) }
                                )
                            }
                        } else {
                            Icon(Icons.Default.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = s.freeFocus,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mode & Strict Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = timer.phase.label(s),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                if (timer.strictMode) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.28f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFFDE68A), modifier = Modifier.size(14.dp))
                            Text(
                                text = s.strictModeActive,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFDE68A)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Circular Progress Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(270.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                    // Track
                    drawArc(
                        color = Color.White.copy(alpha = 0.14f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress Arc
                    drawArc(
                        color = ringAccent,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSecondsMmSs(displaySecs),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 56.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (isStopwatch) s.stopwatchMode else s.countdownMode,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!timer.hasStarted) {
                    Button(
                        onClick = onStartOrResume,
                        colors = ButtonDefaults.buttonColors(containerColor = ringAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .width(170.dp)
                            .testTag("timer_start_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.start, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                } else if (timer.isRunning) {
                    if (!(timer.strictMode && timer.phase == TimerPhase.FOCUS)) {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.height(52.dp).testTag("timer_pause_button")
                        ) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.pause, style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (timer.strictMode && timer.phase == TimerPhase.FOCUS) {
                                showStrictExitWarning = true
                            } else {
                                onStop()
                            }
                        },
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(52.dp).testTag("timer_stop_button")
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.stop, style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    // Paused state
                    Button(
                        onClick = onStartOrResume,
                        colors = ButtonDefaults.buttonColors(containerColor = ringAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(52.dp).testTag("timer_resume_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.resume, style = MaterialTheme.typography.titleSmall)
                    }

                    OutlinedButton(
                        onClick = onStop,
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.stop, style = MaterialTheme.typography.titleSmall)
                    }
                }

                if (timer.phase != TimerPhase.FOCUS) {
                    OutlinedButton(
                        onClick = onSkipBreak,
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.FastForward, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.skipBreak, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Toolbar: Timer Mode, Strict Mode, White Noise Mixer
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimerToolbarItem(
                        icon = if (timer.countMode == TimerCountMode.COUNTDOWN) Icons.Default.Timer else Icons.Default.AvTimer,
                        label = if (timer.countMode == TimerCountMode.COUNTDOWN) s.countdownMode else s.stopwatchMode,
                        active = true,
                        onClick = {
                            val next = if (timer.countMode == TimerCountMode.COUNTDOWN) {
                                TimerCountMode.STOPWATCH
                            } else {
                                TimerCountMode.COUNTDOWN
                            }
                            onSetCountMode(next)
                        },
                        modifier = Modifier.testTag("timer_mode_toggle")
                    )

                    TimerToolbarItem(
                        icon = if (timer.strictMode) Icons.Default.Lock else Icons.Default.LockOpen,
                        label = s.strictMode,
                        active = timer.strictMode,
                        onClick = onToggleStrictMode,
                        modifier = Modifier.testTag("timer_strict_toggle")
                    )

                    TimerToolbarItem(
                        icon = Icons.Default.GraphicEq,
                        label = if (settings.whiteNoise == WhiteNoiseType.NONE) {
                            s.whiteNoise
                        } else {
                            settings.whiteNoise.label(s)
                        },
                        active = settings.whiteNoise != WhiteNoiseType.NONE,
                        onClick = { showNoiseMixer = true },
                        modifier = Modifier.testTag("timer_noise_button")
                    )
                }
            }
        }
    }

    // Strict Mode Exit Warning
    if (showStrictExitWarning) {
        AlertDialog(
            onDismissRequest = { showStrictExitWarning = false },
            title = { Text(s.strictModeWarningTitle) },
            text = { Text(s.strictModeWarningBody) },
            confirmButton = {
                TextButton(onClick = { showStrictExitWarning = false }) {
                    Text(s.stayFocused, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStrictExitWarning = false
                        onStop()
                        onCollapse()
                    }
                ) { Text(s.abandonAnyway, color = CoralPrimary) }
            }
        )
    }

    // Task Switcher Sheet
    if (showTaskPicker) {
        ModalBottomSheet(onDismissRequest = { showTaskPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = s.selectTaskToFocus,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (timer.activeTaskId == null) CoralPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectTask(null)
                            showTaskPicker = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.freeFocus, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (timer.activeTaskId == null) Icon(Icons.Default.Check, null, tint = CoralPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val incompleteTasks = uiState.data.tasks.filter { !it.isCompleted }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(incompleteTasks, key = { it.id }) { t ->
                        val selected = timer.activeTaskId == t.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) CoralPrimary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectTask(t.id)
                                    showTaskPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TomatoIcon(filled = true, size = 16.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = t.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${t.completedPomodoros}/${t.estimatedPomodoros}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // White Noise Mixer Sheet
    if (showNoiseMixer) {
        ModalBottomSheet(onDismissRequest = { showNoiseMixer = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = CoralPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = s.whiteNoise,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Volume Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(10.dp))
                    Slider(
                        value = settings.whiteNoiseVolume,
                        onValueChange = onSetWhiteNoiseVolume,
                        colors = SliderDefaults.colors(thumbColor = CoralPrimary, activeTrackColor = CoralPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(settings.whiteNoiseVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        modifier = Modifier.width(42.dp)
                    )
                }

                // White Noise Grid
                val options = WhiteNoiseType.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { noise ->
                                val selected = settings.whiteNoise == noise
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (selected) BorderStroke(1.5.dp, CoralPrimary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSetWhiteNoise(noise) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = noise.label(s),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) CoralPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TimerToolbarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else Color.White.copy(alpha = 0.65f),
            maxLines = 1
        )
    }
}
