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

        paint.apply {
            textSize = getPercent(width.toFloat(), 25f)
            textAlign = Paint.Align.CENTER
            strokeWidth = getDefaultStrokeWidth().toFloat()
            color = if (isPressed) pressedColor else defaultColor
            style = Paint.Style.STROKE
        }

        rect.set(
            paint.strokeWidth,
            paint.strokeWidth,
            width.toFloat() - paint.strokeWidth,
            height.toFloat() - paint.strokeWidth
        )

        val cornerRadius = radius // round corner size
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        if (icon != -1) {
            val drawable = ContextCompat.getDrawable(context, icon)!! // Use ContextCompat
            drawable.setBounds(5, 5, width - 5, height - 5)
            drawable.draw(canvas)
        } else {
            paint.style = Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = getDefaultStrokeWidth() / 2f
            canvas.drawText(text, getPercent(width.toFloat(), 50f), getPercent(height.toFloat(), 63f), paint)
        }
    }

    @Override
    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        // get masked (not specific to a pointer) action
        val x = this.x + event.x
        val y = this.y + event.y
        Log.d("Touch", "index ${event.actionIndex} pointers ${event.pointerCount} x $x y $y")
        listeners.forEach { it.onTouch(x,y,event) }
        return true
    }
}