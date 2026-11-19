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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DateUtils
import com.example.model.AppLanguage
import com.example.model.AppSettings
import com.example.model.ManualBackupExportSummary
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
    const val CHANNEL_POMODORO_COMPLETE = "pomodoro_complete_v2"
    const val CHANNEL_POMODORO_COMPLETE_SILENT = "pomodoro_complete_v2"
    const val CHANNEL_TASK_ALARMS = "task_alarms_v2"
    const val CHANNEL_TASK_ALARMS_SILENT = "task_alarms_v2"
    const val CHANNEL_AUTO_BACKUP = "auto_backup_silent_v3"
    const val CHANNEL_MANUAL_BACKUP = "manual_backup_status_v2"
    const val CHANNEL_APP_BLOCKER = "app_blocker_alerts_v2"

    const val NOTIFICATION_ID_ONGOING_TIMER = 9001
    const val NOTIFICATION_ID_TIMER_COMPLETE = 9002
    const val NOTIFICATION_ID_MANUAL_BACKUP = 9060
    const val NOTIFICATION_ID_AUTO_BACKUP = 9065
    const val NOTIFICATION_ID_APP_BLOCKER = 9070
    const val EXTRA_OPEN_FULLSCREEN_TIMER = "extra_open_fullscreen_timer"
    const val EXTRA_OPEN_SECTION = "extra_open_section"
    const val CORAL_ACCENT_COLOR = 0xFFE05246.toInt()
    private const val REQUEST_CODE_TIMER_COMPLETE = 9100

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notifSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: alarmSoundUri

        val alarmAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val notifAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Clean up legacy channels on existing installs so Android applies updated channel importance/sound settings
        listOf(
            "pomodoro_complete",
            "pomodoro_complete_silent",
            "task_alarms",
            "task_alarms_silent",
            "auto_backup_status",
            "auto_backup_status_v2"
        ).forEach { legacyId ->
            try {
                if (nm.getNotificationChannel(legacyId) != null) {
                    nm.deleteNotificationChannel(legacyId)
                }
            } catch (_: Exception) {
            }
        }

        // Ensure ongoing channel is IMPORTANCE_DEFAULT if previously created with IMPORTANCE_LOW
        try {
            val existingOngoing = nm.getNotificationChannel(CHANNEL_POMODORO_ONGOING)
            if (existingOngoing != null && existingOngoing.importance != NotificationManager.IMPORTANCE_DEFAULT) {
                nm.deleteNotificationChannel(CHANNEL_POMODORO_ONGOING)
            }
        } catch (_: Exception) {
        }

        // 1. Top-of-Shade Silent Ongoing Channel (IMPORTANCE_DEFAULT with null sound and zero vibration)
        val ongoingChannel = NotificationChannel(
            CHANNEL_POMODORO_ONGOING,
            "Pomodoro Ongoing Timer / المؤقت المستمر",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Persistent silent countdown display while Pomodoro timer runs"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 2. High-Priority Pomodoro Completion Channel (IMPORTANCE_HIGH + Sound + Vibration + Heads-Up + Badge)
        val completeChannel = NotificationChannel(
            CHANNEL_POMODORO_COMPLETE,
            "Pomodoro Completion Alerts / تنبيهات انتهاء الجلسة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Floating heads-up alerts with sound when a Pomodoro focus or break session completes"
            setSound(alarmSoundUri, alarmAudioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 400L, 200L, 500L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 3. High-Priority Task Alarms Channel (IMPORTANCE_HIGH + Sound + Vibration + Heads-Up + Badge)
        val taskAlarmsChannel = NotificationChannel(
            CHANNEL_TASK_ALARMS,
            "Task Due Reminders / تذكيرات المهام",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Floating heads-up reminders with sound for due tasks"
            setSound(alarmSoundUri, alarmAudioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 500L, 250L, 600L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 4. Silent Background Auto-Backup Channel (IMPORTANCE_MIN, no sound, no vibration, no heads-up)
        val autoBackupChannel = NotificationChannel(
            CHANNEL_AUTO_BACKUP,
            "Silent Auto-Backup / النسخ الاحتياطي التلقائي الصامت",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Silent background status for automatic backup without pop-ups, sound, or vibration"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }

        // 5. High-Priority Manual Backup Alerts Channel (IMPORTANCE_HIGH + Sound + Vibration + Heads-Up + Badge)
        val manualBackupChannel = NotificationChannel(
            CHANNEL_MANUAL_BACKUP,
            "Manual Backup Alerts / تنبيهات النسخ اليدوي",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Floating heads-up notifications with sound when manual backup completes"
            setSound(notifSoundUri, notifAudioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 220L, 120L, 220L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 6. High-Priority Strict App Blocker Channel (IMPORTANCE_HIGH + Sound + Vibration + Heads-Up + Badge)
        val blockerChannel = NotificationChannel(
            CHANNEL_APP_BLOCKER,
            "Strict App Blocker Alerts / تنبيهات الحظر الصارم",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Floating heads-up alerts with sound when a non-whitelisted app is blocked"
            setSound(notifSoundUri, notifAudioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 300L, 150L, 300L)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannels(
            listOf(
                ongoingChannel,
                completeChannel,
                taskAlarmsChannel,
                autoBackupChannel,
                manualBackupChannel,
                blockerChannel
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
        val elapsedSegment = if (isArabic) "مضى $formattedElapsed" else "Elapsed $formattedElapsed"
        val secondaryDetailsText = "$elapsedSegment • $resolvedTaskTitle"

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
            setTextViewText(R.id.notif_secondary_details, secondaryDetailsText)
        }

        val expandedView = RemoteViews(context.packageName, R.layout.notification_pomodoro_expanded).apply {
            setTextViewText(R.id.notif_remaining_time, formattedTime)
            setTextViewText(R.id.notif_secondary_details, secondaryDetailsText)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_POMODORO_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentTitle(formattedTime)
            .setContentText(secondaryDetailsText)
            .setSubText(phaseLabel)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (canPauseOrResume) {
            builder.addAction(0, pauseResumeLabel, pauseOrResumePending)
        }
        builder.addAction(0, stopLabel, stopPending)

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

    private fun buildOpenSectionPendingIntent(
        context: Context,
        requestCode: Int,
        section: UnreadSection,
        openFullScreenTimer: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SECTION, section.name)
            if (openFullScreenTimer) {
                putExtra(EXTRA_OPEN_FULLSCREEN_TIMER, true)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showTimerCompletionNotification(
        context: Context,
        taskTitle: String,
        phaseName: String,
        soundAndVibration: Boolean,
        isArabic: Boolean
    ) {
        cancelOngoingTimerNotification(context)
        val badges = UnreadBadgeTracker.recordPomodoroCompleted(context)
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

        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = 9020,
            section = UnreadSection.POMODORO,
            openFullScreenTimer = true
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_POMODORO_COMPLETE)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setFullScreenIntent(contentPending, true)
            .setOnlyAlertOnce(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(badges.pomodoroUnread.coerceAtLeast(1))
            .setAutoCancel(true)
            .setContentIntent(contentPending)

        if (soundAndVibration) {
            triggerDeviceVibration(context)
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
        UnreadBadgeTracker.recordTaskReminder(context, taskId)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return

        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = taskRequestCode(taskId)

        val title = if (isArabic) "⏰ تذكير بمهمة: $taskTitle" else "⏰ Task Reminder: $taskTitle"
        val body = taskNotes.trim().ifEmpty {
            if (isArabic) "حان الآن وقت إنجاز هذه المهمة!" else "It's time to work on this task!"
        }

        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = notifId + 1,
            section = UnreadSection.TASKS
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

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_TASK_ALARMS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setFullScreenIntent(contentPending, true)
            .setOnlyAlertOnce(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(0, "تم الإنجاز / Complete", completePending)
            .addAction(0, "غفوة 10 دقائق / Snooze 10m", snoozePending)

        if (soundAndVibration) {
            playSystemAlarmBurst(context)
            triggerDeviceVibration(context)
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

    fun showBackupWarningNotification(
        context: Context,
        step: Int,
        totalSteps: Int = 3,
        errorReason: String = ""
    ) {
        val badges = UnreadBadgeTracker.recordBackupCompleted(context)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val title = "⚠️ تحذير هام ($step/$totalSteps): فشل حفظ النسخة الاحتياطية"
        val body = buildString {
            append("تعذر حفظ النسخة الاحتياطية اليدوية في مجلد Documents/Focus_Zohayr.")
            if (errorReason.isNotBlank()) {
                append(" ($errorReason)")
            }
        }
        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = 9055 + step,
            section = UnreadSection.SETTINGS_BACKUP
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, CHANNEL_TASK_ALARMS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setFullScreenIntent(contentPending, true)
            .setOnlyAlertOnce(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(badges.settingsBackupUnread.coerceAtLeast(1))
            .setContentIntent(contentPending)
            .setAutoCancel(true)

        triggerDeviceVibration(context)

        try {
            nm.notify(9050 + step, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showManualBackupSuccessNotification(
        context: Context,
        summary: ManualBackupExportSummary
    ) {
        val badges = UnreadBadgeTracker.recordBackupCompleted(context)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val title = "✅ تم النسخ الاحتياطي الشامل بنجاح!"
        val bigText = buildString {
            appendLine("📁 المشاريع: ${summary.projectsCount}")
            appendLine("📝 المهام الرئيسية: ${summary.tasksCount}")
            appendLine("☑️ المهام الفرعية: ${summary.subtasksCount}")
            appendLine("🍅 جلسات البومودورو والإحصائيات: ${summary.pomodoroSessionsCount}")
            appendLine("⚙️ الإعدادات والتفضيلات: ${summary.totalPrefsCount} إعداد")
            if (summary.updatedTimestamp.isNotBlank()) {
                appendLine("🕒 وقت التحديث: ${summary.updatedTimestamp}")
            }
            append("💾 حجم الملف: ${summary.formattedFileSize} (${summary.fileName})")
        }

        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = 9061,
            section = UnreadSection.SETTINGS_BACKUP
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_MANUAL_BACKUP)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText("💾 ${summary.formattedFileSize} • 📝 ${summary.tasksCount} مهام • 📁 ${summary.projectsCount} مشاريع")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setFullScreenIntent(contentPending, true)
            .setOnlyAlertOnce(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(badges.settingsBackupUnread.coerceAtLeast(1))
            .setContentIntent(contentPending)
            .setAutoCancel(true)

        triggerDeviceVibration(context)

        try {
            nm.notify(NOTIFICATION_ID_MANUAL_BACKUP, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showAutoBackupSuccessNotification(
        context: Context,
        summary: ManualBackupExportSummary
    ) {
        UnreadBadgeTracker.recordBackupCompleted(context)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val title = "🔄 تم النسخ الاحتياطي التلقائي بنجاح"
        val bigText = buildString {
            appendLine("✅ اكتمل التحديث التلقائي للملف: ${summary.fileName}")
            if (summary.updatedTimestamp.isNotBlank()) {
                appendLine("🕒 وقت النسخ: ${summary.updatedTimestamp}")
            }
            appendLine("📝 المهام: ${summary.tasksCount} • ☑️ الفرعية: ${summary.subtasksCount}")
            appendLine("📁 المشاريع: ${summary.projectsCount} • 🍅 الجلسات: ${summary.pomodoroSessionsCount}")
            appendLine("⚙️ الإعدادات المحفوظة: ${summary.totalPrefsCount}")
            append("💾 حجم الملف: ${summary.formattedFileSize}")
        }

        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = 9066,
            section = UnreadSection.SETTINGS_BACKUP
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_AUTO_BACKUP)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText("🕒 ${summary.updatedTimestamp} • 📝 ${summary.tasksCount} مهام • 💾 ${summary.formattedFileSize}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setSound(null)
            .setDefaults(0)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPending)
            .setAutoCancel(true)

        try {
            nm.notify(NOTIFICATION_ID_AUTO_BACKUP, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showAppBlockerTriggeredNotification(
        context: Context,
        blockedPackageName: String,
        appLabel: String = blockedPackageName,
        isArabic: Boolean = true
    ) {
        val badges = UnreadBadgeTracker.recordBlockerTriggered(context)
        if (!SystemPermissionsHelper.isNotificationPermissionGranted(context)) return
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val resolvedLabel = appLabel.trim().ifBlank { blockedPackageName }
        val title = if (isArabic) {
            "🛡️ الحظر الصارم: تم إغلاق $resolvedLabel"
        } else {
            "🛡️ Strict Blocker: Blocked $resolvedLabel"
        }
        val body = if (isArabic) {
            "ممنوع فتح التطبيقات غير المسموحة أثناء جلسة التركيز النشطة."
        } else {
            "Non-whitelisted apps cannot be opened during an active strict focus session."
        }

        val contentPending = buildOpenSectionPendingIntent(
            context = context,
            requestCode = 9071,
            section = UnreadSection.BLOCKER
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_APP_BLOCKER)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setColor(CORAL_ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setFullScreenIntent(contentPending, true)
            .setOnlyAlertOnce(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(badges.blockerUnread.coerceAtLeast(1))
            .setContentIntent(contentPending)
            .setAutoCancel(true)

        triggerDeviceVibration(context)

        try {
            nm.notify(NOTIFICATION_ID_APP_BLOCKER, builder.build())
        } catch (_: SecurityException) {
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
