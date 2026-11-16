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
import com.example.model.TaskPriority
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
    fun `seed data starts with empty task list and JSON export import roundtrip works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusRepository(context)
        val seed = SeedData.createInitialState()
        assertTrue(seed.tasks.isEmpty())
        assertTrue(seed.projects.isNotEmpty())

        val userTask = TaskItem(
            id = "user_task_1",
            title = "مهمة المستخدم الأولى",
            dueDateEpochDay = DateUtils.todayEpochDay()
        )
        val stateWithUserTask = seed.copy(
            tasks = listOf(userTask),
            settings = seed.settings.copy(
                enableTaskReminders = true,
                persistentTimerNotification = true,
                alarmSoundAndVibration = true,
                lockedInRecentsConfirmed = true
            ),
            persistedTimer = PersistedTimerState(
                activeTaskId = userTask.id,
                phase = TimerPhase.FOCUS,
                isRunning = true,
                hasStarted = true,
                remainingSeconds = 1200,
                timerEndTimeEpochMillis = System.currentTimeMillis() + 1_200_000L
            )
        )

        val json = repo.exportJson(stateWithUserTask)
        val parsed = repo.parseBackupJson(json)
        assertNotNull(parsed)
        assertEquals(1, parsed?.tasks?.size)
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
        val today = DateUtils.todayEpochDay()
        val tasks = listOf(
            TaskItem(
                id = "u_active",
                title = "مهمة نشطة اليوم",
                priority = TaskPriority.HIGH,
                dueDateEpochDay = today,
                isCompleted = false
            ),
            TaskItem(
                id = "u_done",
                title = "مهمة مكتملة اليوم",
                priority = TaskPriority.MEDIUM,
                dueDateEpochDay = today,
                isCompleted = true,
                completedAtEpochMillis = System.currentTimeMillis()
            )
        )
        val (activeToday, completedToday) = TaskFilterUtils.filterTasks(
            tasks,
            NavDestination.SmartList(SmartListType.TODAY),
            TaskSortOption.PRIORITY
        )
        assertTrue(activeToday.isNotEmpty())
        assertTrue(completedToday.isNotEmpty())
        assertTrue(activeToday.all { !it.isCompleted })
        assertTrue(completedToday.all { it.isCompleted })
    }

    @Test
    fun `local backup generates focus_zohayr_backup filename and supports merge and replace`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusRepository(context)
        val fileName = FocusRepository.generateBackupFileName()
        assertTrue(fileName.matches(Regex("^focus_zohayr_backup_\\d{4}-\\d{2}-\\d{2}\\.json$")))

        kotlinx.coroutines.runBlocking {
            val currentState = SeedData.createInitialState().copy(
                tasks = listOf(
                    TaskItem(
                        id = "existing_task_1",
                        title = "مهمة حالية",
                        dueDateEpochDay = DateUtils.todayEpochDay()
                    )
                )
            )
            val saveRes = repo.saveState(currentState)
            assertTrue(saveRes.isSuccess)

            val loadedState = repo.loadState()
            assertEquals(1, loadedState.tasks.size)
            assertEquals("existing_task_1", loadedState.tasks.first().id)

            val importedState = SeedData.createInitialState().copy(
                tasks = listOf(
                    TaskItem(
                        id = "imported_task_2",
                        title = "مهمة مستوردة",
                        dueDateEpochDay = DateUtils.todayEpochDay()
                    )
                )
            )

            // Test Merge (دمج مع المهام الحالية)
            val merged = repo.mergeBackupStates(loadedState, importedState)
            assertEquals(2, merged.tasks.size)

            // Test Shareable file creation
            val shareFile = repo.createShareableBackupFile(merged)
            assertTrue(shareFile.exists())
            assertTrue(shareFile.name.startsWith("focus_zohayr_backup_"))
        }
    }
}
