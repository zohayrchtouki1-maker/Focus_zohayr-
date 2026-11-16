package com.example

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

@Synchronized
fun ensureFirebaseInitialized(context: Context): FirebaseApp {
    val appContext = context.applicationContext ?: context
    val clientKey = try {
        context.getString(R.string.firebase_client_api_key_p1) +
            context.getString(R.string.firebase_client_api_key_p2)
    } catch (_: Exception) {
        BuildConfig.FIREBASE_GOOGLE_API_KEY
    }

    val options = FirebaseOptions.Builder()
        .setApplicationId("1:452070073350:android:1d7162188b590aa5664f87")
        .setApiKey(clientKey)
        .setProjectId("focus-zohayr")
        .setStorageBucket("focus-zohayr.firebasestorage.app")
        .setGcmSenderId("452070073350")
        .build()

    if (FirebaseApp.getApps(appContext).isEmpty()) {
        return FirebaseApp.initializeApp(appContext, options)
    }

    val defaultApp = try {
        FirebaseApp.getInstance()
    } catch (_: Exception) {
        null
    }

    if (defaultApp != null &&
        defaultApp.options.projectId == "focus-zohayr" &&
        !defaultApp.options.apiKey.isNullOrBlank() &&
        defaultApp.options.apiKey != "YOUR_FIREBASE_WEB_API_KEY"
    ) {
        return defaultApp
    }

    return try {
        FirebaseApp.getInstance("focus-zohayr")
    } catch (_: Exception) {
        FirebaseApp.initializeApp(appContext, options, "focus-zohayr")
    }
}

object FirebaseInitializer {
    @Synchronized
    fun ensureInitialized(context: Context): FirebaseApp {
        return ensureFirebaseInitialized(context)
    }
}
