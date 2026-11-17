package com.example.blocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.FocusRepository
import com.example.model.TimerCountMode
import com.example.model.TimerPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val iconBitmap: ImageBitmap?
)

data class StrictBlockerPermissionStatus(
    val isDeviceAdminGranted: Boolean = false,
    val isAccessibilityServiceGranted: Boolean = false
) {
    val isFullyReady: Boolean
        get() = isDeviceAdminGranted && isAccessibilityServiceGranted
}

object StrictAppBlockerManager {
    const val KEY_STRICT_BLOCKER_ENABLED = "strict_blocker_enabled"
    const val KEY_WHITELISTED_PACKAGES = "strict_blocker_whitelisted_packages"
    const val KEY_ACTIVE_FOCUS_SESSION_RUNNING = "strict_focus_session_active"
    const val KEY_ACTIVE_FOCUS_SESSION_END_MILLIS = "strict_focus_session_end_millis"
    const val KEY_ACTIVE_FOCUS_IS_STOPWATCH = "strict_focus_session_is_stopwatch"

    const val EXTRA_STRICT_BLOCK_TRIGGERED = "extra_strict_block_triggered"
    const val STRICT_BLOCK_MESSAGE_AR = "🔒 الوضع الصارم مفعل! ممنوع فتح التطبيقات حتى تنتهي جلسة التركيز"

    private val _blockedEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val blockedEvents: SharedFlow<String> = _blockedEvents.asSharedFlow()

    @Volatile
    private var cachedInstalledApps: List<InstalledAppInfo>? = null

    // Always-blocked system settings & package management apps during active strict focus
    private val STRICTLY_FORBIDDEN_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.transsion.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    // Essential system UI & emergency/phone dialer packages allowed so the device remains safe
    private val ESSENTIAL_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.sh.smart.caller",
        "com.transsion.smartmessage"
    )

    fun notifyBlockedAttempt(packageName: String) {
        _blockedEvents.tryEmit(packageName)
    }

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(FocusRepository.PREFS_NAME, Context.MODE_PRIVATE)

    fun isStrictBlockerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STRICT_BLOCKER_ENABLED, false)
    }

    fun setStrictBlockerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_STRICT_BLOCKER_ENABLED, enabled)
            .commit()
    }

    fun getWhitelistedPackages(context: Context): Set<String> {
        val raw = getPrefs(context).getStringSet(KEY_WHITELISTED_PACKAGES, emptySet()).orEmpty()
        return raw.toSet()
    }

    fun setWhitelistedPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit()
            .putStringSet(KEY_WHITELISTED_PACKAGES, packages.toSet())
            .commit()
    }

    fun toggleWhitelistedPackage(context: Context, packageName: String): Set<String> {
        val current = getWhitelistedPackages(context).toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setWhitelistedPackages(context, current)
        return current
    }

    fun syncTimerState(
        context: Context,
        isRunning: Boolean,
        phase: TimerPhase,
        strictMode: Boolean,
        timerEndTimeEpochMillis: Long?,
        countMode: TimerCountMode
    ) {
        val isFocusRunning = isRunning && phase == TimerPhase.FOCUS
        val isStopwatch = countMode == TimerCountMode.STOPWATCH
        val endMillis = if (isFocusRunning) {
            timerEndTimeEpochMillis ?: (System.currentTimeMillis() + 25 * 60_000L)
        } else {
            0L
        }
        val editor = getPrefs(context).edit()
            .putBoolean(KEY_ACTIVE_FOCUS_SESSION_RUNNING, isFocusRunning)
            .putLong(KEY_ACTIVE_FOCUS_SESSION_END_MILLIS, endMillis)
            .putBoolean(KEY_ACTIVE_FOCUS_IS_STOPWATCH, isStopwatch)
        if (strictMode) {
            editor.putBoolean(KEY_STRICT_BLOCKER_ENABLED, true)
        }
        editor.commit()
    }

    fun isStrictFocusSessionActivelyRunning(context: Context): Boolean {
        val sp = getPrefs(context)
        val strictEnabled = sp.getBoolean(KEY_STRICT_BLOCKER_ENABLED, false)
        val focusRunning = sp.getBoolean(KEY_ACTIVE_FOCUS_SESSION_RUNNING, false)
        if (!strictEnabled || !focusRunning) return false

        val isStopwatch = sp.getBoolean(KEY_ACTIVE_FOCUS_IS_STOPWATCH, false)
        if (isStopwatch) return true

        val endMillis = sp.getLong(KEY_ACTIVE_FOCUS_SESSION_END_MILLIS, 0L)
        if (endMillis <= 0L) return false
        return System.currentTimeMillis() < endMillis
    }

    fun getDeviceAdminComponent(context: Context): ComponentName {
        return ComponentName(context.applicationContext, FocusDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(getDeviceAdminComponent(context)) == true
        } catch (_: Exception) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context.applicationContext, FocusBlockerAccessibilityService::class.java)
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
            if (enabledList.any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                        it.resolveInfo?.serviceInfo?.name == FocusBlockerAccessibilityService::class.java.name
                }
            ) {
                return true
            }
        } catch (_: Exception) {
        }

        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val flatFull = expectedComponent.flattenToString()
            val flatShort = expectedComponent.flattenToShortString()
            enabledServices.split(':').any { entry ->
                entry.equals(flatFull, ignoreCase = true) || entry.equals(flatShort, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getPermissionStatus(context: Context): StrictBlockerPermissionStatus {
        return StrictBlockerPermissionStatus(
            isDeviceAdminGranted = isDeviceAdminActive(context),
            isAccessibilityServiceGranted = isAccessibilityServiceEnabled(context)
        )
    }

    fun createEnableDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getDeviceAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "يلزم تفعيل مشرف الجهاز لقفل الشاشة فورًا ومنع فتح التطبيقات غير المسموح بها أثناء جلسات التركيز الصارمة."
            )
        }
    }

    fun openDeviceAdminActivation(context: Context) {
        try {
            val intent = createEnableDeviceAdminIntent(context).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun isLauncherHomePackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val knownLaunchers = setOf(
            "com.transsion.XOSLauncher",
            "com.transsion.hilauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home"
        )
        if (packageName in knownLaunchers) return true
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolves.any { it.activityInfo?.packageName == packageName }
        } catch (_: Exception) {
            false
        }
    }

    private fun isInputMethodPackage(context: Context, packageName: String): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Evaluates whether [foregroundPackage] must be immediately blocked and screen-locked
     * under Default-Deny Whitelist Mode during an active Focus session.
     */
    fun shouldBlockPackageImmediately(context: Context, foregroundPackage: String): Boolean {
        if (foregroundPackage.isBlank()) return false
        if (!isStrictFocusSessionActivelyRunning(context)) return false

        // 1. Focus_zohayr itself is always allowed
        if (foregroundPackage == context.packageName) return false

        // 2. Android Settings & Package Installers are ALWAYS blocked during strict focus
        //    to prevent force-stopping, revoking Accessibility, or deactivating Device Admin
        if (foregroundPackage in STRICTLY_FORBIDDEN_PACKAGES) return true

        // 3. User-whitelisted apps are allowed
        val whitelisted = getWhitelistedPackages(context)
        if (foregroundPackage in whitelisted) return false

        // 4. Essential system UI, emergency/phone dialer, and active keyboard IMEs are allowed
        if (foregroundPackage in ESSENTIAL_SYSTEM_PACKAGES) return false
        if (isInputMethodPackage(context, foregroundPackage)) return false

        // 5. Launcher home screen is handled with immediate bounce-back to Focus_zohayr
        if (isLauncherHomePackage(context, foregroundPackage)) return false

        // 6. Default-Deny: EVERY other app on the device is strictly blocked!
        return true
    }

    fun lockScreenNowIfDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null && dpm.isAdminActive(getDeviceAdminComponent(context))) {
                dpm.lockNow()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadInstalledLaunchableApps(
        context: Context,
        forceRefresh: Boolean = false
    ): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedInstalledApps?.let { if (it.isNotEmpty()) return@withContext it }
        }

        val pm = context.applicationContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val excludedSelfAndSettings = setOf(
            context.packageName,
            "com.android.settings"
        )

        val appsByPackage = linkedMapOf<String, InstalledAppInfo>()
        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg in excludedSelfAndSettings) continue
            if (appsByPackage.containsKey(pkg)) continue

            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
            } catch (_: Exception) {
                pkg
            }

            val iconBmp = try {
                val drawable = ri.loadIcon(pm)
                drawableToImageBitmap(drawable)
            } catch (_: Exception) {
                null
            }

            appsByPackage[pkg] = InstalledAppInfo(
                packageName = pkg,
                appName = label,
                iconBitmap = iconBmp
            )
        }

        val sorted = appsByPackage.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
        )
        cachedInstalledApps = sorted
        sorted
    }

    private fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(144)
        val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(144)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
