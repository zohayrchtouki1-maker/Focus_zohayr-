package com.example.data

import android.content.Context
import com.example.model.AppBackupState
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CloudSyncStatus {
    UNAUTHENTICATED,
    SYNCING,
    SYNCED,
    SYNC_FAILED
}

data class ActiveUserSession(
    val userId: String,
    val email: String
)

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without exception")
            )
        }
    }
}

class FocusRepository(private val context: Context) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AppBackupState::class.java).indent("  ")

    private val rootStateFile: File get() = File(context.filesDir, "focus_todo_state.json")

    private fun userStateFile(userId: String): File {
        val dir = File(context.filesDir, "users/$userId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "focus_todo_state.json")
    }

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance().apply {
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestoreSettings = settings
                } catch (_: Exception) {
                    // Settings already locked/configured on this instance
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    val firebaseProjectId: String
        get() = try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseApp.getInstance().options.projectId ?: "focus-zohayr"
        } catch (_: Exception) {
            "focus-zohayr"
        }

    fun getActiveSessionSync(): ActiveUserSession? {
        val user: FirebaseUser = firebaseAuth?.currentUser ?: return null
        return ActiveUserSession(
            userId = user.uid,
            email = user.email ?: ""
        )
    }

    private fun activeStateFile(): File {
        val session = getActiveSessionSync()
        return if (session != null) userStateFile(session.userId) else rootStateFile
    }

    /**
     * Real Firebase Authentication Sign Up (`createUserWithEmailAndPassword`) +
     * automatic migration of any existing local data into `users/{userId}` in Cloud Firestore.
     */
    suspend fun signUp(email: String, password: String): Result< Pair<ActiveUserSession, AppBackupState> > =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
            }
            if (password.length < 6) {
                return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
            }

            val auth = firebaseAuth
                ?: return@withContext Result.failure(IllegalStateException("FirebaseAuth is not initialized."))

            try {
                val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).awaitTask()
                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("User creation returned null user."))
                val session = ActiveUserSession(userId = user.uid, email = user.email ?: cleanEmail)
                val migratedState = migrateAndSyncOnLogin(session)
                Result.success(session to migratedState)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Real Firebase Authentication Sign In (`EmailAuthProvider.getCredential` + `signInWithCredential`) +
     * automatic migration/merge with `users/{userId}` in Cloud Firestore.
     */
    suspend fun signIn(email: String, password: String): Result< Pair<ActiveUserSession, AppBackupState> > =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
            }
            if (password.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
            }

            val auth = firebaseAuth
                ?: return@withContext Result.failure(IllegalStateException("FirebaseAuth is not initialized."))

            try {
                val credential = EmailAuthProvider.getCredential(cleanEmail, password)
                val authResult = auth.signInWithCredential(credential).awaitTask()
                val user = authResult.user
                    ?: return@withContext Result.failure(IllegalStateException("Sign-in returned null user."))
                val session = ActiveUserSession(userId = user.uid, email = user.email ?: cleanEmail)
                val syncedState = migrateAndSyncOnLogin(session)
                Result.success(session to syncedState)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            firebaseAuth?.signOut()
        } catch (_: Exception) {
        }
    }

    /**
     * Attaches a real-time Firestore snapshot listener on `users/{userId}` so changes made on
     * any other phone or device sync automatically in real time.
     */
    fun listenToUserCloudState(
        userId: String,
        onRemoteStateChanged: (AppBackupState) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: error.message ?: "Firestore listener error")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val stateJson = snapshot.getString("stateJson")
                    if (!stateJson.isNullOrBlank()) {
                        val parsed = parseBackupJson(stateJson)?.let { cleanLegacySamples(it) }
                        if (parsed != null) {
                            saveStateInternal(parsed, userStateFile(userId))
                            onRemoteStateChanged(parsed)
                        }
                    }
                }
            }
    }

    /**
     * On first login or sign-in, fetches `users/{userId}` from Cloud Firestore, merges any
     * pre-login local data from `focus_todo_state.json` so nothing is lost, caches locally,
     * and writes the merged state to `users/{userId}` in Cloud Firestore.
     */
    private suspend fun migrateAndSyncOnLogin(session: ActiveUserSession): AppBackupState {
        val db = firestore ?: throw IllegalStateException("FirebaseFirestore is not initialized.")
        val docRef = db.collection("users").document(session.userId)

        val localAnonymousState = readStateFromFile(rootStateFile)
        val localUserState = readStateFromFile(userStateFile(session.userId))

        // Fetch from server first (falling back to Firestore offline cache only if device is offline)
        val remoteSnapshot = try {
            withTimeout(12_000L) {
                docRef.get(Source.SERVER).awaitTask()
            }
        } catch (serverEx: Exception) {
            try {
                docRef.get(Source.CACHE).awaitTask()
            } catch (_: Exception) {
                throw serverEx
            }
        }

        val remoteState: AppBackupState? = if (remoteSnapshot.exists()) {
            remoteSnapshot.getString("stateJson")?.let { parseBackupJson(it) }
        } else {
            null
        }

        val merged = when {
            remoteState != null && localUserState != null -> mergeStates(remoteState, localUserState)
            remoteState != null && localAnonymousState != null && localAnonymousState.tasks.isNotEmpty() ->
                mergeStates(remoteState, localAnonymousState)
            remoteState != null -> remoteState
            localUserState != null && localAnonymousState != null -> mergeStates(localUserState, localAnonymousState)
            localUserState != null -> localUserState
            localAnonymousState != null -> localAnonymousState
            else -> SeedData.createInitialState()
        }

        val cleaned = cleanLegacySamples(merged)
        saveStateInternal(cleaned, userStateFile(session.userId))

        // Clear migrated anonymous tasks from root file so they don't re-merge into another account
        if (localAnonymousState != null && localAnonymousState.tasks.isNotEmpty()) {
            saveStateInternal(SeedData.createInitialState(), rootStateFile)
        }

        // Write to Cloud Firestore users/{userId} and await completion
        pushToFirestoreOrThrow(session, cleaned)
        return cleaned
    }

    /**
     * Pushes `state` to `users/{userId}` in Cloud Firestore and waits for server acknowledgment.
     */
    suspend fun pushStateToCloud(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        val session = getActiveSessionSync()
            ?: return@withContext Result.failure(IllegalStateException("Not signed in."))
        try {
            pushToFirestoreOrThrow(session, state)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Manual "Sync Now": Pulls latest `users/{userId}` from Cloud Firestore server, merges any
     * local state, and writes back to Cloud Firestore `users/{userId}`, returning the synced state
     * or throwing the exact Firebase error if sync fails.
     */
    suspend fun forceCloudSyncNow(currentLocalState: AppBackupState): Result<AppBackupState> =
        withContext(Dispatchers.IO) {
            val session = getActiveSessionSync()
                ?: return@withContext Result.failure(IllegalStateException("Not signed in. Please sign in first."))
            val db = firestore
                ?: return@withContext Result.failure(IllegalStateException("FirebaseFirestore is not initialized."))

            try {
                val docRef = db.collection("users").document(session.userId)
                val serverSnap = withTimeout(12_000L) {
                    docRef.get(Source.SERVER).awaitTask()
                }

                val remoteState = if (serverSnap.exists()) {
                    serverSnap.getString("stateJson")?.let { parseBackupJson(it) }
                } else {
                    null
                }

                val remoteUpdatedAt = if (serverSnap.exists()) {
                    serverSnap.getLong("updatedAtEpochMillis") ?: 0L
                } else {
                    0L
                }

                val finalState = if (remoteState != null && remoteUpdatedAt > 0L) {
                    // Use currentLocalState as primary so local edits aren't lost, while pulling any new remote items
                    cleanLegacySamples(mergeStates(currentLocalState, remoteState))
                } else {
                    cleanLegacySamples(currentLocalState)
                }

                saveStateInternal(finalState, userStateFile(session.userId))
                pushToFirestoreOrThrow(session, finalState)
                Result.success(finalState)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun pushToFirestoreOrThrow(session: ActiveUserSession, state: AppBackupState) {
        val db = firestore ?: throw IllegalStateException("FirebaseFirestore is not initialized.")
        val now = System.currentTimeMillis()
        val stateJsonString = adapter.toJson(state)

        val dataMap = mapOf(
            "userId" to session.userId,
            "email" to session.email,
            "updatedAtEpochMillis" to now,
            "taskCount" to state.tasks.size,
            "projectCount" to state.projects.size,
            "completedTaskCount" to state.tasks.count { it.isCompleted },
            "focusRecordCount" to state.focusRecords.size,
            "stateJson" to stateJsonString
        )

        withTimeout(12_000L) {
            db.collection("users")
                .document(session.userId)
                .set(dataMap)
                .awaitTask()
        }
    }

    private fun mergeStates(primary: AppBackupState, secondary: AppBackupState): AppBackupState {
        val mergedTasks = (primary.tasks + secondary.tasks)
            .distinctBy { it.id }
        val mergedProjects = (primary.projects + secondary.projects)
            .distinctBy { it.id }
        val mergedFolders = (primary.folders + secondary.folders)
            .distinctBy { it.id }
        val mergedTags = (primary.tags + secondary.tags)
            .distinctBy { it.id }
        val mergedRecords = (primary.focusRecords + secondary.focusRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMillis }

        return primary.copy(
            tasks = mergedTasks,
            projects = mergedProjects,
            folders = mergedFolders,
            tags = mergedTags,
            focusRecords = mergedRecords
        )
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

    private fun cleanLegacySamples(loaded: AppBackupState): AppBackupState {
        val cleanedTasks = loaded.tasks.filterNot { it.id in SeedData.legacySampleTaskIds }
        val cleanedRecords = loaded.focusRecords.filterNot { it.id in SeedData.legacySampleRecordIds }
        return if (cleanedTasks.size != loaded.tasks.size || cleanedRecords.size != loaded.focusRecords.size) {
            loaded.copy(tasks = cleanedTasks, focusRecords = cleanedRecords)
        } else {
            loaded
        }
    }

    suspend fun loadState(): AppBackupState = withContext(Dispatchers.IO) {
        val targetFile = activeStateFile()
        try {
            if (targetFile.exists()) {
                val raw = targetFile.readText(Charsets.UTF_8)
                adapter.fromJson(raw)?.let { loaded ->
                    val cleaned = cleanLegacySamples(loaded)
                    if (cleaned !== loaded) {
                        saveStateInternal(cleaned, targetFile)
                    }
                    return@withContext cleaned
                }
            }
        } catch (_: Exception) {
            // Fallback to fresh seed data if file is corrupted
        }
        val initial = SeedData.createInitialState()
        saveStateInternal(initial, targetFile)
        initial
    }

    suspend fun saveState(state: AppBackupState): Result<Unit> = withContext(Dispatchers.IO) {
        val targetFile = activeStateFile()
        saveStateInternal(state, targetFile)
        val session = getActiveSessionSync() ?: return@withContext Result.success(Unit)
        try {
            pushToFirestoreOrThrow(session, state)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveStateInternal(state: AppBackupState, targetFile: File = activeStateFile()) {
        try {
            targetFile.parentFile?.mkdirs()
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeText(adapter.toJson(state), Charsets.UTF_8)
            tmpFile.renameTo(targetFile)
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
