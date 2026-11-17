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

        val ongoingChannel = nm.getNotificationChannel(FocusNotificationManager.CHANNEL_POMODORO_ONGOING)
        assertNotNull(ongoingChannel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, ongoingChannel.importance)
        assertNotNull(nm.getNotificationChannel(FocusNotificationManager.CHANNEL_POMODORO_COMPLETE))
        assertNotNull(nm.getNotificationChannel(FocusNotificationManager.CHANNEL_TASK_ALARMS))

        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        FocusNotificationManager.showOrUpdateOngoingTimerNotification(
            context = context,
            taskTitle = "جلسة تركيز",
            phase = TimerPhase.FOCUS,
            formattedTime = "24:37",
            strictMode = false,
            settings = SeedData.createInitialState().settings,
            elapsedSeconds = 23,
            todayCompletedPomodoros = 2,
            isRunning = true
        )

        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        val posted = shadowNm.getNotification(FocusNotificationManager.NOTIFICATION_ID_ONGOING_TIMER)
        assertNotNull(posted)
        assertEquals(FocusNotificationManager.CORAL_ACCENT_COLOR, posted.color)
        assertTrue((posted.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((posted.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertEquals(2, posted.actions.size)
        assertEquals("إيقاف مؤقت", posted.actions[0].title.toString())
        assertEquals("إنهاء", posted.actions[1].title.toString())
        @Suppress("DEPRECATION")
        assertNotNull(posted.contentView)
        @Suppress("DEPRECATION")
        assertNotNull(posted.bigContentView)

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
    fun `local backup generates focus_zohayr_backup filename and supports exhaustive v3 export and restore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusRepository(context)
        val fileName = FocusRepository.generateBackupFileName()
        assertTrue(fileName.matches(Regex("^focus_zohayr_backup_\\d{4}-\\d{2}-\\d{2}\\.json$")))

        val manualTimestampedName = FocusRepository.generateManualTimestampedBackupFileName()
        assertTrue(
            manualTimestampedName.matches(
                Regex("^focus_zohayr_backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.json$")
            )
        )

        kotlinx.coroutines.runBlocking {
            val initialSeed = SeedData.createInitialState()
            val customProject = com.example.model.ProjectItem(
                id = "custom_proj_99",
                name = "مشروع التطوير الخاص",
                colorHex = "#E05246",
                sortOrder = 4,
                isDefault = false
            )
            val subtask1 = com.example.model.SubtaskItem(
                id = "sub_1",
                parentTaskId = "existing_task_1",
                title = "الخطوة الفرعية الأولى",
                isDone = true,
                sortOrder = 0
            )
            val subtask2 = com.example.model.SubtaskItem(
                id = "sub_2",
                parentTaskId = "existing_task_1",
                title = "الخطوة الفرعية الثانية",
                isDone = false,
                sortOrder = 1
            )
            val sessionRecord = com.example.model.FocusRecord(
                id = "session_1",
                taskId = "existing_task_1",
                taskTitle = "مهمة حالية",
                projectId = customProject.id,
                sessionType = "FOCUS",
                startedAtEpochMillis = System.currentTimeMillis() - 1_500_000L,
                endTimeEpochMillis = System.currentTimeMillis(),
                durationMinutes = 25,
                durationSeconds = 1500,
                wasCompleted = true,
                dateKey = "2026-11-17"
            )
            val currentState = initialSeed.copy(
                projects = initialSeed.projects + customProject,
                tasks = listOf(
                    TaskItem(
                        id = "existing_task_1",
                        title = "مهمة حالية",
                        notes = "ملاحظة تفصيلية للمهمة",
                        projectId = customProject.id,
                        dueDateEpochDay = DateUtils.todayEpochDay(),
                        subtasks = listOf(subtask1, subtask2)
                    )
                ),
                focusRecords = listOf(sessionRecord),
                settings = initialSeed.settings.copy(
                    pomodoroMinutes = 40,
                    shortBreakMinutes = 10
                )
            )
            val saveRes = repo.saveState(currentState)
            assertTrue(saveRes.isSuccess)
            assertTrue(repo.getLastAutoBackupTimeMillis() > 0L)

            val loadedState = repo.loadState()
            assertEquals(1, loadedState.tasks.size)
            assertEquals("existing_task_1", loadedState.tasks.first().id)
            assertEquals(2, loadedState.subtasks.size)
            assertEquals(5, loadedState.projects.size)
            assertEquals(1, loadedState.pomodoroSessions.size)
            assertTrue(loadedState.allPreferences.isNotEmpty())

            val summaryRes = repo.exportManualBackupWithSummary(loadedState)
            assertTrue(summaryRes.isSuccess)
            val summary = summaryRes.getOrNull()!!
            assertTrue(summary.fileName.startsWith("focus_zohayr_backup_"))
            assertEquals(5, summary.projectsCount)
            assertEquals(1, summary.tasksCount)
            assertEquals(2, summary.subtasksCount)
            assertEquals(1, summary.pomodoroSessionsCount)
            assertTrue(summary.totalPrefsCount > 0)
            assertTrue(summary.fileSizeBytes > 0L)

            val exportedJson = repo.exportJson(loadedState)
            assertTrue(exportedJson.contains("\"backupVersion\": 3"))
            assertTrue(exportedJson.contains("\"subtasks\""))
            assertTrue(exportedJson.contains("\"pomodoroSessions\""))
            assertTrue(exportedJson.contains("\"allPreferences\""))

            val parsedFromExport = repo.parseBackupJson(exportedJson)
            assertNotNull(parsedFromExport)
            assertEquals(3, parsedFromExport!!.backupVersion)
            assertEquals(5, parsedFromExport.projects.size)
            assertEquals(2, parsedFromExport.subtasks.size)
            assertEquals(1, parsedFromExport.pomodoroSessions.size)
            assertEquals(40, parsedFromExport.settings.pomodoroMinutes)

            val restoredState = repo.restoreFullStateAtomically(
                current = SeedData.createInitialState(),
                imported = parsedFromExport,
                mode = com.example.data.BackupImportMode.REPLACE
            )
            assertEquals(5, restoredState.projects.size)
            assertEquals(1, restoredState.tasks.size)
            assertEquals(2, restoredState.tasks.first().subtasks.size)
            assertEquals(1, restoredState.focusRecords.size)
            assertEquals(40, restoredState.settings.pomodoroMinutes)

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

            // Test JSON validation rejects invalid payload
            val invalidParsed = repo.parseBackupJson("{\"invalid_key\": 123}")
            assertEquals(null, invalidParsed)
        }
    }
}
