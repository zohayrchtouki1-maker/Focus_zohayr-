package com.example.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.MainActivity
import com.example.notifications.FocusNotificationManager

class FocusBlockerAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastEnforcementMillis: Long = 0L
    private var pendingLauncherBounceRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        val appCtx = applicationContext
        if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
            cancelPendingLauncherBounce()
            return
        }

        // If user returns to Focus_zohayr or a whitelisted app, cancel any pending launcher bounce
        if (pkg == appCtx.packageName || pkg in StrictAppBlockerManager.getWhitelistedPackages(appCtx)) {
            cancelPendingLauncherBounce()
            return
        }

        // If user lands on the Launcher Home Screen during a strict focus session,
        // allow it only briefly (160ms) to bounce right back to Focus_zohayr so apps cannot be launched
        if (StrictAppBlockerManager.isLauncherHomePackage(appCtx, pkg)) {
            scheduleLauncherBounceBack()
            return
        }

        // Default-Deny Whitelist Enforcement: check if foreground package is blocked
        if (StrictAppBlockerManager.shouldBlockPackageImmediately(appCtx, pkg)) {
            cancelPendingLauncherBounce()
            enforceImmediateBlockAndScreenLock(pkg)
        }
    }

    private fun scheduleLauncherBounceBack() {
        cancelPendingLauncherBounce()
        val runnable = Runnable {
            val appCtx = applicationContext
            if (StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
                relaunchFocusAppToFront(showWarningToast = false)
            }
        }
        pendingLauncherBounceRunnable = runnable
        mainHandler.postDelayed(runnable, 160L)
    }

    private fun cancelPendingLauncherBounce() {
        pendingLauncherBounceRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLauncherBounceRunnable = null
    }

    private fun enforceImmediateBlockAndScreenLock(blockedPackage: String) {
        val now = System.currentTimeMillis()
        val appCtx = applicationContext

        // 1. Immediately call DevicePolicyManager.lockNow() (if Device Admin is active)
        //    to instantly turn off and lock the phone screen
        StrictAppBlockerManager.lockScreenNowIfDeviceAdminActive(appCtx)

        // 2. Simultaneously fire GLOBAL_ACTION_HOME so the blocked app is immediately dismissed
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (_: Exception) {
        }

        // 3. Bring Focus_zohayr back to the front and show the strict mode warning toast/banner
        val shouldShowToast = (now - lastEnforcementMillis) > 900L
        lastEnforcementMillis = now

        StrictAppBlockerManager.notifyBlockedAttempt(blockedPackage)
        relaunchFocusAppToFront(showWarningToast = shouldShowToast)
    }

    private fun relaunchFocusAppToFront(showWarningToast: Boolean) {
        val appCtx = applicationContext
        mainHandler.post {
            if (showWarningToast) {
                try {
                    Toast.makeText(
                        appCtx,
                        StrictAppBlockerManager.STRICT_BLOCK_MESSAGE_AR,
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                }
            }
            try {
                val launchIntent = Intent(appCtx, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                    putExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER, true)
                    putExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED, true)
                }
                appCtx.startActivity(launchIntent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() {
        cancelPendingLauncherBounce()
    }

    override fun onDestroy() {
        cancelPendingLauncherBounce()
        super.onDestroy()
    }
}
