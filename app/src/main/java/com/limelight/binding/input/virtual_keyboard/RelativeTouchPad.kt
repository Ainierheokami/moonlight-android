package com.limelight.binding.input.virtual_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.limelight.binding.input.touch.RelativeTouchContext

@SuppressLint("ViewConstructor")
class RelativeTouchPad(
    virtualKeyboard: VirtualKeyboard,
    context: Context,
    elementId: Int,
    layer: Int
): TouchPad(virtualKeyboard, context, elementId, layer) {

    val touchContext = RelativeTouchContext(
        virtualKeyboard.nvConnection,
        0,
        1280,
        720,
        this,
        virtualKeyboard.gameContext.prefConfig
    )

    init {
        addTouchpadListener(object: TouchpadListener {
            override fun onTouch(x: Float, y: Float, event: MotionEvent) {
                val index = event.actionIndex
                val eventX = event.getX(index).toInt()
                val eventY = event.getY(index).toInt()

                Log.d("Touch", "pointerCount: ${event.pointerCount} index: $index")
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN-> {
                        touchContext.setPointerCount(event.pointerCount)
                        touchContext.actionIndex = index
                        touchContext.touchDownEvent(eventX, eventY, event.eventTime, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        touchContext.touchUpEvent(eventX, eventY, event.eventTime)
                        touchContext.setPointerCount(event.pointerCount - 1)
                        touchContext.actionIndex = index
                        if (index == 0 && event.pointerCount > 1 && !touchContext.isCancelled){
                            touchContext.touchDownEvent(event.getX(1).toInt(), event.getY(1).toInt(),event.eventTime, true)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        touchContext.setPointerCount(event.pointerCount)
                        touchContext.actionIndex = index
                        for (i in 0 until event.historySize) {
                            if (touchContext.actionIndex < event.pointerCount){
                                touchContext.touchMoveEvent(
                                    event.getHistoricalX(touchContext.actionIndex, i).toInt(),
                                    event.getHistoricalY(touchContext.actionIndex, i).toInt(),
                                    event.getHistoricalEventTime(i)
                                )
                            }
                        }

                        if (touchContext.actionIndex < event.pointerCount){
                            touchContext.touchMoveEvent(
                                event.getX(touchContext.actionIndex).toInt(),
                                event.getY(touchContext.actionIndex).toInt(),
                                event.eventTime
                            )
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        touchContext.cancelTouch()
                        touchContext.setPointerCount(0)
                    }
                }

            }

        })
    }
}