package com.example.arintabletusage.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 어떤 앱이 현재 포그라운드에 있든 그 위에 그대로 겹쳐서 뜨는 경고 오버레이.
 *
 * Activity/Task를 전혀 만들지 않고 WindowManager에 View를 직접 얹는 방식(TYPE_APPLICATION_OVERLAY)이라
 * 최근 앱 목록이나 화면 전환 이력에 남지 않고, 다이얼로그 안에도 앱 이름/아이콘을 넣지 않으므로
 * "어떤 앱이 이 경고를 띄웠는지"가 겉으로 드러나지 않는다.
 */
object UsageWarningOverlay {

    private var currentView: View? = null

    /** 이미 떠 있으면 중복으로 띄우지 않는다. */
    fun show(context: Context, message: String) {
        if (!Settings.canDrawOverlays(context)) return
        if (currentView != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 32), dp(context, 28), dp(context, 32), dp(context, 24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(context, 16).toFloat()
            }
        }

        val messageView = TextView(context).apply {
            text = message
            textSize = 17f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        card.addView(messageView)

        val button = Button(context).apply {
            text = "확인"
            setOnClickListener { dismiss(context) }
        }
        card.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 20)
            }
        )

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#B3000000"))
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(root, params)
            currentView = root
        } catch (e: Exception) {
            // 표시 중 권한이 취소되는 등 addView가 실패할 수 있으므로 조용히 무시한다.
        }
    }

    private fun dismiss(context: Context) {
        val view = currentView ?: return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            // no-op
        }
        currentView = null
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
