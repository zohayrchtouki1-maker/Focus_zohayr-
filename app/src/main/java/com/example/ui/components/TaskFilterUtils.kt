package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.DateUtils
import com.example.model.AppSettings
import com.example.model.SmartListType
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskSortOption
import com.example.ui.theme.CoralPrimary
import com.example.ui.theme.ForestEmerald
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityNoneColor
import com.example.viewmodel.NavDestination

fun parseHexColor(hex: String, fallback: Color = CoralPrimary): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

fun TaskPriority.color(): Color = when (this) {
    TaskPriority.HIGH -> PriorityHighColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.LOW -> PriorityLowColor
    TaskPriority.NONE -> PriorityNoneColor
}

object TaskFilterUtils {
    fun matchesSmartList(task: TaskItem, type: SmartListType, today: Long): Boolean {
        return when (type) {
            SmartListType.TODAY -> !task.isSomeday && task.dueDateEpochDay != null && task.dueDateEpochDay <= today
            SmartListType.TOMORROW -> !task.isSomeday && task.dueDateEpochDay == today + 1
            SmartListType.UPCOMING -> !task.isSomeday && task.dueDateEpochDay != null && task.dueDateEpochDay > today
            SmartListType.SOMEDAY -> task.isSomeday
            SmartListType.EVENTS -> task.isEvent
            SmartListType.INBOX -> true
            SmartListType.COMPLETED -> task.isCompleted
        }
    }

    fun filterTasks(
        tasks: List<TaskItem>,
        destination: NavDestination,
        sortOption: TaskSortOption
    ): Pair<List<TaskItem>, List<TaskItem>> {
        val today = DateUtils.todayEpochDay()
        val matched = when (destination) {
            is NavDestination.SmartList -> {
                if (destination.type == SmartListType.COMPLETED) {
                    tasks.filter { it.isCompleted }
                } else {
                    tasks.filter { matchesSmartList(it, destination.type, today) }
                }
            }
            is NavDestination.Project -> tasks.filter { it.projectId == destination.projectId }
            is NavDestination.Tag -> tasks.filter { it.tagIds.contains(destination.tagId) }
        }

        val comparator = when (sortOption) {
            TaskSortOption.PRIORITY -> compareByDescending<TaskItem> { it.priority.level }.thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
            TaskSortOption.DUE_DATE -> compareBy<TaskItem> { it.dueDateEpochDay ?: Long.MAX_VALUE }.thenByDescending { it.priority.level }
            TaskSortOption.POMODORO_ESTIMATE -> compareByDescending<TaskItem> { it.estimatedPomodoros }
            TaskSortOption.ALPHABETICAL -> compareBy<TaskItem> { it.title.lowercase() }
        }

        val active = if (destination is NavDestination.SmartList && destination.type == SmartListType.COMPLETED) {
            emptyList()
        } else {
            matched.filter { !it.isCompleted }.sortedWith(comparator)
        }
        val completed = matched.filter { it.isCompleted }.sortedByDescending { it.completedAtEpochMillis ?: it.createdAtEpochMillis }
        return active to completed
    }

    fun smartListSummary(
        tasks: List<TaskItem>,
        type: SmartListType,
        settings: AppSettings
    ): Pair<Int, Int> {
        val today = DateUtils.todayEpochDay()
        val matching = if (type == SmartListType.COMPLETED) {
            tasks.filter { it.isCompleted }
        } else {
            tasks.filter { !it.isCompleted && matchesSmartList(it, type, today) }
        }
        val estMins = matching.sumOf { task ->
            val rem = (task.estimatedPomodoros - task.completedPomodoros).coerceAtLeast(0)
            rem * (task.customPomodoroMinutes ?: settings.pomodoroMinutes)
        }
        return matching.size to estMins
    }
}

@Composable
fun TomatoIcon(
    filled: Boolean,
    size: Dp = 15.dp,
    tint: Color = CoralPrimary,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.24f
        val bodyHeight = h * 0.72f
        val bodyWidth = w * 0.86f
        val bodyLeft = (w - bodyWidth) / 2f

        if (filled) {
            drawOval(
                color = tint,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
            // Tiny highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = w * 0.11f,
                center = Offset(w * 0.36f, h * 0.48f)
            )
        } else {
            drawOval(
                color = tint.copy(alpha = 0.55f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                style = Stroke(width = w * 0.1f)
            )
        }

        // Green calyx/leaf
        val leafPath = Path().apply {
            moveTo(w * 0.5f, bodyTop + h * 0.04f)
            quadraticTo(w * 0.30f, h * 0.02f, w * 0.26f, h * 0.20f)
            quadraticTo(w * 0.44f, h * 0.24f, w * 0.5f, bodyTop + h * 0.04f)
            quadraticTo(w * 0.68f, h * 0.02f, w * 0.74f, h * 0.20f)
            quadraticTo(w * 0.56f, h * 0.24f, w * 0.5f, bodyTop + h * 0.04f)
            close()
        }
        drawPath(
            path = leafPath,
            color = if (filled) ForestEmerald else ForestEmerald.copy(alpha = 0.6f)
        )
    }
}
