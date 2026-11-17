package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class FocusBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        val appCtx = applicationContext
        if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
            return
        }

        // If the app is Focus_zohayr itself, in the user's Whitelist, or in the Safe-System Exclusion List, allow it
        if (pkg == appCtx.packageName ||
            pkg in StrictAppBlockerManager.getWhitelistedPackages(appCtx) ||
            StrictAppBlockerManager.isSafeSystemExcludedPackage(appCtx, pkg)
        ) {
            return
        }

        // When a focus session is active and the user opens an app NOT on the Whitelist,
        // exit that app smoothly and cleanly straight to the phone's Home Screen.
        if (StrictAppBlockerManager.shouldBlockPackageImmediately(appCtx, pkg)) {
            try {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() {
    }
}
