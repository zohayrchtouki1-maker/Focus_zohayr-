package com.example.data

import com.example.model.AppBackupState
import com.example.model.AppSettings
import com.example.model.FocusRecord
import com.example.model.FolderItem
import com.example.model.ForestState
import com.example.model.PlantSpecies
import com.example.model.ProjectItem
import com.example.model.TagItem
import com.example.model.TaskItem

object SeedData {
    val legacySampleTaskIds = setOf(
        "task_1",
        "task_2",
        "task_3",
        "task_4",
        "task_5",
        "task_6",
        "task_7",
        "task_8"
    )

    val legacySampleRecordIds = setOf(
        "rec_1",
        "rec_2",
        "rec_3",
        "rec_4",
        "rec_5",
        "rec_6",
        "rec_7",
        "rec_8",
        "rec_9"
    )

    fun createInitialState(): AppBackupState {
        val folderAcademic = FolderItem(id = "folder_acad", name = "التعليم والتطوير", isExpanded = true)
        val folderLife = FolderItem(id = "folder_life", name = "الحياة والروتين", isExpanded = true)

        val projStudy = ProjectItem(
            id = "proj_study",
            name = "الدراسة الجامعية 📚",
            colorHex = "#4A6EE0",
            folderId = folderAcademic.id
        )
        val projSkills = ProjectItem(
            id = "proj_skills",
            name = "تطوير المهارات 💻",
            colorHex = "#1A73E8",
            folderId = folderAcademic.id
        )
        val projRoutine = ProjectItem(
            id = "proj_routine",
            name = "الروتين اليومي ⚡",
            colorHex = "#34A853",
            folderId = folderLife.id
        )
        val projSide = ProjectItem(
            id = "proj_side",
            name = "مشاريع جانبية 🚀",
            colorHex = "#FABB05",
            folderId = null
        )

        val tagImportant = TagItem(id = "tag_imp", name = "مهم", colorHex = "#EF4444")
        val tagReview = TagItem(id = "tag_rev", name = "مراجعة", colorHex = "#F59E0B")
        val tagReading = TagItem(id = "tag_read", name = "قراءة", colorHex = "#8B5CF6")
        val tagCoding = TagItem(id = "tag_code", name = "برمجة", colorHex = "#4A6EE0")

        val forestState = ForestState(
            sunlightPoints = 0,
            currentGrowthMinutes = 0,
            activeSpecies = PlantSpecies.PINE,
            unlockedSpecies = listOf(PlantSpecies.PINE),
            plantedHistory = emptyList()
        )

        return AppBackupState(
            tasks = emptyList<TaskItem>(),
            projects = listOf(projStudy, projSkills, projRoutine, projSide),
            folders = listOf(folderAcademic, folderLife),
            tags = listOf(tagImportant, tagReview, tagReading, tagCoding),
            focusRecords = emptyList<FocusRecord>(),
            settings = AppSettings(),
            forestState = forestState
        )
    }
}
