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
        assertEquals("FocusTomato", appName)
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
    fun `local backup generates FocusTomato_backup filename and supports exhaustive v4 export and restore`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FocusRepository.resetStaticCacheForTests()
        val repo = FocusRepository(context)
        assertEquals("FocusTomato", FocusRepository.BACKUP_FOLDER_NAME)
        assertEquals("FocusTomato_backup.json", FocusRepository.MASTER_BACKUP_FILE_NAME)
        assertEquals("FocusTomato_backup.json", FocusRepository.AUTO_BACKUP_FILE_NAME)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

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
            val publicDocs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val backupDir = java.io.File(publicDocs, FocusRepository.BACKUP_FOLDER_NAME)
            backupDir.mkdirs()
            val masterFile = java.io.File(backupDir, "FocusTomato_backup.json")
            masterFile.writeText("{\"old\":true}")
            java.io.File(backupDir, "FocusTomato_backup (1).json").writeText("{}")
            java.io.File(backupDir, "FocusTomato_backup (2).json").writeText("{}")

            val saveRes = repo.saveState(currentState)
            assertTrue(saveRes.isSuccess)
            // Trigger second auto-backup to verify in-place overwrite with zero duplicates
            val secondSaveRes = repo.saveState(currentState)
            assertTrue(secondSaveRes.isSuccess)

            val filesInBackupDir = backupDir.listFiles()?.map { it.name }.orEmpty()
            assertEquals(listOf("FocusTomato_backup.json"), filesInBackupDir)
            assertTrue(masterFile.readText().contains("existing_task_1"))

            assertTrue(repo.getLastAutoBackupTimeMillis() > 0L)
            val autoBackupChannel = nm.getNotificationChannel(FocusNotificationManager.CHANNEL_AUTO_BACKUP)
            assertNotNull(autoBackupChannel)
            assertEquals(NotificationManager.IMPORTANCE_MIN, autoBackupChannel.importance)
            assertEquals(null, autoBackupChannel.sound)
            assertEquals(false, autoBackupChannel.shouldVibrate())
            val autoBackupNotif = org.robolectric.Shadows.shadowOf(nm)
                .getNotification(FocusNotificationManager.NOTIFICATION_ID_AUTO_BACKUP)
            assertNotNull(autoBackupNotif)
            assertEquals(FocusNotificationManager.CHANNEL_AUTO_BACKUP, autoBackupNotif.channelId)
            assertEquals(null, autoBackupNotif.fullScreenIntent)
            assertEquals(null, autoBackupNotif.sound)

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
            assertEquals("FocusTomato_backup.json", summary.fileName)
            assertEquals(5, summary.projectsCount)
            assertEquals(1, summary.tasksCount)
            assertEquals(2, summary.subtasksCount)
            assertEquals(1, summary.pomodoroSessionsCount)
            assertTrue(summary.totalPrefsCount > 0)
            assertTrue(summary.fileSizeBytes > 0L)
            assertTrue(summary.updatedTimestamp.isNotBlank())

            val exportedJson = repo.exportJson(loadedState)
            assertTrue(exportedJson.contains("\"backupVersion\": 4"))
            assertTrue(exportedJson.contains("\"subtasks\""))
            assertTrue(exportedJson.contains("\"pomodoroSessions\""))
            assertTrue(exportedJson.contains("\"allPreferences\""))

            val parsedFromExport = repo.parseBackupJson(exportedJson)
            assertNotNull(parsedFromExport)
            assertEquals(4, parsedFromExport!!.backupVersion)
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
            assertEquals("FocusTomato_backup.json", shareFile.name)

            // Test JSON validation rejects invalid payload
            val invalidParsed = repo.parseBackupJson("{\"invalid_key\": 123}")
            assertEquals(null, invalidParsed)
        }
    }

    @Test
    fun `empty state guard never overwrites existing FocusTomato backup and prompts auto-restore on fresh install`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FocusRepository.resetStaticCacheForTests()
        val repo = FocusRepository(context)

        kotlinx.coroutines.runBlocking {
            val today = DateUtils.todayEpochDay()
            val existingBackup = SeedData.createInitialState().copy(
                tasks = listOf(
                    TaskItem(id = "old_t1", title = "Old Backup Task 1", dueDateEpochDay = today),
                    TaskItem(id = "old_t2", title = "Old Backup Task 2", dueDateEpochDay = today)
                ),
                focusRecords = listOf(
                    com.example.model.FocusRecord(
                        id = "old_s1",
                        taskId = "old_t1",
                        taskTitle = "Old Backup Task 1",
                        startedAtEpochMillis = DateUtils.millisFromEpochDay(today, 9, 0),
                        durationMinutes = 30
                    )
                ),
                settings = com.example.model.AppSettings(
                    pomodoroMinutes = 45,
                    accentColorHex = "#34A853",
                    whitelistedPackages = listOf("com.whatsapp"),
                    settingsUpdatedAt = 5_000L
                )
            )

            // Seed pre-existing backup on public storage (/Documents/FocusTomato/FocusTomato_backup.json)
            val publicFile = repo.getPublicBackupFile()
            publicFile.parentFile?.mkdirs()
            publicFile.writeText(repo.exportJson(existingBackup), Charsets.UTF_8)

            // Simulate fresh install (clear private app storage & prefs)
            java.io.File(context.filesDir, "focus_todo_state.json").delete()
            context.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            FocusRepository.resetStaticCacheForTests()

            val freshLoaded = repo.loadState()
            assertTrue(repo.isAppStateEffectivelyEmpty(freshLoaded))

            // 1. Existing backup file MUST still have all old tasks & sessions (never overwritten!)
            val stillOnDisk = repo.readExistingExternalBackup()
            assertNotNull(stillOnDisk)
            assertEquals(2, stillOnDisk!!.tasks.size)
            assertEquals(1, stillOnDisk.focusRecords.size)

            // 2. Fresh-install check pauses auto-backup and returns prompt with exact counts
            val prompt = repo.checkFreshInstallExistingBackup(freshLoaded)
            assertNotNull(prompt)
            assertEquals(2, prompt!!.tasksCount)
            assertEquals(1, prompt.sessionsCount)
            assertTrue(repo.isAutoBackupPaused())

            // 3. Attempting saveState with empty state while paused MUST NOT overwrite existing backup
            repo.saveState(freshLoaded)
            val afterAttemptedEmptySave = repo.readExistingExternalBackup()
            assertNotNull(afterAttemptedEmptySave)
            assertEquals(2, afterAttemptedEmptySave!!.tasks.size)

            // 4. Confirming Restore All restores everything & unpauses auto-backup
            val restored = repo.restoreFullStateAtomically(
                current = freshLoaded,
                imported = prompt.backupState,
                mode = com.example.data.BackupImportMode.REPLACE
            )
            assertEquals(false, repo.isAutoBackupPaused())
            assertEquals(2, restored.tasks.size)
            assertEquals(1, restored.focusRecords.size)
            assertEquals(45, restored.settings.pomodoroMinutes)
            assertEquals("#34A853", restored.settings.accentColorHex)
            assertTrue(restored.settings.whitelistedPackages.contains("com.whatsapp"))
        }
    }

    @Test
    fun `full-state smart merge unions collections, resolves settings by timestamp, preserves streaks, and honors soft-delete tombstones`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FocusRepository.resetStaticCacheForTests()
        val repo = FocusRepository(context)
        repo.clearLocalDeletedIds()
        repo.setAutoBackupPaused(false)
        repo.markStartFreshChosen(false)

        kotlinx.coroutines.runBlocking {
            val today = DateUtils.todayEpochDay()
            val yesterday = today - 1L
            val twoDaysAgo = today - 2L

            val diskState = SeedData.createInitialState().copy(
                tasks = listOf(
                    TaskItem(
                        id = "task_old_only",
                        title = "Old Reinstall Task",
                        updatedAtEpochMillis = 1_000L
                    ),
                    TaskItem(
                        id = "task_conflict",
                        title = "Older Conflict Title",
                        isCompleted = false,
                        updatedAtEpochMillis = 1_000L
                    ),
                    TaskItem(
                        id = "task_to_delete",
                        title = "Will Be Intentionally Deleted",
                        updatedAtEpochMillis = 1_000L
                    )
                ),
                projects = SeedData.createInitialState().projects + com.example.model.ProjectItem(
                    id = "proj_old_custom",
                    name = "Old Custom Project",
                    updatedAt = 1_000L
                ),
                focusRecords = listOf(
                    com.example.model.FocusRecord(
                        id = "rec_day_1",
                        taskTitle = "Day 1",
                        startedAtEpochMillis = DateUtils.millisFromEpochDay(twoDaysAgo, 10, 0),
                        durationMinutes = 30
                    ),
                    com.example.model.FocusRecord(
                        id = "rec_day_2",
                        taskTitle = "Day 2",
                        startedAtEpochMillis = DateUtils.millisFromEpochDay(yesterday, 10, 0),
                        durationMinutes = 40
                    )
                ),
                forestState = com.example.model.ForestState(
                    sunlightPoints = 900,
                    currentGrowthMinutes = 45,
                    unlockedSpecies = listOf(com.example.model.PlantSpecies.PINE, com.example.model.PlantSpecies.OAK),
                    plantedHistory = listOf(
                        com.example.model.PlantedTree(
                            id = "tree_old_1",
                            species = com.example.model.PlantSpecies.OAK,
                            plantedAtEpochMillis = 1_000L,
                            durationMinutes = 40
                        )
                    ),
                    currentDailyStreak = 2,
                    bestDailyStreak = 4
                ),
                settings = com.example.model.AppSettings(
                    pomodoroMinutes = 50,
                    shortBreakMinutes = 10,
                    accentColorHex = "#8B5CF6",
                    whitelistedPackages = listOf("com.old.allowed"),
                    blockedPackages = listOf("com.old.blocked"),
                    geminiModelId = "gemini-2.5-pro",
                    geminiApiKey = "AIzaOldSavedKey",
                    settingsUpdatedAt = 2_000L
                )
            )

            val publicFile = repo.getPublicBackupFile()
            publicFile.parentFile?.mkdirs()
            publicFile.writeText(repo.exportJson(diskState), Charsets.UTF_8)

            // Case 1: Fresh reinstall (deletedIds empty), user adds a new task & session with unmodified default settings
            val liveAfterReinstall = SeedData.createInitialState().copy(
                tasks = listOf(
                    TaskItem(
                        id = "task_new_only",
                        title = "New Post-Reinstall Task",
                        updatedAtEpochMillis = 3_000L
                    ),
                    TaskItem(
                        id = "task_conflict",
                        title = "Newer Conflict Title",
                        isCompleted = true,
                        updatedAtEpochMillis = 3_000L
                    )
                ),
                projects = SeedData.createInitialState().projects + com.example.model.ProjectItem(
                    id = "proj_new_custom",
                    name = "New Custom Project",
                    updatedAt = 3_000L
                ),
                focusRecords = listOf(
                    com.example.model.FocusRecord(
                        id = "rec_day_3",
                        taskTitle = "Day 3",
                        startedAtEpochMillis = DateUtils.millisFromEpochDay(today, 10, 0),
                        durationMinutes = 25
                    )
                ),
                forestState = com.example.model.ForestState(
                    sunlightPoints = 250,
                    currentGrowthMinutes = 25,
                    unlockedSpecies = listOf(com.example.model.PlantSpecies.PINE),
                    plantedHistory = listOf(
                        com.example.model.PlantedTree(
                            id = "tree_new_1",
                            species = com.example.model.PlantSpecies.PINE,
                            plantedAtEpochMillis = 3_000L,
                            durationMinutes = 25
                        )
                    )
                ),
                // Default unmodified settings (settingsUpdatedAt == 0L) -> should inherit disk settings!
                settings = com.example.model.AppSettings(
                    whitelistedPackages = listOf("com.new.allowed"),
                    blockedPackages = listOf("com.new.blocked"),
                    settingsUpdatedAt = 0L
                )
            )

            val mergedRes = repo.saveState(liveAfterReinstall)
            assertTrue(mergedRes.isSuccess)
            val merged = mergedRes.getOrNull()!!

            // Union of tasks + conflict resolution by latest updatedAtEpochMillis
            assertEquals(4, merged.tasks.size)
            assertNotNull(merged.tasks.find { it.id == "task_old_only" })
            assertNotNull(merged.tasks.find { it.id == "task_new_only" })
            val resolvedConflict = merged.tasks.find { it.id == "task_conflict" }!!
            assertEquals("Newer Conflict Title", resolvedConflict.title)
            assertTrue(resolvedConflict.isCompleted)

            // Union of projects
            assertNotNull(merged.projects.find { it.id == "proj_old_custom" })
            assertNotNull(merged.projects.find { it.id == "proj_new_custom" })

            // Union of focus sessions -> total focus minutes never decreases (30 + 40 + 25 = 95)
            assertEquals(3, merged.focusRecords.size)
            assertEquals(95, merged.focusStats.totalFocusMinutes)

            // Forest cumulative/max preservation & 3-day consecutive streak + preserved best streak (4)
            assertEquals(900, merged.forestState.sunlightPoints)
            assertEquals(2, merged.forestState.plantedHistory.size)
            assertTrue(merged.forestState.unlockedSpecies.contains(com.example.model.PlantSpecies.OAK))
            assertEquals(3, merged.forestState.currentDailyStreak)
            assertEquals(4, merged.forestState.bestDailyStreak)

            // Inherited settings from existing backup + unioned blocker lists
            assertEquals(50, merged.settings.pomodoroMinutes)
            assertEquals(10, merged.settings.shortBreakMinutes)
            assertEquals("#8B5CF6", merged.settings.accentColorHex)
            assertEquals("gemini-2.5-pro", merged.settings.geminiModelId)
            assertEquals("AIzaOldSavedKey", merged.settings.geminiApiKey)
            assertEquals(listOf("com.new.allowed", "com.old.allowed"), merged.settings.whitelistedPackages)
            assertEquals(listOf("com.new.blocked", "com.old.blocked"), merged.settings.blockedPackages)

            // Case 2: Intentional Deletion Tracking (Soft-Delete Tombstones) + newer user settings win
            repo.recordDeletedIds(listOf("task_to_delete", "proj_old_custom", "rec_day_1"))
            val afterDeleteState = merged.copy(
                tasks = merged.tasks.filterNot { it.id == "task_to_delete" },
                projects = merged.projects.filterNot { it.id == "proj_old_custom" },
                focusRecords = merged.focusRecords.filterNot { it.id == "rec_day_1" },
                pomodoroSessions = merged.pomodoroSessions.filterNot { it.id == "rec_day_1" },
                settings = merged.settings.copy(
                    pomodoroMinutes = 35,
                    settingsUpdatedAt = 9_000L
                )
            )
            val afterDeleteRes = repo.saveState(afterDeleteState)
            assertTrue(afterDeleteRes.isSuccess)
            val finalMerged = afterDeleteRes.getOrNull()!!

            assertEquals(null, finalMerged.tasks.find { it.id == "task_to_delete" })
            assertEquals(null, finalMerged.projects.find { it.id == "proj_old_custom" })
            assertEquals(null, finalMerged.focusRecords.find { it.id == "rec_day_1" })
            assertEquals(35, finalMerged.settings.pomodoroMinutes)

            // Verify disk file itself has the exact same tombstoned + newer settings state with zero duplicates
            val diskAfterDelete = repo.readExistingExternalBackup()!!
            assertEquals(null, diskAfterDelete.tasks.find { it.id == "task_to_delete" })
            assertEquals(35, diskAfterDelete.settings.pomodoroMinutes)
        }
    }

    @Test
    fun `strict app blocker enforces default-deny whitelist during active focus and persists in backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.blocker.StrictAppBlockerManager.setStrictBlockerEnabled(context, true)
        com.example.blocker.StrictAppBlockerManager.setWhitelistedPackages(
            context,
            setOf("com.whatsapp", "com.google.android.calculator")
        )

        // Simulate active Pomodoro Focus session
        com.example.blocker.StrictAppBlockerManager.syncTimerState(
            context = context,
            isRunning = true,
            phase = TimerPhase.FOCUS,
            strictMode = true,
            timerEndTimeEpochMillis = System.currentTimeMillis() + 600_000L,
            countMode = com.example.model.TimerCountMode.COUNTDOWN
        )

        assertTrue(com.example.blocker.StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(context))
        // Safe-System Exclusion List: Focus_zohayr, SystemUI, android, XOSLauncher, launcher3, Dialer are NEVER blocked
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, context.packageName)
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.android.systemui")
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "android")
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.transsion.XOSLauncher")
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.android.launcher3")
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.google.android.dialer")
        )
        // Whitelisted app is allowed
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.whatsapp")
        )
        // Android Settings, Package Installers, and Phone Manager are strictly blocked during active focus
        assertEquals(
            true,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.android.settings")
        )
        assertEquals(
            true,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.transsion.phonemanager")
        )
        // Non-whitelisted third-party app is strictly blocked
        assertEquals(
            true,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.zhiliaoapp.musically")
        )

        // When in SHORT_BREAK, strict blocker does not block apps
        com.example.blocker.StrictAppBlockerManager.syncTimerState(
            context = context,
            isRunning = true,
            phase = TimerPhase.SHORT_BREAK,
            strictMode = true,
            timerEndTimeEpochMillis = System.currentTimeMillis() + 300_000L,
            countMode = com.example.model.TimerCountMode.COUNTDOWN
        )
        assertEquals(
            false,
            com.example.blocker.StrictAppBlockerManager.shouldBlockPackageImmediately(context, "com.zhiliaoapp.musically")
        )
    }

    @Test
    fun `tomato ai live context snapshot and autonomous function calling tools execute on database`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.FocusTodoViewModel(app)
        vm.resetDemoData()

        // 1. Test API Key saving & Gemini Model Selector persistence + dynamic URL routing
        vm.saveTomatoAiApiKey("AIzaSyTestKey12345")
        assertEquals("AIzaSyTestKey12345", com.example.ai.TomatoAiService.getSavedApiKey(app))
        assertEquals("AIzaSyTestKey12345", vm.uiState.value.tomatoAiApiKey)

        // Default model is gemini-2.5-flash
        assertEquals(com.example.ai.TomatoGeminiModel.FLASH_2_5, vm.uiState.value.tomatoAiSelectedModel)
        assertEquals("2.5 Flash — متوازن وذكي", com.example.ai.TomatoGeminiModel.FLASH_2_5.displayLabel)
        assertEquals("Flash-Lite 3.5 — سريع وخفيف", com.example.ai.TomatoGeminiModel.FLASH_LITE_3_5.displayLabel)
        assertEquals("Flash-Lite 3.5", com.example.ai.TomatoGeminiModel.FLASH_LITE_3_5.shortBadge)
        assertEquals("2.5 Pro — تخطيط عميق", com.example.ai.TomatoGeminiModel.PRO_2_5.displayLabel)

        vm.selectTomatoAiModel(com.example.ai.TomatoGeminiModel.FLASH_LITE_3_5)
        assertEquals(com.example.ai.TomatoGeminiModel.FLASH_LITE_3_5, com.example.ai.TomatoAiService.getSavedModel(app))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=AIzaSyTestKey12345",
            com.example.ai.TomatoAiService.buildEndpointUrl(vm.uiState.value.tomatoAiSelectedModel.modelId, "AIzaSyTestKey12345")
        )

        vm.selectTomatoAiModel(com.example.ai.TomatoGeminiModel.PRO_2_5)
        assertEquals(com.example.ai.TomatoGeminiModel.PRO_2_5, com.example.ai.TomatoAiService.getSavedModel(app))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent?key=AIzaSyTestKey12345",
            com.example.ai.TomatoAiService.buildEndpointUrl(vm.uiState.value.tomatoAiSelectedModel.modelId, "AIzaSyTestKey12345")
        )

        vm.selectTomatoAiModel(com.example.ai.TomatoGeminiModel.FLASH_2_5)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSyTestKey12345",
            com.example.ai.TomatoAiService.buildEndpointUrl(vm.uiState.value.tomatoAiSelectedModel.modelId, "AIzaSyTestKey12345")
        )

        // 2. Test Live Context Prompt contains folders, projects, tags, and timer settings
        val (snapshotData, snapshotTimer) = vm.getCurrentStateSnapshot()
        val liveContext = com.example.ai.TomatoAiService.buildLiveAppContextPrompt(snapshotData, snapshotTimer)
        assertTrue(liveContext.contains("التعليم والتطوير"))
        assertTrue(liveContext.contains("الحياة والروتين"))
        assertTrue(liveContext.contains("الروتين اليومي"))
        assertTrue(liveContext.contains("مشاريع جانبية"))

        // 3. Test autonomous addTask tool call
        val addRes = com.example.ai.TomatoAiService.dispatchToolCall(
            com.example.ai.TomatoFunctionCall(
                name = "addTask",
                args = org.json.JSONObject().apply {
                    put("title", "مراجعة الرياضيات - التفاضل")
                    put("estimatedPomodoros", 4)
                    put("reminderTime", "16:30")
                    put("project", "التعليم والتطوير")
                    put("tag", "مهم")
                    put("priority", "HIGH")
                }
            ),
            vm
        )
        assertEquals("success", addRes.resultJson.optString("status"))
        val createdTask = vm.uiState.value.data.tasks.find { it.title == "مراجعة الرياضيات - التفاضل" }
        assertNotNull(createdTask)
        assertEquals(4, createdTask!!.estimatedPomodoros)
        assertEquals("16:30", createdTask.reminderTime)
        assertEquals("proj_study", createdTask.projectId)
        assertTrue(createdTask.tagIds.contains("tag_imp"))

        // 4. Test autonomous updateTask tool call
        val updateRes = com.example.ai.TomatoAiService.dispatchToolCall(
            com.example.ai.TomatoFunctionCall(
                name = "updateTask",
                args = org.json.JSONObject().apply {
                    put("taskId", createdTask.id)
                    put("estimatedPomodoros", 6)
                    put("reminderTime", "18:00")
                }
            ),
            vm
        )
        assertEquals("success", updateRes.resultJson.optString("status"))
        val updatedTask = vm.uiState.value.data.tasks.find { it.id == createdTask.id }!!
        assertEquals(6, updatedTask.estimatedPomodoros)
        assertEquals("18:00", updatedTask.reminderTime)

        // 5. Test autonomous completeTask tool call
        val completeRes = com.example.ai.TomatoAiService.dispatchToolCall(
            com.example.ai.TomatoFunctionCall(
                name = "completeTask",
                args = org.json.JSONObject().apply {
                    put("taskId", createdTask.id)
                    put("completed", true)
                }
            ),
            vm
        )
        assertEquals("success", completeRes.resultJson.optString("status"))
        assertTrue(vm.uiState.value.data.tasks.find { it.id == createdTask.id }!!.isCompleted)

        // 6. Test autonomous createProject and deleteTask tool calls
        val createProjRes = com.example.ai.TomatoAiService.dispatchToolCall(
            com.example.ai.TomatoFunctionCall(
                name = "createProject",
                args = org.json.JSONObject().apply {
                    put("name", "اللغة الإنجليزية")
                    put("folderName", "التعليم والتطوير")
                }
            ),
            vm
        )
        assertEquals("success", createProjRes.resultJson.optString("status"))
        assertTrue(vm.uiState.value.data.projects.any { it.name == "اللغة الإنجليزية" })

        val deleteRes = com.example.ai.TomatoAiService.dispatchToolCall(
            com.example.ai.TomatoFunctionCall(
                name = "deleteTask",
                args = org.json.JSONObject().apply {
                    put("taskId", createdTask.id)
                }
            ),
            vm
        )
        assertEquals("success", deleteRes.resultJson.optString("status"))
        assertEquals(null, vm.uiState.value.data.tasks.find { it.id == createdTask.id })

        vm.uiState.value.data.projects.find { it.name == "اللغة الإنجليزية" }?.let {
            vm.deleteProject(it.id)
        }
    }

    @Test
    fun `all alert notification channels use IMPORTANCE_HIGH while auto-backup channel is completely silent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FocusNotificationManager.ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val highImportanceChannels = listOf(
            FocusNotificationManager.CHANNEL_POMODORO_COMPLETE,
            FocusNotificationManager.CHANNEL_TASK_ALARMS,
            FocusNotificationManager.CHANNEL_MANUAL_BACKUP,
            FocusNotificationManager.CHANNEL_APP_BLOCKER
        )
        highImportanceChannels.forEach { channelId ->
            val ch = nm.getNotificationChannel(channelId)
            assertNotNull("Missing channel $channelId", ch)
            assertEquals("Expected IMPORTANCE_HIGH for $channelId", NotificationManager.IMPORTANCE_HIGH, ch.importance)
            assertTrue("Expected badge enabled for $channelId", ch.canShowBadge())
            assertNotNull("Expected sound for $channelId", ch.sound)
        }

        val silentAutoBackupChannel = nm.getNotificationChannel(FocusNotificationManager.CHANNEL_AUTO_BACKUP)
        assertNotNull("Missing silent auto-backup channel", silentAutoBackupChannel)
        assertEquals(NotificationManager.IMPORTANCE_MIN, silentAutoBackupChannel.importance)
        assertEquals(null, silentAutoBackupChannel.sound)
        assertEquals(false, silentAutoBackupChannel.shouldVibrate())
        assertEquals(false, silentAutoBackupChannel.canShowBadge())
    }

    @Test
    fun `universal unread badges increment across all sections and auto-clear when viewed`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)

        com.example.notifications.UnreadBadgeTracker.resetAll(app)
        val vm = com.example.viewmodel.FocusTodoViewModel(app)
        vm.resetDemoData()

        val today = DateUtils.todayEpochDay()
        vm.addTask(
            title = "مهمة التذكير والغد",
            estimatedPomodoros = 2,
            dueDateEpochDay = today,
            isSomeday = false,
            isEvent = false,
            priority = TaskPriority.HIGH,
            projectId = "proj_study",
            tagIds = listOf("tag_imp"),
            reminderTime = "10:00"
        )
        val task = vm.uiState.value.data.tasks.first { it.title == "مهمة التذكير والغد" }

        // 1. Trigger Task Reminder -> heads-up notif + tasksUnread = 1
        FocusNotificationManager.showTaskReminderNotification(
            context = app,
            taskId = task.id,
            taskTitle = task.title,
            taskNotes = "مراجعة الفصل الأول",
            soundAndVibration = true,
            isArabic = true
        )
        val taskNotif = shadowNm.getNotification(FocusNotificationManager.taskRequestCode(task.id))
        assertNotNull(taskNotif)
        assertEquals(FocusNotificationManager.CHANNEL_TASK_ALARMS, taskNotif.channelId)
        assertEquals(1, taskNotif.number)
        assertEquals(1, com.example.notifications.UnreadBadgeTracker.getState(app).tasksUnread)
        assertTrue(com.example.notifications.UnreadBadgeTracker.getState(app).unreadTaskIds.contains(task.id))

        // 2. Trigger Pomodoro Completion -> heads-up notif + pomodoroUnread = 1
        FocusNotificationManager.showTimerCompletionNotification(
            context = app,
            taskTitle = task.title,
            phaseName = TimerPhase.FOCUS.name,
            soundAndVibration = true,
            isArabic = true
        )
        val timerNotif = shadowNm.getNotification(FocusNotificationManager.NOTIFICATION_ID_TIMER_COMPLETE)
        assertNotNull(timerNotif)
        assertEquals(FocusNotificationManager.CHANNEL_POMODORO_COMPLETE, timerNotif.channelId)
        assertEquals(1, timerNotif.number)
        assertEquals(1, com.example.notifications.UnreadBadgeTracker.getState(app).pomodoroUnread)

        // 3. Trigger App Blocker Alert -> heads-up notif + blockerUnread = 1
        FocusNotificationManager.showAppBlockerTriggeredNotification(
            context = app,
            blockedPackageName = "com.instagram.android",
            appLabel = "Instagram",
            isArabic = true
        )
        val blockerNotif = shadowNm.getNotification(FocusNotificationManager.NOTIFICATION_ID_APP_BLOCKER)
        assertNotNull(blockerNotif)
        assertEquals(FocusNotificationManager.CHANNEL_APP_BLOCKER, blockerNotif.channelId)
        assertEquals(1, blockerNotif.number)
        assertEquals(1, com.example.notifications.UnreadBadgeTracker.getState(app).blockerUnread)

        // 4. Record Stats & Forest Activity + Backup Alert
        com.example.notifications.UnreadBadgeTracker.recordStatsAndForestActivity(app, recordReports = true, recordForest = true)
        assertEquals(1, com.example.notifications.UnreadBadgeTracker.getState(app).reportsUnread)
        assertEquals(1, com.example.notifications.UnreadBadgeTracker.getState(app).forestUnread)
        assertTrue(com.example.notifications.UnreadBadgeTracker.getState(app).settingsBackupUnread >= 1)
        assertTrue(com.example.notifications.UnreadBadgeTracker.getState(app).totalUnread >= 6)

        // 5. Auto-Clear on View for each section
        vm.openTaskInspector(task.id)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).tasksUnread)
        assertEquals(null, shadowNm.getNotification(FocusNotificationManager.taskRequestCode(task.id)))

        vm.setShowFullScreenTimer(true)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).pomodoroUnread)
        assertEquals(null, shadowNm.getNotification(FocusNotificationManager.NOTIFICATION_ID_TIMER_COMPLETE))

        vm.setShowStrictBlockerSetup(true)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).blockerUnread)
        assertEquals(null, shadowNm.getNotification(FocusNotificationManager.NOTIFICATION_ID_APP_BLOCKER))

        vm.setShowReports(true)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).reportsUnread)

        vm.setShowForest(true)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).forestUnread)

        vm.setShowSettings(true)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).settingsBackupUnread)
        assertEquals(0, com.example.notifications.UnreadBadgeTracker.getState(app).totalUnread)
    }
}
