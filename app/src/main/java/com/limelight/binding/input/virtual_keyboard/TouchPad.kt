package com.limelight.binding.input.virtual_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import androidx.core.content.ContextCompat

@SuppressLint("ViewConstructor")
open class TouchPad(
    virtualKeyboard: VirtualKeyboard,
    context: Context,
    elementId: Int,
    layer: Int
) : VirtualKeyboardElement(virtualKeyboard, context, elementId, layer) {

    interface TouchpadListener {
        fun onTouch(x: Float, y: Float, event: MotionEvent)
    }

    private val listeners = ArrayList<TouchpadListener>()
    private val paint = Paint()
    private val rect = RectF()

    fun addTouchpadListener(listener: TouchpadListener) {
        listeners.add(listener)
    }

    private fun inRange(x: Float, y: Float) =
        (this.x < x && this.x + this.width > x) &&
                (this.y < y && this.y + this.height > y)

    override fun onElementDraw(canvas: Canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT)

        // 读取与 DigitalButton 一致的外观样式键
        var borderEnabled = true
        var borderWidthPx = getDefaultStrokeWidth()
        var borderColor = if (isPressed) pressedColor else defaultColor
        var textColor = Color.WHITE
        var textAlphaPct = 100
        var fillColor = if (isPressed) pressedColor else defaultColor
        var bgAlphaPct = 100
        var bgPressedAlphaPct = 100
        var overallEnabled = false
        var overallColorNormal = fillColor
        var overallColorPressed = fillColor
        var overallAlphaPct = 100

        try {
            buttonData?.let { data ->
                if (data.has("BORDER_ENABLED")) borderEnabled = data.getBoolean("BORDER_ENABLED")
                if (data.has("BORDER_WIDTH_PX")) borderWidthPx = data.getInt("BORDER_WIDTH_PX")
                if (data.has("BORDER_COLOR")) borderColor = data.getInt("BORDER_COLOR")
                if (data.has("BORDER_ALPHA")) {
                    val a = data.getInt("BORDER_ALPHA").coerceIn(0, 100)
                    val baseA = (borderColor ushr 24) and 0xFF
                    val composedA = (baseA * (a / 100f)).toInt()
                    borderColor = (composedA shl 24) or (borderColor and 0x00FFFFFF)
                }
                if (data.has("TEXT_COLOR")) textColor = data.getInt("TEXT_COLOR")
                if (data.has("TEXT_ALPHA")) textAlphaPct = data.getInt("TEXT_ALPHA").coerceIn(0, 100)
                if (data.has("BG_COLOR")) fillColor = data.getInt("BG_COLOR")
                if (data.has("BG_COLOR_PRESSED") && isPressed) fillColor = data.getInt("BG_COLOR_PRESSED")
                if (data.has("BG_ALPHA")) bgAlphaPct = data.getInt("BG_ALPHA").coerceIn(0, 100)
                if (data.has("BG_ALPHA_PRESSED") && isPressed) bgPressedAlphaPct = data.getInt("BG_ALPHA_PRESSED").coerceIn(0, 100)
                if (data.has("OVERALL_ENABLED")) overallEnabled = data.getBoolean("OVERALL_ENABLED")
                if (data.has("OVERALL_COLOR")) overallColorNormal = data.getInt("OVERALL_COLOR")
                if (data.has("OVERALL_COLOR_PRESSED")) overallColorPressed = data.getInt("OVERALL_COLOR_PRESSED")
                if (data.has("OVERALL_ALPHA")) overallAlphaPct = data.getInt("OVERALL_ALPHA").coerceIn(0, 100)
            }
        } catch (_: Exception) {}

        // 计算填充色合成透明度：先根据 BG_ALPHA，然后如启用 overall 再覆盖
        run {
            var a = if (isPressed) bgPressedAlphaPct else bgAlphaPct
            a = a.coerceIn(0, 100)
            val baseA = (fillColor ushr 24) and 0xFF
            val composedA = (baseA * (a / 100f)).toInt()
            fillColor = (composedA shl 24) or (fillColor and 0x00FFFFFF)
        }
        if (overallEnabled) {
            val ov = if (isPressed) overallColorPressed else overallColorNormal
            val baseA = (ov ushr 24) and 0xFF
            val composedA = (baseA * (overallAlphaPct / 100f)).toInt()
            fillColor = (composedA shl 24) or (ov and 0x00FFFFFF)
        }

        // 外框 rect（内缩边框宽度，以保证边框与填充不重叠）
        rect.set(
            borderWidthPx.toFloat(),
            borderWidthPx.toFloat(),
            width.toFloat() - borderWidthPx,
            height.toFloat() - borderWidthPx
        )

        val cornerRadius = radius

        // 背景填充
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // 轮廓描边
        if (borderEnabled && borderWidthPx > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidthPx.toFloat()
            paint.color = borderColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }

        if (icon != -1) {
            val drawable = ContextCompat.getDrawable(context, icon)!!
            drawable.setBounds(5, 5, width - 5, height - 5)
            drawable.draw(canvas)
        } else {
            // 文本颜色与透明度（自适应放大并居中绘制）
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = getDefaultStrokeWidth() / 2f
            val baseA = (textColor ushr 24) and 0xFF
            val composedA = (baseA * (textAlphaPct / 100f)).toInt()
            val composedTextColor = (composedA shl 24) or (textColor and 0x00FFFFFF)
            paint.color = composedTextColor
            paint.textAlign = Paint.Align.CENTER

            val textStr = text ?: ""
            if (textStr.isNotEmpty()) {
                // 以可用区域（rect）为参考，动态适配文本尺寸
                val targetW = rect.width() * 0.7f
                val targetH = rect.height() * 0.5f
                // 初始字号以较大的比例开始，再按宽/高约束收缩
                var textSize = Math.min(rect.width(), rect.height()) * 0.45f
                paint.textSize = textSize
                val measuredW = paint.measureText(textStr).coerceAtLeast(1f)
                val widthScale = targetW / measuredW
                textSize *= widthScale
                paint.textSize = textSize
                val fm = paint.fontMetrics
                val textHeight = (fm.bottom - fm.top).coerceAtLeast(1f)
                if (textHeight > targetH) {
                    val heightScale = targetH / textHeight
                    textSize *= heightScale
                    paint.textSize = textSize
                }
                val fm2 = paint.fontMetrics
                val baselineY = rect.centerY() - (fm2.ascent + fm2.descent) / 2f
                canvas.drawText(textStr, rect.centerX(), baselineY, paint)
            }
        }
    }

    @Override
    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        // get masked (not specific to a pointer) action
        val x = this.x + event.x
        val y = this.y + event.y
        Log.d("Touch", "index ${event.actionIndex} pointers ${event.pointerCount} x $x y $y")
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                setPressed(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 1) {
                    setPressed(false)
                }
            }
        }
        listeners.forEach { it.onTouch(x,y,event) }
        invalidate()
        return true
    }
}