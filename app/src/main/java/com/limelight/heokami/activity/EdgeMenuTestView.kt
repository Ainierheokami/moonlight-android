package com.limelight.heokami.activity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.limelight.R
import com.limelight.heokami.EdgeSwipeDetector
import com.limelight.heokami.EdgeSwipeDetector.Miss
import com.limelight.heokami.EdgeSwipeDetector.MoveResult
import com.limelight.heokami.EdgeSwipeDetector.Side
import com.limelight.heokami.GameMenuGeometry

/** Draws the simulated stream, the edge zones, the live trail and the result log. */
internal class EdgeMenuTestView(
    context: Context,
    private val hotZoneDp: Int,
    private val thresholdDp: Int,
    private val gestureEnabledInStream: Boolean
) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val gridPaint = paint(Paint.Style.STROKE, Color.argb(24, 255, 255, 255), 1f)
    private val zoneFill = paint(Paint.Style.FILL, Color.argb(48, 68, 170, 255))
    private val zoneStroke = paint(Paint.Style.STROKE, Color.argb(190, 68, 170, 255), 1.5f)
    private val exclusionStroke = paint(Paint.Style.STROKE, Color.argb(150, 255, 170, 60), 1.5f).apply {
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    }
    private val trailPaint = paint(Paint.Style.STROKE, Color.rgb(102, 255, 204), 3f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val takeoverPaint = paint(Paint.Style.STROKE, Color.rgb(255, 214, 102), 3f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = paint(Paint.Style.FILL, Color.WHITE)
    private val guidePaint = paint(Paint.Style.STROKE, Color.argb(150, 108, 180, 255), 2f).apply {
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val panelFill = paint(Paint.Style.FILL, Color.argb(56, 80, 160, 255))
    private val panelStroke = paint(Paint.Style.STROKE, Color.argb(220, 108, 180, 255), 2f)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        textSize = dp(13f)
    }
    private val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textSize = dp(12f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(16f)
        isFakeBoldText = true
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 90)
        textSize = dp(12f)
    }

    private val trail = ArrayList<Pair<Float, Float>>()
    private var downX = -1f
    private var tracking = false
    private var consuming = false
    private var triggeredSide: Side? = null
    private var liveRemainingDp = -1f
    private var exclusionWidthPx = 0
    private val log = ArrayList<String>()
    private val trailPath = Path()
    private val leftZone = RectF()
    private val rightZone = RectF()
    private val menuRect = RectF()

    private fun paint(style: Paint.Style, argb: Int, strokeDp: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = style
            color = argb
            if (style == Paint.Style.STROKE) strokeWidth = dp(strokeDp)
        }

    /** -1 when the system gesture exclusion API is unavailable (before Android 10). */
    fun setExclusionWidthPx(px: Int) {
        exclusionWidthPx = px
        invalidate()
    }

    fun onGestureStart(x: Float, y: Float, candidate: Boolean) {
        trail.clear()
        trail.add(x to y)
        downX = x
        tracking = candidate
        consuming = false
        triggeredSide = null
        liveRemainingDp = -1f
        invalidate()
    }

    fun onGestureMove(x: Float, y: Float, detector: EdgeSwipeDetector, result: MoveResult) {
        if (trail.size < MAX_TRAIL_POINTS) trail.add(x to y)
        consuming = detector.isConsuming
        liveRemainingDp = if (result == MoveResult.NOT_TRACKING) -1f else detector.remainingPx() / density
        when (result) {
            MoveResult.TRIGGERED_LEFT -> triggeredSide = Side.LEFT
            MoveResult.TRIGGERED_RIGHT -> triggeredSide = Side.RIGHT
            else -> Unit
        }
        invalidate()
    }

    fun onGestureEnd(openedSide: Side?, miss: Miss, remainingDp: Float) {
        val entry = when {
            openedSide != null -> context.getString(
                if (openedSide == Side.LEFT) R.string.edge_test_result_open_left
                else R.string.edge_test_result_open_right)
            else -> when (miss) {
                Miss.NOT_IN_EDGE_ZONE -> context.getString(R.string.edge_test_miss_zone)
                Miss.WRONG_DIRECTION -> context.getString(R.string.edge_test_miss_direction)
                Miss.NOT_HORIZONTAL -> context.getString(R.string.edge_test_miss_vertical)
                Miss.TOO_SHORT -> context.getString(R.string.edge_test_miss_short, Math.ceil(remainingDp.toDouble()).toInt())
                Miss.MULTI_TOUCH -> context.getString(R.string.edge_test_miss_multitouch)
                Miss.NONE -> null
            }
        }
        if (entry != null) addLog(entry)
        tracking = false
        consuming = false
        triggeredSide = openedSide
        liveRemainingDp = -1f
        invalidate()
    }

    fun onSystemBack() {
        addLog(context.getString(R.string.edge_test_system_back))
        invalidate()
    }

    private fun addLog(entry: String) {
        log.add(0, entry)
        while (log.size > MAX_LOG) log.removeAt(log.size - 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.rgb(14, 20, 27))
        drawGrid(canvas, w, h)

        val hotZone = dp(hotZoneDp.toFloat()).coerceAtMost(w / 3f)
        val thresholdPx = dp(thresholdDp.toFloat())

        if (exclusionWidthPx > 0) {
            val ex = exclusionWidthPx.toFloat()
            canvas.drawRect(0f, 0f, ex, h, exclusionStroke)
            canvas.drawRect(w - ex, 0f, w, h, exclusionStroke)
        }
        leftZone.set(0f, 0f, hotZone, h)
        rightZone.set(w - hotZone, 0f, w, h)
        for (rect in arrayOf(leftZone, rightZone)) {
            canvas.drawRect(rect, zoneFill)
            canvas.drawRect(rect, zoneStroke)
        }

        // Before touching: arrows showing the swipe distance from each zone.
        if (!tracking && trail.isEmpty()) {
            drawArrow(canvas, hotZone + dp(8f), h / 2f, hotZone + thresholdPx, h / 2f)
            drawArrow(canvas, w - hotZone - dp(8f), h / 2f, w - hotZone - thresholdPx, h / 2f)
        }

        if (tracking && downX >= 0f) {
            val dir = if (downX <= w / 2f) 1f else -1f
            val x = downX + dir * thresholdPx
            canvas.drawLine(x, h * 0.2f, x, h * 0.8f, guidePaint)
        }

        drawTrail(canvas)

        triggeredSide?.let { drawMenuOutline(canvas, it, w, h) }

        drawHud(canvas, w, h)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val step = dp(48f)
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }
        val label = context.getString(R.string.edge_test_stream_label)
        canvas.drawText(label, (w - dimTextPaint.measureText(label)) / 2f, h / 2f - dp(40f), dimTextPaint)
    }

    private fun drawTrail(canvas: Canvas) {
        if (trail.size < 2) {
            trail.firstOrNull()?.let { canvas.drawCircle(it.first, it.second, dp(6f), pointPaint) }
            return
        }
        trailPath.rewind()
        trailPath.moveTo(trail[0].first, trail[0].second)
        for (i in 1 until trail.size) trailPath.lineTo(trail[i].first, trail[i].second)
        canvas.drawPath(trailPath, if (consuming || triggeredSide != null) takeoverPaint else trailPaint)
        val last = trail.last()
        canvas.drawCircle(last.first, last.second, dp(7f), pointPaint)
    }

    private fun drawMenuOutline(canvas: Canvas, side: Side, w: Float, h: Float) {
        val panelWidth = GameMenuGeometry.panelWidthPx(width, height, density).toFloat()
        val margin = dp(12f)
        val rect = menuRect
        if (side == Side.LEFT) {
            rect.set(margin, margin, margin + panelWidth, h - margin)
        } else {
            rect.set(w - margin - panelWidth, margin, w - margin, h - margin)
        }
        canvas.drawRoundRect(rect, dp(20f), dp(20f), panelFill)
        canvas.drawRoundRect(rect, dp(20f), dp(20f), panelStroke)
        val label = context.getString(R.string.edge_test_menu_here)
        canvas.drawText(label, rect.centerX() - textPaint.measureText(label) / 2f, rect.centerY(), textPaint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        var y = dp(36f)
        val x = dp(24f) + hotZoneDp.coerceAtLeast(0) * density
        canvas.drawText(context.getString(R.string.edge_test_title), x, y, titlePaint)
        y += dp(22f)
        canvas.drawText(context.getString(R.string.edge_test_params, hotZoneDp, thresholdDp), x, y, textPaint)
        y += dp(18f)
        val exclusion = if (exclusionWidthPx > 0) {
            context.getString(R.string.edge_test_exclusion_on, Math.round(exclusionWidthPx / density))
        } else {
            context.getString(R.string.edge_test_exclusion_off)
        }
        canvas.drawText(exclusion, x, y, dimTextPaint)
        if (!gestureEnabledInStream) {
            y += dp(18f)
            canvas.drawText(context.getString(R.string.edge_test_gesture_off), x, y, warnPaint)
        }

        y += dp(26f)
        val state = when {
            triggeredSide != null && consuming -> context.getString(R.string.edge_test_state_triggered)
            consuming -> context.getString(R.string.edge_test_state_consuming, Math.ceil(liveRemainingDp.toDouble()).toInt())
            tracking -> context.getString(R.string.edge_test_state_candidate)
            else -> context.getString(R.string.edge_test_state_idle)
        }
        canvas.drawText(state, x, y, titlePaint)

        y += dp(22f)
        for ((i, entry) in log.withIndex()) {
            textPaint.alpha = (255 - i * 55).coerceAtLeast(70)
            canvas.drawText(entry, x, y, textPaint)
            y += dp(18f)
        }
        textPaint.alpha = 235
    }

    private fun drawArrow(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float) {
        canvas.drawLine(startX, startY, endX, endY, guidePaint)
        val dir = if (endX > startX) 1f else -1f
        val head = dp(12f)
        canvas.drawLine(endX, endY, endX - dir * head, endY - head * 0.55f, guidePaint)
        canvas.drawLine(endX, endY, endX - dir * head, endY + head * 0.55f, guidePaint)
    }

    private companion object {
        const val MAX_TRAIL_POINTS = 256
        const val MAX_LOG = 5
    }
}
