package com.example.data

import android.content.Context
import com.example.model.AppBackupState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FocusRepository(private val context: Context) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")
    private val stateFile: File get() = File(context.filesDir, "focus_todo_state.json")

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        try {
            if (stateFile.exists()) {
                val raw = stateFile.readText(Charsets.UTF_8)
                adapter.fromJson(raw)?.let { return@withContext it }
            }
        } catch (_: Exception) {
            // Fallback to fresh seed data if file is corrupted
        }
        val initial = SeedData.createInitialState()
        saveStateInternal(initial)
        initial
    }

    suspend fun saveState(state: AppBackupState) = withContext(Dispatchers.IO) {
        saveStateInternal(state)
    }

    private fun saveStateInternal(state: AppBackupState) {
        try {
            val tmpFile = File(context.filesDir, "focus_todo_state.json.tmp")
            tmpFile.writeText(adapter.toJson(state), Charsets.UTF_8)
            tmpFile.renameTo(stateFile)
        } catch (_: Exception) {
        }
    }

    fun exportJson(state: AppBackupState): String {
        return adapter.toJson(state)
    }

    fun parseBackupJson(json: String): AppBackupState? {
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}
