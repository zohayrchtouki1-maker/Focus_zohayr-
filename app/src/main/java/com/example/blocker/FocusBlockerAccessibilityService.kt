package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.example.data.FocusRepository
import com.example.model.AppLanguage
import com.example.notifications.FocusNotificationManager

class FocusBlockerAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastAlertElapsedMs: Long = 0L

    @Volatile
    private var lastAlertPkg: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        val appCtx = applicationContext
        if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
            return
        }

        // If the app is FocusTomato itself, in the user's Whitelist, or in the Safe-System Exclusion List, allow it
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

            val now = android.os.SystemClock.elapsedRealtime()
            val shouldNotify = (pkg != lastAlertPkg) || (now - lastAlertElapsedMs >= 2500L)
            if (shouldNotify) {
                lastAlertPkg = pkg
                lastAlertElapsedMs = now
                val label = resolveAppLabel(appCtx, pkg)
                val langName = appCtx
                    .getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("setting_language", AppLanguage.AR.name)
                val isArabic = langName != AppLanguage.EN.name
                FocusNotificationManager.showAppBlockerTriggeredNotification(
                    context = appCtx,
                    blockedPackageName = pkg,
                    appLabel = label,
                    isArabic = isArabic
                )
            }
        }
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    override fun onInterrupt() {
    }
}
