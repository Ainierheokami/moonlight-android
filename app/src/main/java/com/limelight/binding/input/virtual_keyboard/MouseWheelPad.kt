package com.limelight.binding.input.virtual_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class MouseWheelPad(
    virtualKeyboard: VirtualKeyboard,
    context: Context,
    elementId: Int,
    layer: Int
) : TouchPad(virtualKeyboard, context, elementId, layer) {

    private var lastY = 0f

    init {
        text = context.getString(com.limelight.R.string.virtual_keyboard_mouse_wheel_label)
    }

    override fun onElementTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastY = event.y
                isPressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                if (dy != 0f) {
                    var scale = 5
                    try {
                        if (buttonData != null && buttonData.has("WHEEL_SENSITIVITY")) {
                            scale = buttonData.getInt("WHEEL_SENSITIVITY").coerceIn(1, 20)
                        }
                    } catch (_: Exception) {}

                    val amount = (-dy * scale).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    if (amount != 0) {
                        virtualKeyboard.nvConnection.sendMouseHighResScroll(amount.toShort())
                        lastY = event.y
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isPressed = false
                invalidate()
                return true
            }
        }
        return true
    }
}
