package com.example

import android.app.Application

class FocusTodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
    }
}
