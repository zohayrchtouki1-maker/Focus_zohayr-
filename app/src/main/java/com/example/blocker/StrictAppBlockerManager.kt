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
import android.telecom.TelecomManager
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
    const val KEY_ACTIVE_FOCUS_SESSION_START_MILLIS = "strict_focus_session_start_millis"
    const val KEY_ACTIVE_FOCUS_IS_STOPWATCH = "strict_focus_session_is_stopwatch"
    const val KEY_ACTIVE_FOCUS_TASK_TITLE = "strict_focus_task_title"

    const val EXTRA_STRICT_BLOCK_TRIGGERED = "extra_strict_block_triggered"
    const val STRICT_BLOCK_MESSAGE_AR = "🔒 الوضع الصارم مفعل! ممنوع فتح التطبيقات حتى تنتهي جلسة التركيز"

    private val _blockedEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val blockedEvents: SharedFlow<String> = _blockedEvents.asSharedFlow()

    @Volatile
    private var cachedInstalledApps: List<InstalledAppInfo>? = null

    // Always-blocked system settings, package installers, and phone managers during active strict focus
    // so permissions cannot be revoked or the app force-stopped during a session.
    private val STRICTLY_FORBIDDEN_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.transsion.packageinstaller",
        "com.transsion.phonemanager",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    // Static Safe-System Exclusion List (NEVER block or trigger on these)
    private val STATIC_SAFE_SYSTEM_EXCLUSIONS = setOf(
        "android",
        "com.android.systemui",
        "com.transsion.XOSLauncher",
        "com.transsion.hilauncher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.sh.smart.caller"
    )

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
        countMode: TimerCountMode,
        activeTaskTitle: String = "جلسة تركيز",
        stopwatchStartTimeEpochMillis: Long? = null
    ) {
        val isFocusRunning = isRunning && phase == TimerPhase.FOCUS
        val isStopwatch = countMode == TimerCountMode.STOPWATCH
        val endMillis = if (isFocusRunning) {
            timerEndTimeEpochMillis ?: (System.currentTimeMillis() + 25 * 60_000L)
        } else {
            0L
        }
        val startMillis = stopwatchStartTimeEpochMillis ?: System.currentTimeMillis()
        val editor = getPrefs(context).edit()
            .putBoolean(KEY_ACTIVE_FOCUS_SESSION_RUNNING, isFocusRunning)
            .putLong(KEY_ACTIVE_FOCUS_SESSION_END_MILLIS, endMillis)
            .putLong(KEY_ACTIVE_FOCUS_SESSION_START_MILLIS, startMillis)
            .putBoolean(KEY_ACTIVE_FOCUS_IS_STOPWATCH, isStopwatch)
            .putString(KEY_ACTIVE_FOCUS_TASK_TITLE, activeTaskTitle.ifBlank { "جلسة تركيز" })
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
        return ComponentName(context, FocusDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(ComponentName(context.applicationContext, FocusDeviceAdminReceiver::class.java)) == true
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
        val componentName = ComponentName(context, FocusDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "مطلوب لمنع حذف التطبيق أو تجاوز الحظر الصارم أثناء جلسات التركيز."
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

    /**
     * Checks the Strict Safe-System Exclusion List (NEVER block or trigger on these):
     * - context.packageName ("com.aistudio.focustodo.arpmzq")
     * - "com.android.systemui"
     * - "android"
     * - Active default Home Launcher (resolved dynamically + XOSLauncher / launcher3)
     * - Default Phone / Dialer & Emergency packages (TelecomManager.getDefaultDialerPackage(), incallui, google dialer)
     * - Active Input Method / Keyboard packages (Settings.Secure.DEFAULT_INPUT_METHOD + InputMethodManager)
     */
    fun isSafeSystemExcludedPackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return true
        // Explicitly forbidden packages (Settings, Package Installers, Phone Manager) are NEVER excluded
        if (packageName in STRICTLY_FORBIDDEN_PACKAGES) return false

        if (packageName == context.packageName) return true
        if (packageName in STATIC_SAFE_SYSTEM_EXCLUSIONS) return true

        // Resolve active default Home Launcher dynamically
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolvedHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )?.activityInfo?.packageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(
                    homeIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName
            }
            if (!resolvedHome.isNullOrBlank() && resolvedHome == packageName) {
                return true
            }
        } catch (_: Exception) {
        }

        // Resolve default Phone / Dialer package dynamically
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecom?.defaultDialerPackage
            if (!defaultDialer.isNullOrBlank() && defaultDialer == packageName) {
                return true
            }
        } catch (_: Exception) {
        }

        // Resolve active Input Method / Keyboard package dynamically
        try {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ).orEmpty()
            val imePkg = defaultIme.substringBefore('/')
            if (imePkg.isNotBlank() && imePkg == packageName) {
                return true
            }
        } catch (_: Exception) {
        }

        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm?.enabledInputMethodList?.any { it.packageName == packageName } == true) {
                return true
            }
        } catch (_: Exception) {
        }

        return false
    }

    /**
     * Exact Trigger Condition:
     * Trigger enforcement ONLY when Strict Mode is ON, a Pomodoro Work/Focus session is actively
     * counting down, [foregroundPackage] is NOT in the Safe-System Exclusion List, AND
     * [foregroundPackage] is NOT in the user's selected whitelistedPackages set (while always
     * blocking com.android.settings, package installers, and com.transsion.phonemanager).
     */
    fun shouldBlockPackageImmediately(context: Context, foregroundPackage: String): Boolean {
        if (foregroundPackage.isBlank()) return false
        if (!isStrictFocusSessionActivelyRunning(context)) return false

        // Always block Settings, Package Installers, and Phone Manager during active strict focus
        if (foregroundPackage in STRICTLY_FORBIDDEN_PACKAGES) return true

        // Never block or trigger on the Safe-System Exclusion List (Focus_zohayr, SystemUI, Home Launcher, Dialer, Keyboard)
        if (isSafeSystemExcludedPackage(context, foregroundPackage)) return false

        // Never block user-whitelisted apps
        val whitelisted = getWhitelistedPackages(context)
        if (foregroundPackage in whitelisted) return false

        // Default-Deny: block every other non-whitelisted app
        return true
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

        val excludedSelfAndSettings = STRICTLY_FORBIDDEN_PACKAGES + context.packageName

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
