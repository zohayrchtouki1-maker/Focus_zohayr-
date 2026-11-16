package com.example.data

import android.content.Context
import com.example.model.AppBackupState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupImportMode {
    MERGE,
    REPLACE
}

class FocusRepository(private val context: Context) {
    companion object {
        private const val LOCAL_STATE_FILE_NAME = "focus_todo_state.json"
        private const val PREFS_NAME = "focus_todo_local_storage"
        private const val PREFS_STATE_KEY = "focus_todo_state_json"

        fun generateBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(nowMillis))
            return "focus_zohayr_backup_$dateStr.json"
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")

    private val stateFile: File get() = File(context.filesDir, LOCAL_STATE_FILE_NAME)
    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        val fileState = readStateFromFile(stateFile)
        val prefsState = readStateFromPrefs()
        val legacyUserStates = loadAnyLegacyUserDirectoryStates()

        var resolved = fileState ?: prefsState ?: SeedData.createInitialState()
        for (legacy in legacyUserStates) {
            if (legacy.tasks.isNotEmpty() || legacy.focusRecords.isNotEmpty()) {
                resolved = mergeBackupStates(resolved, legacy)
            }
        }

        val cleaned = cleanLegacySamples(resolved)
        saveStateInternal(cleaned)
        cleaned
    }

    suspend fun saveState(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            saveStateInternal(cleanLegacySamples(state))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveStateInternal(state: AppBackupState) {
        val json = adapter.toJson(state)
        try {
            stateFile.parentFile?.mkdirs()
            val tmpFile = File(stateFile.parentFile, "${stateFile.name}.tmp")
            tmpFile.writeText(json, Charsets.UTF_8)
            tmpFile.renameTo(stateFile)
        } catch (_: Exception) {
        }
        try {
            prefs.edit().putString(PREFS_STATE_KEY, json).apply()
        } catch (_: Exception) {
        }
    }

    private fun readStateFromFile(file: File): AppBackupState? {
        return try {
            if (file.exists()) {
                adapter.fromJson(file.readText(Charsets.UTF_8))?.let { cleanLegacySamples(it) }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readStateFromPrefs(): AppBackupState? {
        return try {
            val raw = prefs.getString(PREFS_STATE_KEY, null) ?: return null
            adapter.fromJson(raw)?.let { cleanLegacySamples(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAnyLegacyUserDirectoryStates(): List<AppBackupState> {
        return try {
            val usersDir = File(context.filesDir, "users")
            if (!usersDir.exists() || !usersDir.isDirectory) return emptyList()
            val states = mutableListOf<AppBackupState>()
            usersDir.listFiles()?.forEach { userDir ->
                val f = File(userDir, "focus_todo_backup.json")
                readStateFromFile(f)?.let { states.add(it) }
            }
            // Remove legacy user dirs after migrating into main local state
            usersDir.deleteRecursively()
            states
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cleanLegacySamples(loaded: AppBackupState): AppBackupState {
        val cleanedTasks = loaded.tasks.filterNot { it.id in SeedData.legacySampleTaskIds }
        val cleanedRecords = loaded.focusRecords.filterNot { it.id in SeedData.legacySampleRecordIds }
        return if (cleanedTasks.size != loaded.tasks.size || cleanedRecords.size != loaded.focusRecords.size) {
            loaded.copy(tasks = cleanedTasks, focusRecords = cleanedRecords)
        } else {
            loaded
        }
    }

    fun mergeBackupStates(current: AppBackupState, imported: AppBackupState): AppBackupState {
        val cleanedImported = cleanLegacySamples(imported)
        val mergedTasks = (cleanedImported.tasks + current.tasks)
            .distinctBy { it.id }
        val mergedProjects = (current.projects + cleanedImported.projects)
            .distinctBy { it.id }
        val mergedFolders = (current.folders + cleanedImported.folders)
            .distinctBy { it.id }
        val mergedTags = (current.tags + cleanedImported.tags)
            .distinctBy { it.id }
        val mergedRecords = (current.focusRecords + cleanedImported.focusRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }

        val mergedPlantedHistory = (current.forestState.plantedHistory + cleanedImported.forestState.plantedHistory)
            .distinctBy { it.id }
            .sortedByDescending { it.plantedAtEpochMillis }
        val mergedUnlockedSpecies = (current.forestState.unlockedSpecies + cleanedImported.forestState.unlockedSpecies)
            .distinct()
        val mergedSunlight = maxOf(current.forestState.sunlightPoints, cleanedImported.forestState.sunlightPoints)

        return current.copy(
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedRecords,
            forestState = current.forestState.copy(
                sunlightPoints = mergedSunlight,
                unlockedSpecies = mergedUnlockedSpecies,
                plantedHistory = mergedPlantedHistory
            )
        )
    }

    fun exportJson(state: AppBackupState): String {
        return adapter.toJson(cleanLegacySamples(state))
    }

    fun createShareableBackupFile(state: AppBackupState): File {
        val backupsDir = File(context.cacheDir, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()
        val fileName = generateBackupFileName()
        val outFile = File(backupsDir, fileName)
        outFile.writeText(exportJson(state), Charsets.UTF_8)
        return outFile
    }

    fun parseBackupJson(json: String): AppBackupState? {
        return try {
            adapter.fromJson(json)?.let { cleanLegacySamples(it) }
        } catch (_: Exception) {
            null
        }
    }
}
