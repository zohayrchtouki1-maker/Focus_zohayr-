package com.example

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    @Synchronized
    fun ensureInitialized(context: Context): FirebaseApp {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return FirebaseApp.getInstance()
        }

        // 1. Try standard resource-based initialization first
        try {
            val defaultApp = FirebaseApp.initializeApp(appContext)
            if (defaultApp != null) {
                return defaultApp
            }
        } catch (_: Exception) {
        }

        // 2. Guaranteed manual fallback using exact values from app/google-services.json
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val appId = BuildConfig.FIREBASE_GOOGLE_APP_ID.ifBlank {
                "1:452070073350:android:1d7162188b590aa5664f87"
            }
            val apiKey = BuildConfig.FIREBASE_GOOGLE_API_KEY.ifBlank {
                BuildConfig.FIREBASE_WEB_API_KEY
            }
            val projectId = BuildConfig.FIREBASE_GOOGLE_PROJECT_ID.ifBlank {
                "focus-zohayr"
            }
            val storageBucket = BuildConfig.FIREBASE_GOOGLE_STORAGE_BUCKET.ifBlank {
                "focus-zohayr.firebasestorage.app"
            }
            val gcmSenderId = BuildConfig.FIREBASE_GOOGLE_GCM_SENDER_ID.ifBlank {
                "452070073350"
            }

            val options = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .setGcmSenderId(gcmSenderId)
                .build()

            return FirebaseApp.initializeApp(appContext, options)
        }

        return FirebaseApp.getInstance()
    }
}
