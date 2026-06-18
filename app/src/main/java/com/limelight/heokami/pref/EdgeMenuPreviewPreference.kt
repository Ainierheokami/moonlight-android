package com.limelight.heokami.pref

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.preference.Preference
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.limelight.R
import com.limelight.preferences.PreferenceConfiguration

class EdgeMenuPreviewPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val hotZone = prefs.getInt(
            PreferenceConfiguration.EDGE_MENU_HOT_ZONE_PREF_STRING,
            PreferenceConfiguration.DEFAULT_EDGE_MENU_HOT_ZONE_DP
        ).coerceIn(1, 160)
        val swipeThreshold = prefs.getInt(
            PreferenceConfiguration.EDGE_MENU_SWIPE_THRESHOLD_PREF_STRING,
            PreferenceConfiguration.DEFAULT_EDGE_MENU_SWIPE_THRESHOLD_DP
        ).coerceIn(4, 240)

        val density = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(10))
        }
        layout.addView(TextView(context).apply {
            text = context.getString(R.string.edge_menu_preview_message, hotZone, swipeThreshold)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, dp(12))
        })
        layout.addView(PreviewView(context, hotZone, swipeThreshold, density), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(150)
        ))

        AlertDialog.Builder(context)
            .setTitle(R.string.title_edge_menu_preview)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private class PreviewView(
        context: Context,
        private val hotZoneDp: Int,
        private val swipeThresholdDp: Int,
        private val density: Float
    ) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60, 255, 255, 255)
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.argb(210, 255, 255, 255)
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(235, 108, 180, 255)
        }
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(210, 255, 255, 255)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val hotZone = dp(hotZoneDp).coerceAtMost((w / 3f).toInt()).toFloat()
            val arrowDistance = dp(swipeThresholdDp).coerceAtMost((w / 2.5f).toInt()).toFloat()
            val centerY = h / 2f

            canvas.drawColor(Color.rgb(22, 28, 36))
            canvas.drawRect(0f, 0f, hotZone, h, fillPaint)
            canvas.drawRect(0f, 0f, hotZone, h, strokePaint)
            canvas.drawRect(w - hotZone, 0f, w, h, fillPaint)
            canvas.drawRect(w - hotZone, 0f, w, h, strokePaint)
            canvas.drawRect(0f, dp(18).toFloat(), dp(3).toFloat(), h - dp(18).toFloat(), edgePaint)
            canvas.drawRect(w - dp(3).toFloat(), dp(18).toFloat(), w, h - dp(18).toFloat(), edgePaint)

            drawArrow(canvas, hotZone + dp(8), centerY, hotZone + arrowDistance, centerY)
            drawArrow(canvas, w - hotZone - dp(8), centerY, w - hotZone - arrowDistance, centerY)
        }

        private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
            canvas.drawLine(startX, startY, endX, endY, arrowPaint)
            val dir = if (endX > startX) 1f else -1f
            val head = dp(12).toFloat()
            canvas.drawLine(endX, endY, endX - dir * head, endY - head * 0.55f, arrowPaint)
            canvas.drawLine(endX, endY, endX - dir * head, endY + head * 0.55f, arrowPaint)
        }

        private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    }
}
