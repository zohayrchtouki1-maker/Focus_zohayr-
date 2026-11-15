package com.example.data

import com.example.model.AppBackupState
import com.example.model.AppSettings
import com.example.model.FocusRecord
import com.example.model.FolderItem
import com.example.model.ForestState
import com.example.model.PlantSpecies
import com.example.model.PlantedTree
import com.example.model.ProjectItem
import com.example.model.RecurrenceRule
import com.example.model.SubtaskItem
import com.example.model.TagItem
import com.example.model.TaskItem
import com.example.model.TaskPriority

object SeedData {
    fun createInitialState(): AppBackupState {
        val today = DateUtils.todayEpochDay()
        val now = System.currentTimeMillis()

        val folderAcademic = FolderItem(id = "folder_acad", name = "التعليم والتطوير", isExpanded = true)
        val folderLife = FolderItem(id = "folder_life", name = "الحياة والروتين", isExpanded = true)

        val projStudy = ProjectItem(
            id = "proj_study",
            name = "الدراسة الجامعية 📚",
            colorHex = "#4F46E5",
            folderId = folderAcademic.id
        )
        val projSkills = ProjectItem(
            id = "proj_skills",
            name = "تطوير المهارات 💻",
            colorHex = "#0EA5E9",
            folderId = folderAcademic.id
        )
        val projRoutine = ProjectItem(
            id = "proj_routine",
            name = "الروتين اليومي ⚡",
            colorHex = "#10B981",
            folderId = folderLife.id
        )
        val projSide = ProjectItem(
            id = "proj_side",
            name = "مشاريع جانبية 🚀",
            colorHex = "#F59E0B",
            folderId = null
        )

        val tagImportant = TagItem(id = "tag_imp", name = "مهم", colorHex = "#EF4444")
        val tagReview = TagItem(id = "tag_rev", name = "مراجعة", colorHex = "#F59E0B")
        val tagReading = TagItem(id = "tag_read", name = "قراءة", colorHex = "#8B5CF6")
        val tagCoding = TagItem(id = "tag_code", name = "برمجة", colorHex = "#0EA5E9")

        val tasks = listOf(
            TaskItem(
                id = "task_1",
                title = "مراجعة محاضرة هياكل البيانات والخوارزميات",
                projectId = projStudy.id,
                tagIds = listOf(tagImportant.id, tagReview.id),
                priority = TaskPriority.HIGH,
                estimatedPomodoros = 4,
                completedPomodoros = 2,
                elapsedFocusMinutes = 50,
                dueDateEpochDay = today,
                reminderTime = "16:00",
                notes = "التركيز على أشجار البحث الثنائية (BST) وتحليل التعقيد الزمني Big-O.",
                subtasks = listOf(
                    SubtaskItem(id = "st_1", title = "تلخيص مفاهيم الأشجار المتوازنة", isDone = true),
                    SubtaskItem(id = "st_2", title = "حل ٣ مسائل تطبيقية على DFS و BFS", isDone = true),
                    SubtaskItem(id = "st_3", title = "مراجعة بطاقات الاستذكار السريعة", isDone = false)
                ),
                createdAtEpochMillis = now - 86_400_000L * 2
            ),
            TaskItem(
                id = "task_2",
                title = "بناء واجهة مستخدم متجاوبة بتقنية Compose",
                projectId = projSkills.id,
                tagIds = listOf(tagCoding.id, tagImportant.id),
                priority = TaskPriority.HIGH,
                estimatedPomodoros = 3,
                completedPomodoros = 1,
                elapsedFocusMinutes = 25,
                dueDateEpochDay = today,
                notes = "تطبيق مكونات Material 3 مع دعم كامل للاتجاه من اليمين لليسار.",
                subtasks = listOf(
                    SubtaskItem(id = "st_4", title = "تصميم بطاقة الملخص العلوي", isDone = true),
                    SubtaskItem(id = "st_5", title = "اختبار التجاوب على الشاشات العريضة", isDone = false)
                ),
                createdAtEpochMillis = now - 86_400_000L
            ),
            TaskItem(
                id = "task_3",
                title = "قراءة فصل من كتاب العادات الذرية",
                projectId = projRoutine.id,
                tagIds = listOf(tagReading.id),
                priority = TaskPriority.MEDIUM,
                estimatedPomodoros = 2,
                completedPomodoros = 0,
                elapsedFocusMinutes = 0,
                dueDateEpochDay = today,
                recurrence = RecurrenceRule.DAILY,
                notes = "تدوين أهم ٣ أفكار قابلة للتطبيق اليوم.",
                subtasks = emptyList(),
                createdAtEpochMillis = now - 43_200_000L
            ),
            TaskItem(
                id = "task_4",
                title = "إعداد عرض تقديمي لمشروع التخرج",
                projectId = projStudy.id,
                tagIds = listOf(tagImportant.id),
                priority = TaskPriority.MEDIUM,
                estimatedPomodoros = 4,
                completedPomodoros = 0,
                dueDateEpochDay = today + 1,
                isEvent = true,
                reminderTime = "10:30",
                subtasks = listOf(
                    SubtaskItem(id = "st_6", title = "تجهيز الشرائح الافتتاحية", isDone = false),
                    SubtaskItem(id = "st_7", title = "إضافة الرسوم البيانية للنتائج", isDone = false)
                ),
                createdAtEpochMillis = now - 86_400_000L * 3
            ),
            TaskItem(
                id = "task_5",
                title = "تحديث ملف الأعمال الشخصي وإضافة المشاريع الجديدة",
                projectId = projSide.id,
                tagIds = listOf(tagCoding.id),
                priority = TaskPriority.LOW,
                estimatedPomodoros = 3,
                completedPomodoros = 0,
                dueDateEpochDay = today + 3,
                subtasks = emptyList(),
                createdAtEpochMillis = now - 86_400_000L * 4
            ),
            TaskItem(
                id = "task_6",
                title = "تعلّم أساسيات التصميم الصوتي والمؤثرات",
                projectId = projSkills.id,
                tagIds = listOf(tagReading.id),
                priority = TaskPriority.NONE,
                estimatedPomodoros = 2,
                completedPomodoros = 0,
                isSomeday = true,
                subtasks = emptyList(),
                createdAtEpochMillis = now - 86_400_000L * 5
            ),
            TaskItem(
                id = "task_7",
                title = "ممارسة تمارين التمدد والمشي المسائي",
                projectId = projRoutine.id,
                tagIds = emptyList(),
                priority = TaskPriority.MEDIUM,
                estimatedPomodoros = 1,
                completedPomodoros = 1,
                elapsedFocusMinutes = 25,
                dueDateEpochDay = today,
                recurrence = RecurrenceRule.DAILY,
                isCompleted = true,
                completedAtEpochMillis = now - 3_600_000L * 3,
                createdAtEpochMillis = now - 86_400_000L * 2
            ),
            TaskItem(
                id = "task_8",
                title = "كتابة ملخص أسبوعي للإنجازات",
                projectId = projRoutine.id,
                tagIds = listOf(tagReview.id),
                priority = TaskPriority.LOW,
                estimatedPomodoros = 2,
                completedPomodoros = 2,
                elapsedFocusMinutes = 50,
                dueDateEpochDay = today - 1,
                isCompleted = true,
                completedAtEpochMillis = now - 86_400_000L,
                createdAtEpochMillis = now - 86_400_000L * 6
            )
        )

        val focusRecords = listOf(
            FocusRecord(
                id = "rec_1",
                taskId = "task_1",
                taskTitle = "مراجعة محاضرة هياكل البيانات والخوارزميات",
                projectId = projStudy.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today, 9, 30),
                durationMinutes = 25
            ),
            FocusRecord(
                id = "rec_2",
                taskId = "task_1",
                taskTitle = "مراجعة محاضرة هياكل البيانات والخوارزميات",
                projectId = projStudy.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today, 10, 5),
                durationMinutes = 25
            ),
            FocusRecord(
                id = "rec_3",
                taskId = "task_2",
                taskTitle = "بناء واجهة مستخدم متجاوبة بتقنية Compose",
                projectId = projSkills.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today, 11, 15),
                durationMinutes = 25
            ),
            FocusRecord(
                id = "rec_4",
                taskId = "task_8",
                taskTitle = "كتابة ملخص أسبوعي للإنجازات",
                projectId = projRoutine.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 1, 18, 0),
                durationMinutes = 50
            ),
            FocusRecord(
                id = "rec_5",
                taskId = "task_2",
                taskTitle = "تطبيق أنماط التصميم النظيف",
                projectId = projSkills.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 2, 15, 20),
                durationMinutes = 75
            ),
            FocusRecord(
                id = "rec_6",
                taskId = "task_1",
                taskTitle = "حل تمارين الخوارزميات",
                projectId = projStudy.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 3, 14, 0),
                durationMinutes = 50
            ),
            FocusRecord(
                id = "rec_7",
                taskId = "task_5",
                taskTitle = "تخطيط خارطة المشاريع القادمة",
                projectId = projSide.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 4, 20, 10),
                durationMinutes = 50
            ),
            FocusRecord(
                id = "rec_8",
                taskId = "task_3",
                taskTitle = "قراءة مسائية مركزة",
                projectId = projRoutine.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 5, 19, 30),
                durationMinutes = 35
            ),
            FocusRecord(
                id = "rec_9",
                taskId = "task_1",
                taskTitle = "مذاكرة قواعد البيانات",
                projectId = projStudy.id,
                startedAtEpochMillis = DateUtils.millisFromEpochDay(today - 6, 16, 45),
                durationMinutes = 60
            )
        )

        val forestState = ForestState(
            sunlightPoints = 1250,
            currentGrowthMinutes = 18,
            activeSpecies = PlantSpecies.PINE,
            unlockedSpecies = listOf(PlantSpecies.PINE, PlantSpecies.CHERRY_BLOSSOM),
            plantedHistory = listOf(
                PlantedTree(
                    id = "tree_1",
                    species = PlantSpecies.PINE,
                    plantedAtEpochMillis = DateUtils.millisFromEpochDay(today, 10, 30),
                    durationMinutes = 25
                ),
                PlantedTree(
                    id = "tree_2",
                    species = PlantSpecies.CHERRY_BLOSSOM,
                    plantedAtEpochMillis = DateUtils.millisFromEpochDay(today - 1, 18, 50),
                    durationMinutes = 50
                ),
                PlantedTree(
                    id = "tree_3",
                    species = PlantSpecies.PINE,
                    plantedAtEpochMillis = DateUtils.millisFromEpochDay(today - 2, 16, 35),
                    durationMinutes = 75
                ),
                PlantedTree(
                    id = "tree_4",
                    species = PlantSpecies.CHERRY_BLOSSOM,
                    plantedAtEpochMillis = DateUtils.millisFromEpochDay(today - 4, 21, 0),
                    durationMinutes = 50
                )
            )
        )

        return AppBackupState(
            tasks = tasks,
            projects = listOf(projStudy, projSkills, projRoutine, projSide),
            folders = listOf(folderAcademic, folderLife),
            tags = listOf(tagImportant, tagReview, tagReading, tagCoding),
            focusRecords = focusRecords,
            settings = AppSettings(),
            forestState = forestState
        )
    }
}
