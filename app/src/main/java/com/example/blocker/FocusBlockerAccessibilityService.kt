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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank()) return

        val appCtx = applicationContext

        // If focus session is not actively running, ensure overlay is dismissed and return immediately
        if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
            StrictBlockingOverlayController.dismissOverlay(appCtx)
            return
        }

        // If user returned to Focus_zohayr or an explicitly whitelisted app, dismiss the blocking overlay
        if (pkg == appCtx.packageName || pkg in StrictAppBlockerManager.getWhitelistedPackages(appCtx)) {
            StrictBlockingOverlayController.dismissOverlay(appCtx)
            return
        }

        // Never block or trigger on the Safe-System Exclusion List (SystemUI, Home Launcher, Dialer, Keyboard)
        if (StrictAppBlockerManager.isSafeSystemExcludedPackage(appCtx, pkg)) {
            return
        }

        // Exact Trigger Condition: non-whitelisted app (or Settings / Package Installer / Phone Manager)
        if (StrictAppBlockerManager.shouldBlockPackageImmediately(appCtx, pkg)) {
            enforceDualOverlayAndBounceBack(pkg)
        }
    }

    private fun enforceDualOverlayAndBounceBack(blockedPackage: String) {
        val now = System.currentTimeMillis()
        val appCtx = applicationContext

        // 1. Show the unbypassable Full-Screen Blocking Overlay (WindowManager TYPE_APPLICATION_OVERLAY)
        StrictBlockingOverlayController.showOverlay(appCtx, blockedPackage)

        // 2. Immediately bounce the user back to Focus_zohayr
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (_: Exception) {
        }

        val shouldShowToast = (now - lastEnforcementMillis) > 1000L
        lastEnforcementMillis = now

        StrictAppBlockerManager.notifyBlockedAttempt(blockedPackage)

        mainHandler.post {
            if (shouldShowToast) {
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
                val launchIntent = Intent(this@FocusBlockerAccessibilityService, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER, true)
                    putExtra(StrictAppBlockerManager.EXTRA_STRICT_BLOCK_TRIGGERED, true)
                }
                startActivity(launchIntent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() {
        StrictBlockingOverlayController.dismissOverlay(applicationContext)
    }

    override fun onDestroy() {
        StrictBlockingOverlayController.dismissOverlay(applicationContext)
        super.onDestroy()
    }
}
