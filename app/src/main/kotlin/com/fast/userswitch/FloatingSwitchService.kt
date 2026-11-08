package com.fast.userswitch

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
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

    private var switchStep = 0
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
                setColor(Color.parseColor("#66000000"))
                setStroke(3, Color.parseColor("#CCFFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(100, 100)
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
                    triggerSafeUserSwitch()
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

        @Suppress("DEPRECATION")
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
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

    private fun triggerSafeUserSwitch() {
        // 1. Ovozni darhol o'chirish
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (_: Exception) {}

        // 2. Agar MIUI Second Space (Ikkinchi makon) bo'lsa, to'g'ridan-to'g'ri o'tkazishga urinish
        try {
            val secondSpaceIntent = Intent().apply {
                component = ComponentName("com.miui.securitycore", "com.miui.securityspace.ui.activity.SwitchSpaceActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(secondSpaceIntent)
            return
        } catch (_: Exception) {}

        // 3. Standart Android Multi-User: Bosh ekranga chiqmasdan, Quick Settings pardasini tushiramiz
        switchStep = 1
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

        handler.postDelayed({ switchStep = 0 }, 3500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (switchStep == 0) return
        val pkg = event?.packageName?.toString() ?: ""

        // MUHIM: ChatGPT yoki boshqa ilovalarni bosib yubormaslik uchun FAQAT Tizim oynalaridagina ishlaydi!
        if (pkg != "com.android.systemui" && pkg != "com.android.settings" && pkg != "com.miui.securitycore") {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        // 1-qadam: Quick Settings pardasidan User (Foydalanuvchi) tugmasini bosish
        if (switchStep == 1) {
            val userTileIds = listOf(
                "com.android.systemui:id/multi_user_switch",
                "com.android.systemui:id/multi_user_avatar",
                "com.android.systemui:id/user_switcher_item"
            )
            for (id in userTileIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty() && clickNodeOrParent(nodes[0])) {
                    switchStep = 2
                    return
                }
            }
        }

        // 2-qadam: Chiqqan oynadan Admin / Ega profilini tanlash
        val adminKeywords = listOf("Admin", "Owner", "Ega", "Asosiy", "Владелец", "Администратор")
        for (keyword in adminKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (clickNodeOrParent(node)) {
                        switchStep = 3
                        return
                    }
                }
            }
        }

        // 3-qadam: Tasdiqlash ("OK" / "Almashtirish") tugmasini bosish
        val confirmBtn = rootNode.findAccessibilityNodeInfosByViewId("android:id/button1")
        if (!confirmBtn.isNullOrEmpty()) {
            confirmBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            switchStep = 0
            return
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
