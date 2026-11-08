package com.fast.userswitch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingSwitchService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootContainer: LinearLayout
    private lateinit var dotView: View
    private lateinit var confirmPanel: LinearLayout
    private lateinit var params: WindowManager.LayoutParams

    private var isSwitchingUser = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        initOverlay()
    }

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        dotView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#55000000"))
                setStroke(3, Color.parseColor("#99FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(95, 95)
        }

        confirmPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                cornerRadius = 50f
                setColor(Color.parseColor("#EE121212"))
                setStroke(2, Color.parseColor("#FF5252"))
            }

            val questionText = TextView(context).apply {
                text = "Amalni bajarasizmi?  "
                setTextColor(Color.WHITE)
                textSize = 13f
            }

            val yesBtn = TextView(context).apply {
                text = " HA "
                setTextColor(Color.parseColor("#00E676"))
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                setPadding(20, 8, 20, 8)
                setOnClickListener {
                    collapsePanel()
                    startRootlessUserSwitch()
                }
            }

            val noBtn = TextView(context).apply {
                text = " YO'Q "
                setTextColor(Color.parseColor("#FF5252"))
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                setPadding(20, 8, 10, 8)
                setOnClickListener {
                    collapsePanel()
                }
            }

            addView(questionText)
            addView(yesBtn)
            addView(noBtn)
        }

        rootContainer.addView(dotView)
        rootContainer.addView(confirmPanel)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        setupTouchLogic()
        windowManager.addView(rootContainer, params)
    }

    private fun setupTouchLogic() {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        dotView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(rootContainer, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchX) < 15 && abs(event.rawY - touchY) < 15) {
                        expandPanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun expandPanel() {
        dotView.visibility = View.GONE
        confirmPanel.visibility = View.VISIBLE
        windowManager.updateViewLayout(rootContainer, params)
    }

    private fun collapsePanel() {
        confirmPanel.visibility = View.GONE
        dotView.visibility = View.VISIBLE
        windowManager.updateViewLayout(rootContainer, params)
    }

    private fun startRootlessUserSwitch() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (_: Exception) {}

        performGlobalAction(GLOBAL_ACTION_HOME)

        isSwitchingUser = true
        try {
            val intent = Intent("android.settings.USER_SETTINGS").apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            isSwitchingUser = false
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        handler.postDelayed({ isSwitchingUser = false }, 3000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isSwitchingUser) return
        val rootNode = rootInActiveWindow ?: return

        val confirmButton = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!confirmButton.isNullOrEmpty()) {
            confirmButton[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            isSwitchingUser = false
            return
        }

        val switchKeywords = listOf("OK", "Switch", "Almashtirish", "O'tish", "Ha", "Переключить", "Да")
        for (word in switchKeywords) {
            val btnNodes = rootNode.findAccessibilityNodeInfosByText(word)
            if (!btnNodes.isNullOrEmpty()) {
                for (btn in btnNodes) {
                    if (btn.isClickable) {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        isSwitchingUser = false
                        return
                    }
                }
            }
        }

        val adminKeywords = listOf("Admin", "Owner", "Ega", "Asosiy", "Владелец", "Администратор", "Mustafo")
        for (keyword in adminKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (clickNodeOrParent(node)) {
                        return
                    }
                }
            }
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            current = current.parent
        }
        return false
    }

    override fun onInterrupt() {}
}