package com.example.blocker

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.notifications.FocusNotificationManager

object StrictBlockingOverlayController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var taskTitleTextView: TextView? = null
    private var windowManager: WindowManager? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            val view = overlayView ?: return
            val ctx = view.context.applicationContext
            if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(ctx)) {
                dismissOverlay(ctx)
                return
            }
            timerTextView?.text = StrictAppBlockerManager.getFormattedRemainingFocusTime(ctx)
            taskTitleTextView?.text = "المهمة الحالية: ${StrictAppBlockerManager.getActiveFocusTaskTitle(ctx)}"
            mainHandler.postDelayed(this, 1000L)
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun showOverlay(context: Context, blockedPackage: String) {
        val appCtx = context.applicationContext
        mainHandler.post {
            if (!canDrawOverlays(appCtx)) return@post
            if (!StrictAppBlockerManager.isStrictFocusSessionActivelyRunning(appCtx)) {
                dismissOverlayInternal()
                return@post
            }

            if (overlayView != null) {
                timerTextView?.text = StrictAppBlockerManager.getFormattedRemainingFocusTime(appCtx)
                taskTitleTextView?.text = "المهمة الحالية: ${StrictAppBlockerManager.getActiveFocusTaskTitle(appCtx)}"
                return@post
            }

            try {
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val rootContainer = object : FrameLayout(appCtx) {
                    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                            return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }.apply {
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setBackgroundColor(Color.parseColor("#121215"))
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setPadding(dp(appCtx, 24), dp(appCtx, 32), dp(appCtx, 24), dp(appCtx, 32))
                }

                val cardLayout = LinearLayout(appCtx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(appCtx, 20).toFloat()
                        setColor(Color.parseColor("#1C1C21"))
                        setStroke(dp(appCtx, 1), Color.parseColor("#2E3446"))
                    }
                    setPadding(dp(appCtx, 24), dp(appCtx, 32), dp(appCtx, 24), dp(appCtx, 32))
                }

                // Shield Icon Badge (🛡️)
                val shieldBadge = TextView(appCtx).apply {
                    text = "🛡️"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#252836"))
                        setStroke(dp(appCtx, 2), Color.parseColor("#E05246"))
                    }
                    width = dp(appCtx, 88)
                    height = dp(appCtx, 88)
                }
                cardLayout.addView(
                    shieldBadge,
                    LinearLayout.LayoutParams(dp(appCtx, 88), dp(appCtx, 88)).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dp(appCtx, 20)
                    }
                )

                // Primary Headline
                val titleView = TextView(appCtx).apply {
                    text = "التطبيق محظور في الوضع الصارم"
                    setTextColor(Color.parseColor("#F2F2F5"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                cardLayout.addView(
                    titleView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(appCtx, 10)
                    }
                )

                // Active Task Subtitle
                val taskView = TextView(appCtx).apply {
                    text = "المهمة الحالية: ${StrictAppBlockerManager.getActiveFocusTaskTitle(appCtx)}"
                    setTextColor(Color.parseColor("#3B7BF6"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                taskTitleTextView = taskView
                cardLayout.addView(
                    taskView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(appCtx, 18)
                    }
                )

                // Live Countdown Timer Box (MM:SS)
                val timerBox = TextView(appCtx).apply {
                    text = StrictAppBlockerManager.getFormattedRemainingFocusTime(appCtx)
                    setTextColor(Color.parseColor("#E05246"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(appCtx, 14).toFloat()
                        setColor(Color.parseColor("#14161D"))
                        setStroke(dp(appCtx, 1), Color.parseColor("#26262C"))
                    }
                    setPadding(dp(appCtx, 28), dp(appCtx, 14), dp(appCtx, 28), dp(appCtx, 14))
                }
                timerTextView = timerBox
                cardLayout.addView(
                    timerBox,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        bottomMargin = dp(appCtx, 18)
                    }
                )

                // Secondary Explanation
                val descView = TextView(appCtx).apply {
                    text = "🔒 الوضع الصارم مفعل! لا يمكن استخدام هذا التطبيق (${blockedPackage}) حتى تنتهي جلسة التركيز الحالية."
                    setTextColor(Color.parseColor("#A0A0AB"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                }
                cardLayout.addView(
                    descView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(appCtx, 24)
                    }
                )

                // Primary Action Button: "العودة إلى Focus_zohayr"
                val returnButton = Button(appCtx).apply {
                    text = "العودة إلى Focus_zohayr"
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(appCtx, 12).toFloat()
                        setColor(Color.parseColor("#E05246"))
                    }
                    setPadding(dp(appCtx, 20), dp(appCtx, 12), dp(appCtx, 20), dp(appCtx, 12))
                    setOnClickListener {
                        dismissOverlayInternal()
                        launchFocusZohayrMainActivity(appCtx)
                    }
                }
                cardLayout.addView(
                    returnButton,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(appCtx, 50)
                    )
                )

                rootContainer.addView(
                    cardLayout,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                )

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                @Suppress("DEPRECATION")
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm.addView(rootContainer, params)
                overlayView = rootContainer
                mainHandler.removeCallbacks(tickRunnable)
                mainHandler.postDelayed(tickRunnable, 1000L)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissOverlay(context: Context? = null) {
        mainHandler.post {
            dismissOverlayInternal()
        }
    }

    private fun dismissOverlayInternal() {
        mainHandler.removeCallbacks(tickRunnable)
        val view = overlayView ?: return
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Exception) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        timerTextView = null
        taskTitleTextView = null
    }

    private fun launchFocusZohayrMainActivity(context: Context) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(FocusNotificationManager.EXTRA_OPEN_FULLSCREEN_TIMER, true)
            }
            context.startActivity(launchIntent)
        } catch (_: Exception) {
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
