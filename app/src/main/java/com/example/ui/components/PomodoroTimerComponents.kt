package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.draw.shadow
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
    val displaySecs = if (timer.countMode == TimerCountMode.STOPWATCH && timer.phase == TimerPhase.FOCUS) {
        timer.stopwatchSeconds
    } else {
        timer.remainingSeconds
    }
    val displayMinutesNumber = ((displaySecs + 59) / 60).coerceAtLeast(0)
    val circleDisplayText = if (timer.isRunning || timer.hasStarted) {
        formatSecondsMmSs(displaySecs)
    } else {
        displayMinutesNumber.toString()
    }

    val rimColor = when {
        timer.isRunning && timer.phase == TimerPhase.FOCUS -> CoralPrimary
        timer.isRunning && timer.phase == TimerPhase.SHORT_BREAK -> ShortBreakColor
        timer.isRunning && timer.phase == TimerPhase.LONG_BREAK -> LongBreakColor
        else -> Color(0xFF4B5568)
    }

    // Bottom center: Floating circular Pomodoro timer widget (56dp) with dark night-sky gradient,
    // subtle rim glow, and crisp white countdown number (tapping opens full-screen timer).
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            rimColor.copy(alpha = 0.32f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(elevation = 10.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF23273A),
                                Color(0xFF141622),
                                Color(0xFF0D0E15)
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                rimColor.copy(alpha = 0.85f),
                                Color(0xFF2A2E42)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(onClick = onExpand)
                    .testTag("mini_timer_bar")
            ) {
                // Subtle night-sky stars/dots inside the circle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(size.width * 0.28f, size.height * 0.26f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f),
                        radius = 1.dp.toPx(),
                        center = Offset(size.width * 0.74f, size.height * 0.30f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = 1.dp.toPx(),
                        center = Offset(size.width * 0.24f, size.height * 0.70f)
                    )
                }

                Text(
                    text = circleDisplayText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (circleDisplayText.length > 2) 13.sp else 19.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // Hidden/subtle quick play-pause action badge for accessibility & testTag("mini_timer_play_pause")
            val pauseBlocked = timer.strictMode && timer.phase == TimerPhase.FOCUS && timer.isRunning
            if (timer.isRunning) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(CoralPrimary)
                        .border(1.dp, Color(0xFF121215), CircleShape)
                        .clickable(enabled = !pauseBlocked) {
                            onPause()
                        }
                        .testTag("mini_timer_play_pause")
                ) {
                    Icon(
                        imageVector = if (pauseBlocked) Icons.Default.Lock else Icons.Default.Pause,
                        contentDescription = s.pause,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
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
    onSetWhiteNoiseVolume: (Float) -> Unit,
    onOpenStrictBlockerSetup: () -> Unit = {},
    onDismissStatusBanner: () -> Unit = {}
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
        TimerPhase.FOCUS -> CoralPrimary
        TimerPhase.SHORT_BREAK -> ShortBreakColor
        TimerPhase.LONG_BREAK -> LongBreakColor
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
                    color = Color(0xFF1C1C21),
                    border = BorderStroke(1.dp, Color(0xFF26262C)),
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

                // Shield Icon Button: 🛡️ الحظر الصارم
                val blockerReady = uiState.strictBlockerPermissions.isFullyReady &&
                    (timer.strictMode || settings.strictBlockerEnabled)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (blockerReady) ForestEmerald.copy(alpha = 0.2f) else Color(0xFF1C1C21),
                    border = BorderStroke(
                        1.dp,
                        if (blockerReady) ForestEmerald.copy(alpha = 0.7f) else CoralPrimary.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenStrictBlockerSetup() }
                        .testTag("open_strict_blocker_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "🛡️ الحظر الصارم",
                            tint = if (blockerReady) ForestEmerald else CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "🛡️ الحظر الصارم",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            if (!uiState.statusBannerMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CoralPrimary.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, CoralPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissStatusBanner() }
                        .testTag("strict_blocker_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = uiState.statusBannerMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mode & Strict Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF25252B)
                ) {
                    Text(
                        text = timer.phase.label(s),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                if (timer.strictMode || settings.strictBlockerEnabled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF39C12).copy(alpha = 0.25f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenStrictBlockerSetup() }
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
                    val strokeWidth = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                    // Track
                    drawArc(
                        color = Color.White.copy(alpha = 0.12f),
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
                        color = Color(0xFF7E7E8A)
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
                        shape = CircleShape,
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25252B), contentColor = Color(0xFFF2F2F5)),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("timer_pause_button")
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
                        border = BorderStroke(1.dp, Color(0xFF26262C)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1C1C21),
                            contentColor = Color(0xFFF2F2F5)
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("timer_stop_button")
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
                        shape = CircleShape,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("timer_resume_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.resume, style = MaterialTheme.typography.titleSmall)
                    }

                    OutlinedButton(
                        onClick = onStop,
                        border = BorderStroke(1.dp, Color(0xFF26262C)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1C1C21),
                            contentColor = Color(0xFFF2F2F5)
                        ),
                        shape = CircleShape,
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
                        border = BorderStroke(1.dp, Color(0xFF26262C)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1C1C21),
                            contentColor = Color(0xFFF2F2F5)
                        ),
                        shape = CircleShape,
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
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1C1C21),
                border = BorderStroke(1.dp, Color(0xFF26262C)),
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
                        icon = Icons.Default.Security,
                        label = "🛡️ الحظر الصارم",
                        active = timer.strictMode || settings.strictBlockerEnabled,
                        onClick = onOpenStrictBlockerSetup,
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
        ModalBottomSheet(
            onDismissRequest = { showTaskPicker = false },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
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
                    color = if (timer.activeTaskId == null) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            color = if (selected) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                TomatoIcon(filled = true, size = 16.dp, tint = CoralPrimary)
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
        ModalBottomSheet(
            onDismissRequest = { showNoiseMixer = false },
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
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
                                    color = if (selected) CoralPrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
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
            tint = if (active) CoralPrimary else Color(0xFF9E9EA9),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color(0xFFF2F2F5) else Color(0xFF7E7E8A),
            maxLines = 1
        )
    }
}
