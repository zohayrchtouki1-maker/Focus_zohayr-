package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.FocusRepository
import com.example.data.SeedData
import com.example.model.PersistedTimerState
import com.example.model.SmartListType
import com.example.model.TaskItem
import com.example.model.TaskSortOption
import com.example.model.TimerPhase
import com.example.notifications.FocusNotificationManager
import com.example.ui.components.TaskFilterUtils
import com.example.viewmodel.NavDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Focus To-Do", appName)
    }

    @Test
    fun `seed data and JSON export import roundtrip with notification settings and persisted timer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusRepository(context)
        val initial = SeedData.createInitialState().copy(
            settings = SeedData.createInitialState().settings.copy(
                enableTaskReminders = true,
                persistentTimerNotification = true,
                alarmSoundAndVibration = true,
                lockedInRecentsConfirmed = true
            ),
            persistedTimer = PersistedTimerState(
                activeTaskId = "task_1",
                phase = TimerPhase.FOCUS,
                isRunning = true,
                hasStarted = true,
                remainingSeconds = 1200,
                timerEndTimeEpochMillis = System.currentTimeMillis() + 1_200_000L
            )
        )

        assertTrue(initial.tasks.isNotEmpty())
        assertTrue(initial.projects.isNotEmpty())
        assertTrue(initial.focusRecords.isNotEmpty())

        val json = repo.exportJson(initial)
        val parsed = repo.parseBackupJson(json)
        assertNotNull(parsed)
        assertEquals(initial.tasks.size, parsed?.tasks?.size)
        assertEquals(true, parsed?.settings?.enableTaskReminders)
        assertEquals(true, parsed?.settings?.persistentTimerNotification)
        assertEquals(true, parsed?.settings?.lockedInRecentsConfirmed)
        assertEquals(1200, parsed?.persistedTimer?.remainingSeconds)
    }

    @Test
    fun `notification channels and task reminder timestamp calculation work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FocusNotificationManager.ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertNotNull(nm.getNotificationChannel(FocusNotificationManager.CHANNEL_POMODORO_ONGOING))
        assertNotNull(nm.getNotificationChannel(FocusNotificationManager.CHANNEL_POMODORO_COMPLETE))
        assertNotNull(nm.getNotificationChannel(FocusNotificationManager.CHANNEL_TASK_ALARMS))

        val tomorrow = DateUtils.todayEpochDay() + 1
        val sampleTask = TaskItem(
            title = "اختبار التذكير",
            dueDateEpochDay = tomorrow,
            reminderTime = "14:30"
        )
        val ts = FocusNotificationManager.computeTaskReminderEpochMillis(sampleTask)
        assertNotNull(ts)
        assertTrue(ts!! > System.currentTimeMillis())
    }

    @Test
    fun `smart list filtering returns today active and completed tasks`() {
        val initial = SeedData.createInitialState()
        val (activeToday, completedToday) = TaskFilterUtils.filterTasks(
            initial.tasks,
            NavDestination.SmartList(SmartListType.TODAY),
            TaskSortOption.PRIORITY
        )
        assertTrue(activeToday.isNotEmpty())
        assertTrue(completedToday.isNotEmpty())
        assertTrue(activeToday.all { !it.isCompleted })
        assertTrue(completedToday.all { it.isCompleted })
    }
}
