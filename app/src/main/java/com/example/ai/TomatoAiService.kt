package com.example.ai

import android.content.Context
import com.example.data.DateUtils
import com.example.data.FocusRepository
import com.example.model.AppBackupState
import com.example.model.TaskPriority
import com.example.viewmodel.TimerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

data class TomatoChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val executedActions: List<String> = emptyList(),
    val isError: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class TomatoFunctionCall(
    val name: String,
    val args: JSONObject
)

data class TomatoToolExecutionResult(
    val functionName: String,
    val resultJson: JSONObject,
    val humanSummary: String
)

interface TomatoToolExecutor {
    fun executeAddTask(
        title: String,
        estimatedPomodoros: Int,
        reminderTime: String?,
        projectQuery: String?,
        tagQuery: String?,
        dueDateEpochDay: Long?,
        priority: TaskPriority,
        notes: String
    ): TomatoToolExecutionResult

    fun executeUpdateTask(
        taskId: String?,
        targetTitle: String?,
        newTitle: String?,
        estimatedPomodoros: Int?,
        reminderTime: String?,
        projectQuery: String?,
        tagQuery: String?,
        dueDateEpochDay: Long?,
        priority: TaskPriority?,
        notes: String?
    ): TomatoToolExecutionResult

    fun executeCompleteTask(
        taskId: String?,
        targetTitle: String?,
        completed: Boolean
    ): TomatoToolExecutionResult

    fun executeDeleteTask(
        taskId: String?,
        targetTitle: String?
    ): TomatoToolExecutionResult

    fun executeCreateProject(
        name: String,
        colorHex: String?,
        folderName: String?
    ): TomatoToolExecutionResult

    fun getCurrentStateSnapshot(): Pair<AppBackupState, TimerUiState>
}

enum class TomatoGeminiModel(
    val modelId: String,
    val shortBadge: String,
    val displayLabel: String
) {
    FLASH_2_5(
        modelId = "gemini-2.5-flash",
        shortBadge = "2.5 Flash",
        displayLabel = "2.5 Flash — متوازن وذكي"
    ),
    FLASH_LITE_3_5(
        modelId = "gemini-3.5-flash-lite",
        shortBadge = "Flash-Lite 3.5",
        displayLabel = "Flash-Lite 3.5 — سريع وخفيف"
    ),
    PRO_2_5(
        modelId = "gemini-2.5-pro",
        shortBadge = "2.5 Pro",
        displayLabel = "2.5 Pro — تخطيط عميق"
    );

    companion object {
        fun fromModelId(id: String?): TomatoGeminiModel {
            val clean = id?.trim()?.removePrefix("models/") ?: return FLASH_2_5
            if (clean.endsWith("flash-lite", ignoreCase = true)) {
                return FLASH_LITE_3_5
            }
            return entries.find { it.modelId.equals(clean, ignoreCase = true) } ?: FLASH_2_5
        }
    }
}

object TomatoAiService {
    const val PREF_GEMINI_API_KEY = "tomato_ai_gemini_api_key"
    const val PREF_GEMINI_MODEL = "tomato_ai_selected_model"
    const val MODEL_NAME = "gemini-2.5-flash"

    fun getSavedApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_GEMINI_API_KEY, "")?.trim().orEmpty()
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_GEMINI_API_KEY, apiKey.trim()).commit()
    }

    fun getSavedModel(context: Context): TomatoGeminiModel {
        val prefs = context.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_GEMINI_MODEL, TomatoGeminiModel.FLASH_2_5.modelId)
        val resolved = TomatoGeminiModel.fromModelId(raw)
        if (raw != resolved.modelId) {
            prefs.edit().putString(PREF_GEMINI_MODEL, resolved.modelId).commit()
        }
        return resolved
    }

    fun saveSelectedModel(context: Context, model: TomatoGeminiModel) {
        val prefs = context.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_GEMINI_MODEL, model.modelId).commit()
    }

    fun buildEndpointUrl(modelId: String, apiKey: String): String {
        val resolvedModel = TomatoGeminiModel.fromModelId(modelId).modelId.removePrefix("models/")
        return "https://generativelanguage.googleapis.com/v1beta/models/$resolvedModel:generateContent?key=${apiKey.trim()}"
    }

    fun buildLiveAppContextPrompt(state: AppBackupState, timer: TimerUiState): String {
        val nowMillis = System.currentTimeMillis()
        val nowDate = Date(nowMillis)
        val todayEpoch = DateUtils.todayEpochDay()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(nowDate)
        val timeStr = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(nowDate)
        val dayNameAr = SimpleDateFormat("EEEE", Locale("ar")).format(nowDate)
        val dayNameEn = SimpleDateFormat("EEEE", Locale.ENGLISH).format(nowDate)

        val foldersById = state.folders.associateBy { it.id }
        val projectsById = state.projects.associateBy { it.id }
        val tagsById = state.tags.associateBy { it.id }

        val todayRecords = state.focusRecords.filter { rec ->
            rec.dateKey == dateStr || DateUtils.epochDayFromMillis(rec.startedAtEpochMillis) == todayEpoch
        }
        val todayFocusMinutes = todayRecords.sumOf { it.durationMinutes }
        val todaySessionsCount = todayRecords.size

        return buildString {
            appendLine("You are 'Tomato AI' (طماطم الذكي 🍅), an intelligent, warm, proactive, and autonomous productivity & study assistant built directly into the 'FocusTomato' Pomodoro & task management app.")
            appendLine("Always reply in the same language the user writes in (default to clear, natural Arabic if the user writes in Arabic).")
            appendLine("CRITICAL AUTONOMOUS EXECUTION RULE:")
            appendLine("- You have direct Function Calling tools (`addTask`, `updateTask`, `completeTask`, `deleteTask`, `createProject`) connected to the live app database and alarm scheduler.")
            appendLine("- Whenever the user asks to add tasks, create a study schedule/program, edit/reschedule a task, set an alarm/reminder, complete a task, delete a task, or create a project, you MUST invoke the corresponding tool function(s) IMMEDIATELY and AUTONOMOUSLY without asking the user to confirm first.")
            appendLine("- Avoid creating duplicate tasks if an identical pending task already exists in the live context; update it instead if appropriate.")
            appendLine("- When calculating `estimatedPomodoros` from hours/minutes, divide the requested duration by the user's Pomodoro duration (${state.settings.pomodoroMinutes} minutes per Pomodoro). For example, 1 hour = ${maxOf(1, 60 / state.settings.pomodoroMinutes.coerceAtLeast(1))} Pomodoros.")
            appendLine("- For `reminderTime`, always pass 24-hour `HH:mm` format (e.g., `08:00`, `14:30`, `19:00`).")
            appendLine()
            appendLine("=== LIVE APP STATE SNAPSHOT (REAL-TIME) ===")
            appendLine("Current Date: $dateStr ($dayNameAr / $dayNameEn) | Today EpochDay: $todayEpoch")
            appendLine("Current Local Time (24h): $timeStr")
            appendLine()
            appendLine("--- Focus Timer Settings & Today's Stats ---")
            appendLine("- Pomodoro Duration: ${state.settings.pomodoroMinutes} min | Short Break: ${state.settings.shortBreakMinutes} min | Long Break: ${state.settings.longBreakMinutes} min (every ${state.settings.longBreakInterval} sessions)")
            appendLine("- Task Reminders Enabled: ${state.settings.enableTaskReminders} | Strict App Blocker Enabled: ${state.settings.strictBlockerEnabled}")
            appendLine("- Timer Currently Running: ${timer.isRunning} (Phase: ${timer.phase}, Remaining: ${timer.remainingSeconds / 60}m ${timer.remainingSeconds % 60}s)")
            appendLine("- Today's Completed Focus Sessions: $todaySessionsCount sessions ($todayFocusMinutes minutes focused today)")
            appendLine("- Total Pending Tasks: ${state.tasks.count { !it.isCompleted }} | Total Completed Tasks: ${state.tasks.count { it.isCompleted }}")
            appendLine()
            appendLine("--- Existing Folders (${state.folders.size}) ---")
            if (state.folders.isEmpty()) {
                appendLine("(None)")
            } else {
                state.folders.forEach { folder ->
                    appendLine("- Folder ID: ${folder.id} | Name: \"${folder.name}\"")
                }
            }
            appendLine()
            appendLine("--- Existing Projects / Lists (${state.projects.size}) ---")
            if (state.projects.isEmpty()) {
                appendLine("(None)")
            } else {
                state.projects.forEach { proj ->
                    val folderName = proj.folderId?.let { foldersById[it]?.name } ?: "Root (No Folder)"
                    appendLine("- Project ID: ${proj.id} | Name: \"${proj.name}\" | Folder: \"$folderName\" | Archived: ${proj.isArchived}")
                }
            }
            appendLine()
            appendLine("--- Existing Tags (${state.tags.size}) ---")
            if (state.tags.isEmpty()) {
                appendLine("(None)")
            } else {
                state.tags.forEach { tag ->
                    appendLine("- Tag ID: ${tag.id} | Name: \"#${tag.name}\"")
                }
            }
            appendLine()
            appendLine("--- All Existing Tasks (${state.tasks.size}) ---")
            if (state.tasks.isEmpty()) {
                appendLine("(No tasks currently in the database)")
            } else {
                state.tasks.forEach { task ->
                    val status = if (task.isCompleted) "COMPLETED" else "PENDING"
                    val projName = task.projectId?.let { projectsById[it]?.name } ?: "Inbox (بدون مشروع)"
                    val folderName = task.projectId?.let { pid ->
                        projectsById[pid]?.folderId?.let { fid -> foldersById[fid]?.name }
                    }
                    val projDisplay = if (folderName != null) "$projName (Folder: $folderName)" else projName
                    val tagNames = task.tagIds.mapNotNull { tagsById[it]?.name }.joinToString(", ") { "#$it" }.ifBlank { "None" }
                    val dueDisplay = when {
                        task.isSomeday -> "Someday (لاحقاً)"
                        task.dueDateEpochDay == null -> "No Due Date"
                        task.dueDateEpochDay == todayEpoch -> "Today ($dateStr)"
                        task.dueDateEpochDay == todayEpoch + 1 -> "Tomorrow"
                        else -> {
                            val millis = DateUtils.millisFromEpochDay(task.dueDateEpochDay)
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(millis))
                        }
                    }
                    val alarmDisplay = task.reminderTime ?: "None"
                    appendLine("- TaskID: \"${task.id}\" | Title: \"${task.title}\" | Status: $status | Pomodoros: ${task.completedPomodoros}/${task.estimatedPomodoros} | Due: $dueDisplay | Alarm: $alarmDisplay | Priority: ${task.priority} | Project: \"$projDisplay\" | Tags: [$tagNames]")
                }
            }
            appendLine("=== END OF LIVE APP STATE SNAPSHOT ===")
        }
    }

    fun buildToolsJson(): JSONArray {
        val declarations = JSONArray()

        // 1. addTask
        declarations.put(
            JSONObject().apply {
                put("name", "addTask")
                put(
                    "description",
                    "Creates and saves a new task directly into the app's task list, assigns it to the matching project/folder and tag, sets its Pomodoro estimate, and schedules its alarm/reminder if reminderTime is provided."
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "title",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The task title (required).")
                                )
                                put(
                                    "estimatedPomodoros",
                                    JSONObject()
                                        .put("type", "INTEGER")
                                        .put("description", "Estimated number of Pomodoro focus sessions (1 to 20). Default is 1.")
                                )
                                put(
                                    "reminderTime",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional 24-hour alarm/reminder time in HH:mm format, e.g. '08:30', '14:00', '19:45'.")
                                )
                                put(
                                    "project",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional project or folder name/ID to assign the task to (e.g. 'الدراسة الجامعية 📚', 'تطوير المهارات 💻', 'الروتين اليومي ⚡', 'مشاريع جانبية 🚀', 'التعليم والتطوير', 'الحياة والروتين').")
                                )
                                put(
                                    "tag",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional tag name (e.g. 'مهم', 'مراجعة', 'قراءة', 'برمجة') or tag ID.")
                                )
                                put(
                                    "dueDateOffsetDays",
                                    JSONObject()
                                        .put("type", "INTEGER")
                                        .put("description", "Days offset from today: 0 for Today (default), 1 for Tomorrow, 2 for in two days, etc.")
                                )
                                put(
                                    "dueDateIso",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional explicit due date in YYYY-MM-DD format.")
                                )
                                put(
                                    "priority",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional task priority: 'HIGH', 'MEDIUM', 'LOW', or 'NONE'.")
                                )
                                put(
                                    "notes",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional additional notes or description for the task.")
                                )
                            }
                        )
                        put("required", JSONArray().put("title"))
                    }
                )
            }
        )

        // 2. updateTask
        declarations.put(
            JSONObject().apply {
                put("name", "updateTask")
                put(
                    "description",
                    "Updates an existing task (title, estimatedPomodoros, reminderTime alarm, due date, project, tag, priority, or notes) by its taskId or current title."
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "taskId",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The exact TaskID from the live context snapshot.")
                                )
                                put(
                                    "targetTitle",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Current title of the task to match if taskId is omitted.")
                                )
                                put(
                                    "newTitle",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional new title for the task.")
                                )
                                put(
                                    "estimatedPomodoros",
                                    JSONObject()
                                        .put("type", "INTEGER")
                                        .put("description", "Optional new Pomodoro estimate (1 to 20).")
                                )
                                put(
                                    "reminderTime",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional new 24-hour alarm time in HH:mm format (or 'NONE' to remove alarm).")
                                )
                                put(
                                    "project",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional project/folder name or ID to move the task to.")
                                )
                                put(
                                    "tag",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional tag name or ID.")
                                )
                                put(
                                    "dueDateOffsetDays",
                                    JSONObject()
                                        .put("type", "INTEGER")
                                        .put("description", "Optional new due date offset from today (0 = Today, 1 = Tomorrow).")
                                )
                                put(
                                    "dueDateIso",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional new due date in YYYY-MM-DD format.")
                                )
                                put(
                                    "priority",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional new priority: 'HIGH', 'MEDIUM', 'LOW', or 'NONE'.")
                                )
                                put(
                                    "notes",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional updated notes.")
                                )
                            }
                        )
                    }
                )
            }
        )

        // 3. completeTask
        declarations.put(
            JSONObject().apply {
                put("name", "completeTask")
                put(
                    "description",
                    "Marks an existing task as completed (or uncompleted) by its taskId or title."
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "taskId",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The TaskID of the task to complete.")
                                )
                                put(
                                    "targetTitle",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Title of the task to complete if taskId is omitted.")
                                )
                                put(
                                    "completed",
                                    JSONObject()
                                        .put("type", "BOOLEAN")
                                        .put("description", "True to mark completed (default), false to mark pending.")
                                )
                            }
                        )
                    }
                )
            }
        )

        // 4. deleteTask
        declarations.put(
            JSONObject().apply {
                put("name", "deleteTask")
                put(
                    "description",
                    "Deletes an existing task from the app database and cancels any scheduled alarm."
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "taskId",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The TaskID of the task to delete.")
                                )
                                put(
                                    "targetTitle",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Title of the task to delete if taskId is omitted.")
                                )
                            }
                        )
                    }
                )
            }
        )

        // 5. createProject
        declarations.put(
            JSONObject().apply {
                put("name", "createProject")
                put(
                    "description",
                    "Creates a new project/list in the app, optionally inside an existing or new folder."
                )
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "name",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Name of the new project.")
                                )
                                put(
                                    "colorHex",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional hex color such as '#E05246', '#3B7BF6', '#10B981', '#F59E0B', '#8B5CF6'.")
                                )
                                put(
                                    "folderName",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional folder name to place the project under (e.g. 'التعليم والتطوير', 'الحياة والروتين').")
                                )
                            }
                        )
                        put("required", JSONArray().put("name"))
                    }
                )
            }
        )

        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    suspend fun sendMessageWithAutonomousTools(
        apiKey: String,
        selectedModelId: String = MODEL_NAME,
        conversationHistory: List<TomatoChatMessage>,
        userMessage: String,
        executor: TomatoToolExecutor
    ): Result<TomatoChatMessage> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("يرجى إدخال مفتاح Gemini API أولاً."))
        }
        val activeModelId = TomatoGeminiModel.fromModelId(selectedModelId).modelId

        try {
            val contents = JSONArray()
            // Include recent conversation history (last 14 messages) for multi-turn context
            val recentHistory = conversationHistory.filter { !it.isError && it.text.isNotBlank() }.takeLast(14)
            for (msg in recentHistory) {
                contents.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }
            // Add current user message
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val allExecutedSummaries = mutableListOf<String>()
            var finalReplyText = ""
            var loopCount = 0
            val maxToolLoops = 4

            while (loopCount < maxToolLoops) {
                loopCount++
                val (currentState, currentTimer) = executor.getCurrentStateSnapshot()
                val systemPrompt = buildLiveAppContextPrompt(currentState, currentTimer)

                val requestPayload = JSONObject().apply {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemPrompt))
                        )
                    )
                    put("contents", contents)
                    put("tools", buildToolsJson())
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("temperature", 0.3)
                        }
                    )
                }

                val responseJson = executePostRequest(cleanKey, activeModelId, requestPayload)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    break
                }

                val firstCandidate = candidates.getJSONObject(0)
                val modelContent = firstCandidate.optJSONObject("content") ?: break
                val parts = modelContent.optJSONArray("parts") ?: break

                val functionCalls = mutableListOf<TomatoFunctionCall>()
                val textChunks = mutableListOf<String>()

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text") && !part.isNull("text")) {
                        val t = part.optString("text", "").trim()
                        if (t.isNotEmpty()) {
                            textChunks.add(t)
                        }
                    }
                    if (part.has("functionCall") && !part.isNull("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        val fnName = fc.optString("name", "")
                        val fnArgs = fc.optJSONObject("args") ?: JSONObject()
                        if (fnName.isNotEmpty()) {
                            functionCalls.add(TomatoFunctionCall(name = fnName, args = fnArgs))
                        }
                    }
                }

                if (textChunks.isNotEmpty()) {
                    finalReplyText = textChunks.joinToString("\n")
                }

                if (functionCalls.isEmpty()) {
                    // Model finished answering with text
                    break
                }

                // Append the model's content (with its functionCall parts) to contents
                contents.put(modelContent)

                // Execute all requested tool calls immediately on the live database
                val responseParts = JSONArray()
                for (call in functionCalls) {
                    val execResult = dispatchToolCall(call, executor)
                    allExecutedSummaries.add(execResult.humanSummary)
                    responseParts.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject().apply {
                                put("name", call.name)
                                put("response", JSONObject().put("result", execResult.resultJson))
                            }
                        )
                    )
                }

                // Feed functionResponse parts back to Gemini so it can confirm naturally
                contents.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", responseParts)
                    }
                )
            }

            if (finalReplyText.isBlank()) {
                finalReplyText = if (allExecutedSummaries.isNotEmpty()) {
                    "تم تنفيذ طلبك بنجاح وتحديث قائمة المهام:\n" +
                        allExecutedSummaries.joinToString("\n") { "• $it" }
                } else {
                    "تمت معالجة طلبك بنجاح."
                }
            }

            Result.success(
                TomatoChatMessage(
                    isUser = false,
                    text = finalReplyText,
                    executedActions = allExecutedSummaries
                )
            )
        } catch (e: UnknownHostException) {
            Result.failure(Exception("لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة والمحاولة مرة أخرى."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("انتهت مهلة الاتصال بخادم Gemini. يرجى المحاولة مرة أخرى."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun dispatchToolCall(
        call: TomatoFunctionCall,
        executor: TomatoToolExecutor
    ): TomatoToolExecutionResult {
        val args = call.args
        return when (call.name) {
            "addTask" -> {
                val title = args.optString("title", "").trim()
                val estPoms = args.optInt("estimatedPomodoros", 1).coerceIn(1, 20)
                val reminder = args.optString("reminderTime", "").trim().takeIf { it.isNotEmpty() && it != "null" && it != "NONE" }
                val project = args.optString("project", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val tag = args.optString("tag", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val dueEpochDay = resolveDueDateEpochDay(args) ?: DateUtils.todayEpochDay()
                val priority = parsePriority(args.optString("priority", "NONE"))
                val notes = args.optString("notes", "").trim().takeIf { it != "null" }.orEmpty()

                executor.executeAddTask(
                    title = title,
                    estimatedPomodoros = estPoms,
                    reminderTime = normalizeReminderTime(reminder),
                    projectQuery = project,
                    tagQuery = tag,
                    dueDateEpochDay = dueEpochDay,
                    priority = priority,
                    notes = notes
                )
            }

            "updateTask" -> {
                val taskId = args.optString("taskId", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val targetTitle = args.optString("targetTitle", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val newTitle = args.optString("newTitle", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val estPoms = if (args.has("estimatedPomodoros") && !args.isNull("estimatedPomodoros")) {
                    args.optInt("estimatedPomodoros", 1).coerceIn(1, 20)
                } else null
                val rawReminder = if (args.has("reminderTime") && !args.isNull("reminderTime")) {
                    args.optString("reminderTime", "").trim()
                } else null
                val normalizedReminder = when {
                    rawReminder == null -> null
                    rawReminder.equals("NONE", ignoreCase = true) || rawReminder.isEmpty() -> "NONE"
                    else -> normalizeReminderTime(rawReminder)
                }
                val project = args.optString("project", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val tag = args.optString("tag", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val dueEpochDay = resolveDueDateEpochDay(args)
                val priority = if (args.has("priority") && !args.isNull("priority")) {
                    parsePriority(args.optString("priority", "NONE"))
                } else null
                val notes = if (args.has("notes") && !args.isNull("notes")) {
                    args.optString("notes", "").trim()
                } else null

                executor.executeUpdateTask(
                    taskId = taskId,
                    targetTitle = targetTitle,
                    newTitle = newTitle,
                    estimatedPomodoros = estPoms,
                    reminderTime = normalizedReminder,
                    projectQuery = project,
                    tagQuery = tag,
                    dueDateEpochDay = dueEpochDay,
                    priority = priority,
                    notes = notes
                )
            }

            "completeTask" -> {
                val taskId = args.optString("taskId", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val targetTitle = args.optString("targetTitle", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val completed = if (args.has("completed") && !args.isNull("completed")) {
                    args.optBoolean("completed", true)
                } else true
                executor.executeCompleteTask(taskId, targetTitle, completed)
            }

            "deleteTask" -> {
                val taskId = args.optString("taskId", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val targetTitle = args.optString("targetTitle", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                executor.executeDeleteTask(taskId, targetTitle)
            }

            "createProject" -> {
                val name = args.optString("name", "").trim()
                val colorHex = args.optString("colorHex", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                val folderName = args.optString("folderName", "").trim().takeIf { it.isNotEmpty() && it != "null" }
                executor.executeCreateProject(name, colorHex, folderName)
            }

            else -> TomatoToolExecutionResult(
                functionName = call.name,
                resultJson = JSONObject().put("status", "error").put("message", "Unknown tool ${call.name}"),
                humanSummary = "أداة غير معروفة: ${call.name}"
            )
        }
    }

    private fun resolveDueDateEpochDay(args: JSONObject): Long? {
        if (args.has("dueDateIso") && !args.isNull("dueDateIso")) {
            val iso = args.optString("dueDateIso", "").trim()
            if (iso.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(iso)
                    if (parsed != null) {
                        return DateUtils.epochDayFromMillis(parsed.time)
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (args.has("dueDateOffsetDays") && !args.isNull("dueDateOffsetDays")) {
            val offset = args.optInt("dueDateOffsetDays", 0)
            return DateUtils.todayEpochDay() + offset
        }
        return null
    }

    private fun normalizeReminderTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        val match = Regex("(\\d{1,2}):(\\d{2})").find(clean) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)
    }

    private fun parsePriority(raw: String?): TaskPriority {
        return when (raw?.trim()?.uppercase(Locale.ENGLISH)) {
            "HIGH" -> TaskPriority.HIGH
            "MEDIUM" -> TaskPriority.MEDIUM
            "LOW" -> TaskPriority.LOW
            else -> TaskPriority.NONE
        }
    }

    private fun executePostRequest(apiKey: String, modelId: String, payload: JSONObject): JSONObject {
        val endpoint = buildEndpointUrl(modelId, apiKey)
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else ""

            if (statusCode !in 200..299) {
                val apiErrorMsg = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val friendlyError = when (statusCode) {
                    400, 401, 403 -> {
                        if (apiErrorMsg.contains("API_KEY", ignoreCase = true) ||
                            apiErrorMsg.contains("key", ignoreCase = true) ||
                            statusCode == 401 || statusCode == 403
                        ) {
                            "مفتاح Gemini API غير صالح أو غير مفعل. يرجى التأكد من المفتاح من أيقونة المفتاح بالأعلى."
                        } else {
                            "تعذر إتمام الطلب ($statusCode): ${apiErrorMsg.ifBlank { "تحقق من مفتاح API" }}"
                        }
                    }
                    429 -> "تم تجاوز حد الطلبات المسموح به مؤقتاً (429). يرجى الانتظار قليلاً ثم المحاولة."
                    else -> "خطأ من خادم Gemini ($statusCode): ${apiErrorMsg.ifBlank { "يرجى المحاولة لاحقاً" }}"
                }
                throw Exception(friendlyError)
            }

            return JSONObject(responseBody)
        } finally {
            conn.disconnect()
        }
    }
}
