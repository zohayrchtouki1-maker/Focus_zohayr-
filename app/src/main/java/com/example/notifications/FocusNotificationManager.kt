package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DateUtils
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.TaskItem
import com.example.model.TimerPhase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

sealed class ExternalNotificationEvent {
    object PauseTimer : ExternalNotificationEvent()
    object ResumeTimer : ExternalNotificationEvent()
    object StopTimer : ExternalNotificationEvent()
    object TimerCompletedInBackground : ExternalNotificationEvent()
    data class CompleteTaskFromNotification(val taskId: String) : ExternalNotificationEvent()
}

object FocusNotificationBus {
    private val _events = MutableSharedFlow<ExternalNotificationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ExternalNotificationEvent> = _events.asSharedFlow()

    fun emit(event: ExternalNotificationEvent) {
        _events.tryEmit(event)
    }
}

object FocusNotificationManager {
    const val CHANNEL_POMODORO_ONGOING = "pomodoro_ongoing"
    const val CHANNEL_POMODORO_COMPLETE = "pomodoro_complete"
    const val CHANNEL_POMODORO_COMPLETE_SILENT = "pomodoro_complete_silent"
    const val CHANNEL_TASK_ALARMS = "task_alarms"
    const val CHANNEL_TASK_ALARMS_SILENT = "task_alarms_silent"

    const val NOTIFICATION_ID_ONGOING_TIMER = 9001
    const val NOTIFICATION_ID_TIMER_COMPLETE = 9002
    const val EXTRA_OPEN_FULLSCREEN_TIMER = "extra_open_fullscreen_timer"
    const val CORAL_ACCENT_COLOR = 0xFFE05246.toInt()
    private const val REQUEST_CODE_TIMER_COMPLETE = 9100

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // 1. Silent Ongoing Channel (IMPORTANCE_LOW with null sound and no vibration)
        val ongoingChannel = NotificationChannel(
            CHANNEL_POMODORO_ONGOING,
            "Pomodoro Ongoing Timer / المؤقت المستمر",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent silent countdown display while Pomodoro timer runs"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 2. High-Priority Pomodoro Completion Channel (importance 5 / HIGH)
        val completeChannel = NotificationChannel(
            CHANNEL_POMODORO_COMPLETE,
            "Pomodoro Completion Alarm / تنبيه انتهاء الجلسة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings when a Pomodoro focus or break session reaches 00:00"
            setSound(alarmSoundUri, audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 400L, 200L, 500L)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val completeSilentChannel = NotificationChannel(
            CHANNEL_POMODORO_COMPLETE_SILENT,
            "Pomodoro Silent Alert / تنبيه صامت للمؤقت",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        // 3. Maximum-Priority Task Alarms Channel (importance 5 / HIGH, Public)
        val taskAlarmsChannel = NotificationChannel(
            CHANNEL_TASK_ALARMS,
            "Task Due Reminders / تذكيرات المهام",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Ringing task due-time reminders"
            setSound(alarmSoundUri, audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 500L, 250L, 600L)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val taskAlarmsSilentChannel = NotificationChannel(
            CHANNEL_TASK_ALARMS_SILENT,
            "Task Silent Reminders / تذكيرات مهام صامتة",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        nm.createNotificationChannels(
            listOf(
                ongoingChannel,
                completeChannel,
                completeSilentChannel,
                taskAlarmsChannel,
                taskAlarmsSilentChannel
            )
        )
    }

    fun showOrUpdateOngoingTimerNotification(
        context: Context,
        taskTitle: String,
        phase: TimerPhase,
        formattedTime: String,
        strictMode: Boolean,
        settings: AppSettings,
        elapsedSeconds: Int = 0,
        todayCompletedPomodoros: Int = 0,
        isRunning: Boolean = true
    ) {
        if (!settings.persistentTimerNotification) {
            cancelOngoingTimerNotification(context)
            return
        }
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return

        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val isArabic = settings.language == AppLanguage.AR
        val basePhaseLabel = when (phase) {
            TimerPhase.FOCUS -> if (isArabic) "جلسة تركيز" else "Focus Session"
            TimerPhase.SHORT_BREAK -> if (isArabic) "استراحة قصيرة" else "Short Break"
            TimerPhase.LONG_BREAK -> if (isArabic) "استراحة طويلة" else "Long Break"
        }
        val phaseLabel = if (isRunning) {
            basePhaseLabel
        } else {
            if (isArabic) "$basePhaseLabel (متوقف مؤقتًا)" else "$basePhaseLabel (Paused)"
        }

        val resolvedTaskTitle = taskTitle.trim().ifEmpty {
            if (isArabic) "جلسة تركيز" else "Focus Session"
        }
        val safeElapsed = elapsedSeconds.coerceAtLeast(0)
        val formattedElapsed = String.format(
            Locale.US,
            "%02d:%02d",
            safeElapsed / 60,
            safeElapsed % 60
        )
        val elapsedSegment = if (isArabic) "مضى: $formattedElapsed" else "Elapsed: $formattedElapsed"
        val pomodoroSegment = "🍅 ${todayCompletedPomodoros.coerceAtLeast(0)}"
        val secondaryDetailsText = "$resolvedTaskTitle • $elapsedSegment • $pomodoroSegment"
        val expandedDetailsText = "$elapsedSegment • $pomodoroSegment"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_FULLSCREEN_TIMER, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            9010,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeIntent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = if (isRunning) {
                FocusTimerReceiver.ACTION_TIMER_PAUSE
            } else {
                FocusTimerReceiver.ACTION_TIMER_RESUME
            }
        }
        val pauseOrResumePending = PendingIntent.getBroadcast(
            context,
            if (isRunning) 9011 else 9013,
            pauseOrResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TIMER_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            9012,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeLabel = if (isRunning) {
            if (isArabic) "إيقاف مؤقت" else "Pause"
        } else {
            if (isArabic) "استئناف" else "Resume"
        }
        val stopLabel = if (isArabic) "إنهاء" else "Stop"
        val canPauseOrResume = !(strictMode && phase == TimerPhase.FOCUS && isRunning)

        val collapsedView = RemoteViews(context.packageName, R.layout.notification_pomodoro_collapsed).apply {
            setTextViewText(R.id.notif_remaining_time, formattedTime)
            setTextViewText(R.id.notif_phase_badge, phaseLabel)
            setTextViewText(R.id.notif_secondary_details, secondaryDetailsText)
            setTextViewText(R.id.notif_btn_pause_resume, pauseResumeLabel)
            setTextViewText(R.id.notif_btn_stop, stopLabel)
            setViewVisibility(R.id.notif_btn_pause_resume, if (canPauseOrResume) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.notif_btn_pause_resume, pauseOrResumePending)
            setOnClickPendingIntent(R.id.notif_btn_stop, stopPending)
        }

        val expandedView = RemoteViews(context.packageName, R.layout.notification_pomodoro_expanded).apply {
            setTextViewText(R.id.notif_remaining_time, formattedTime)
            setTextViewText(R.id.notif_phase_badge, phaseLabel)
            setTextViewText(R.id.notif_task_title, resolvedTaskTitle)
            setTextViewText(R.id.notif_secondary_details, expandedDetailsText)
            setTextViewText(R.id.notif_btn_pause_resume, pauseResumeLabel)
            setTextViewText(R.id.notif_btn_stop, stopLabel)
            setViewVisibility(R.id.notif_btn_pause_resume, if (canPauseOrResume) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.notif_btn_pause_resume, pauseOrResumePending)
            setOnClickPendingIntent(R.id.notif_btn_stop, stopPending)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_POMODORO_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setColorized(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentTitle("$phaseLabel • $formattedTime")
            .setContentText(secondaryDetailsText)
            .setSubText(pomodoroSegment)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        try {
            nm.notify(NOTIFICATION_ID_ONGOING_TIMER, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelOngoingTimerNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID_ONGOING_TIMER)
    }

    fun scheduleExactTimerCompletionAlarm(
        context: Context,
        triggerAtMillis: Long,
        taskTitle: String,
        phase: TimerPhase,
        soundAndVibration: Boolean,
        isArabic: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TIMER_COMPLETE_ALARM
            putExtra(FocusTimerReceiver.EXTRA_TASK_TITLE, taskTitle)
            putExtra(FocusTimerReceiver.EXTRA_PHASE_NAME, phase.name)
            putExtra(FocusTimerReceiver.EXTRA_SOUND_ENABLED, soundAndVibration)
            putExtra(FocusTimerReceiver.EXTRA_IS_ARABIC, isArabic)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TIMER_COMPLETE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactSafe(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    fun cancelExactTimerCompletionAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TIMER_COMPLETE_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TIMER_COMPLETE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun showTimerCompletionNotification(
        context: Context,
        taskTitle: String,
        phaseName: String,
        soundAndVibration: Boolean,
        isArabic: Boolean
    ) {
        cancelOngoingTimerNotification(context)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return

        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val isFocus = phaseName == TimerPhase.FOCUS.name
        val title = if (isArabic) {
            if (isFocus) "🎉 انتهت جلسة التركيز!" else "⏰ انتهت فترة الاستراحة!"
        } else {
            if (isFocus) "🎉 Focus Session Completed!" else "⏰ Break Finished!"
        }
        val body = if (isArabic) {
            if (isFocus) "أحسنت! اكتملت الجلسة على: $taskTitle" else "حان وقت العودة للتركيز: $taskTitle"
        } else {
            if (isFocus) "Great job! Completed focus on: $taskTitle" else "Time to resume focus: $taskTitle"
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            9020,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (soundAndVibration) CHANNEL_POMODORO_COMPLETE else CHANNEL_POMODORO_COMPLETE_SILENT
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (soundAndVibration) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPending)

        if (soundAndVibration) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(uri)
            builder.setFullScreenIntent(contentPending, true)
            triggerDeviceVibration(context)
        } else {
            builder.setSilent(true)
        }

        try {
            nm.notify(NOTIFICATION_ID_TIMER_COMPLETE, builder.build())
        } catch (_: SecurityException) {
        }
    }

    // --- Task Due-Time Reminders ---

    fun computeTaskReminderEpochMillis(task: TaskItem): Long? {
        val day = task.dueDateEpochDay ?: return null
        val timeStr = task.reminderTime?.trim() ?: return null
        val parts = timeStr.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return DateUtils.millisFromEpochDay(day, hour, minute)
    }

    fun taskRequestCode(taskId: String): Int {
        return (taskId.hashCode() and 0x0FFFFFFF).coerceAtLeast(10000)
    }

    fun scheduleTaskReminder(
        context: Context,
        task: TaskItem,
        settings: AppSettings,
        overrideTriggerAtMillis: Long? = null
    ) {
        if (!settings.enableTaskReminders || task.isCompleted) {
            cancelTaskReminder(context, task.id)
            return
        }
        val triggerAt = overrideTriggerAtMillis ?: computeTaskReminderEpochMillis(task) ?: run {
            cancelTaskReminder(context, task.id)
            return
        }
        if (triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reqCode = taskRequestCode(task.id)
        val intent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TASK_REMINDER_ALARM
            putExtra(FocusTimerReceiver.EXTRA_TASK_ID, task.id)
            putExtra(FocusTimerReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(FocusTimerReceiver.EXTRA_TASK_NOTES, task.notes)
            putExtra(
                FocusTimerReceiver.EXTRA_SOUND_ENABLED,
                settings.alarmSoundAndVibration && settings.alarmEnabled
            )
            putExtra(FocusTimerReceiver.EXTRA_IS_ARABIC, settings.language == AppLanguage.AR)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactSafe(context, alarmManager, triggerAt, pendingIntent)
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reqCode = taskRequestCode(taskId)
        val intent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TASK_REMINDER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(reqCode)
    }

    fun cancelAllTaskReminders(context: Context, tasks: List<TaskItem>) {
        tasks.forEach { cancelTaskReminder(context, it.id) }
    }

    fun syncAllTaskReminders(context: Context, tasks: List<TaskItem>, settings: AppSettings) {
        if (!settings.enableTaskReminders) {
            cancelAllTaskReminders(context, tasks)
            return
        }
        tasks.forEach { task ->
            if (!task.isCompleted && task.dueDateEpochDay != null && !task.reminderTime.isNullOrBlank()) {
                scheduleTaskReminder(context, task, settings)
            } else {
                cancelTaskReminder(context, task.id)
            }
        }
    }

    fun showTaskReminderNotification(
        context: Context,
        taskId: String,
        taskTitle: String,
        taskNotes: String,
        soundAndVibration: Boolean,
        isArabic: Boolean
    ) {
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return

        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = taskRequestCode(taskId)

        val title = "⏰ تذكير بمهمة: $taskTitle"
        val body = taskNotes.trim().ifEmpty { "حان الآن وقت إنجاز هذه المهمة!" }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notifId + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TASK_COMPLETE
            putExtra(FocusTimerReceiver.EXTRA_TASK_ID, taskId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, FocusTimerReceiver::class.java).apply {
            action = FocusTimerReceiver.ACTION_TASK_SNOOZE_10M
            putExtra(FocusTimerReceiver.EXTRA_TASK_ID, taskId)
            putExtra(FocusTimerReceiver.EXTRA_TASK_TITLE, taskTitle)
            putExtra(FocusTimerReceiver.EXTRA_TASK_NOTES, taskNotes)
            putExtra(FocusTimerReceiver.EXTRA_SOUND_ENABLED, soundAndVibration)
            putExtra(FocusTimerReceiver.EXTRA_IS_ARABIC, isArabic)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notifId + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (soundAndVibration) CHANNEL_TASK_ALARMS else CHANNEL_TASK_ALARMS_SILENT
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (soundAndVibration) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(0, "تم الإنجاز / Complete", completePending)
            .addAction(0, "غفوة 10 دقائق / Snooze 10m", snoozePending)

        if (soundAndVibration) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(uri)
            builder.setFullScreenIntent(contentPending, true)
            playSystemAlarmBurst(context)
            triggerDeviceVibration(context)
        } else {
            builder.setSilent(true)
        }

        try {
            nm.notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun scheduleExactSafe(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val showAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            9099,
            showAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (_: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: Exception) {
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun playSystemAlarmBurst(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (_: Exception) {
        }
    }

    private fun triggerDeviceVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0L, 400L, 200L, 500L), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(600L)
                }
            }
        } catch (_: Exception) {
        }
    }
}
