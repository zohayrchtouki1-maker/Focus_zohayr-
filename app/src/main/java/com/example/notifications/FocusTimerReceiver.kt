package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.FocusRepository
import com.example.model.TimerPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusTimerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TIMER_PAUSE = "com.example.focustodo.ACTION_TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = "com.example.focustodo.ACTION_TIMER_RESUME"
        const val ACTION_TIMER_STOP = "com.example.focustodo.ACTION_TIMER_STOP"
        const val ACTION_TIMER_COMPLETE_ALARM = "com.example.focustodo.ACTION_TIMER_COMPLETE_ALARM"
        const val ACTION_TASK_REMINDER_ALARM = "com.example.focustodo.ACTION_TASK_REMINDER_ALARM"
        const val ACTION_TASK_COMPLETE = "com.example.focustodo.ACTION_TASK_COMPLETE"
        const val ACTION_TASK_SNOOZE_10M = "com.example.focustodo.ACTION_TASK_SNOOZE_10M"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_NOTES = "extra_task_notes"
        const val EXTRA_PHASE_NAME = "extra_phase_name"
        const val EXTRA_SOUND_ENABLED = "extra_sound_enabled"
        const val EXTRA_IS_ARABIC = "extra_is_arabic"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext

        when (action) {
            ACTION_TIMER_PAUSE -> {
                FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
                FocusNotificationBus.emit(ExternalNotificationEvent.PauseTimer)
            }

            ACTION_TIMER_RESUME -> {
                FocusNotificationBus.emit(ExternalNotificationEvent.ResumeTimer)
            }

            ACTION_TIMER_STOP -> {
                FocusNotificationManager.cancelOngoingTimerNotification(appContext)
                FocusNotificationManager.cancelExactTimerCompletionAlarm(appContext)
                FocusNotificationBus.emit(ExternalNotificationEvent.StopTimer)
            }

            ACTION_TIMER_COMPLETE_ALARM -> {
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "تركيز حر"
                val phaseName = intent.getStringExtra(EXTRA_PHASE_NAME) ?: TimerPhase.FOCUS.name
                val soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
                val isArabic = intent.getBooleanExtra(EXTRA_IS_ARABIC, true)

                FocusNotificationManager.showTimerCompletionNotification(
                    context = appContext,
                    taskTitle = taskTitle,
                    phaseName = phaseName,
                    soundAndVibration = soundEnabled,
                    isArabic = isArabic
                )
                FocusNotificationBus.emit(ExternalNotificationEvent.TimerCompletedInBackground)
            }

            ACTION_TASK_REMINDER_ALARM -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: ""
                val taskNotes = intent.getStringExtra(EXTRA_TASK_NOTES) ?: ""
                val soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
                val isArabic = intent.getBooleanExtra(EXTRA_IS_ARABIC, true)

                FocusNotificationManager.showTaskReminderNotification(
                    context = appContext,
                    taskId = taskId,
                    taskTitle = taskTitle,
                    taskNotes = taskNotes,
                    soundAndVibration = soundEnabled,
                    isArabic = isArabic
                )
            }

            ACTION_TASK_COMPLETE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                FocusNotificationManager.cancelTaskReminder(appContext, taskId)
                FocusNotificationBus.emit(ExternalNotificationEvent.CompleteTaskFromNotification(taskId))

                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val repo = FocusRepository(appContext)
                        val state = repo.loadState()
                        val updatedTasks = state.tasks.map { t ->
                            if (t.id == taskId && !t.isCompleted) {
                                t.copy(
                                    isCompleted = true,
                                    completedAtEpochMillis = System.currentTimeMillis()
                                )
                            } else t
                        }
                        repo.saveState(state.copy(tasks = updatedTasks))
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_TASK_SNOOZE_10M -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: ""
                val taskNotes = intent.getStringExtra(EXTRA_TASK_NOTES) ?: ""
                val soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
                val isArabic = intent.getBooleanExtra(EXTRA_IS_ARABIC, true)

                val notifId = FocusNotificationManager.taskRequestCode(taskId)
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(notifId)

                val triggerAt = System.currentTimeMillis() + 10 * 60_000L
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val alarmIntent = Intent(appContext, FocusTimerReceiver::class.java).apply {
                    this.action = ACTION_TASK_REMINDER_ALARM
                    putExtra(EXTRA_TASK_ID, taskId)
                    putExtra(EXTRA_TASK_TITLE, taskTitle)
                    putExtra(EXTRA_TASK_NOTES, taskNotes)
                    putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                    putExtra(EXTRA_IS_ARABIC, isArabic)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    notifId,
                    alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                FocusNotificationManager.scheduleExactSafe(
                    context = appContext,
                    alarmManager = alarmManager,
                    triggerAtMillis = triggerAt,
                    pendingIntent = pendingIntent
                )
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val repo = FocusRepository(appContext)
                        val state = repo.loadState()
                        FocusNotificationManager.ensureChannelsCreated(appContext)
                        FocusNotificationManager.syncAllTaskReminders(
                            context = appContext,
                            tasks = state.tasks,
                            settings = state.settings
                        )
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
