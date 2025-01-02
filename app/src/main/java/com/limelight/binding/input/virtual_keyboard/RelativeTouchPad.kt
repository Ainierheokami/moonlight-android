package com.limelight.binding.input.virtual_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext

@SuppressLint("ViewConstructor")
class RelativeTouchPad(
    virtualKeyboard: VirtualKeyboard,
    context: Context,
    elementId: Int,
    layer: Int
): TouchPad(virtualKeyboard, context, elementId, layer) {

    private val touchContextMap = arrayOfNulls<TouchContext>(2)

    private fun getTouchContext(actionIndex: Int): TouchContext? {
        return if (actionIndex < touchContextMap.size) {
            touchContextMap[actionIndex]
        } else {
            null
        }
    }

    init {

        // Initialize touch contexts
        for (i in touchContextMap.indices) {
            touchContextMap[i] =  RelativeTouchContext(
                virtualKeyboard.nvConnection,
                i,
                1280,
                720,
                this,
                virtualKeyboard.gameContext.prefConfig
            )
        }

        addTouchpadListener(object: TouchpadListener {
            override fun onTouch(x: Float, y: Float, event: MotionEvent) {
                val actionIndex = event.actionIndex
                val eventX = event.getX(actionIndex).toInt()
                val eventY = event.getY(actionIndex).toInt()

                val touchContext = getTouchContext(actionIndex) ?: return

                Log.d("Touch", "pointerCount: ${event.pointerCount} index: $actionIndex")
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN-> {
                        for (aTouchContext in touchContextMap) {
                            aTouchContext?.setPointerCount(event.pointerCount)
                        }
                        touchContext.touchDownEvent(eventX, eventY, event.eventTime, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        touchContext.touchUpEvent(eventX, eventY, event.eventTime);
                        for (aTouchContext in touchContextMap) {
                            aTouchContext?.setPointerCount(event.pointerCount - 1)
                        }
                        if (actionIndex == 0 && event.pointerCount > 1 && !touchContext.isCancelled()) {
                            // The original secondary touch now becomes primary
                            touchContext.touchDownEvent(
                                (event.getX(1)).toInt(),
                                (event.getY(1)).toInt(),
                                event.eventTime, false
                            )
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // ACTION_MOVE is special because it always has actionIndex == 0
                        // We'll call the move handlers for all indexes manually

                        // First process the historical events
                        for (i in 0 until event.historySize) {
                            for (aTouchContextMap in touchContextMap) {
                                if (aTouchContextMap?.actionIndex!! < event.pointerCount) {
                                    aTouchContextMap.touchMoveEvent(
                                        (event.getHistoricalX(
                                            aTouchContextMap.actionIndex,
                                            i
                                        )).toInt(),
                                        (event.getHistoricalY(
                                            aTouchContextMap.actionIndex,
                                            i
                                        )).toInt(),
                                        event.getHistoricalEventTime(i)
                                    )
                                }
                            }
                        }

                        // Now process the current values
                        for (aTouchContextMap in touchContextMap) {
                            if (aTouchContextMap?.actionIndex!! < event.pointerCount) {
                                aTouchContextMap.touchMoveEvent(
                                    (event.getX(aTouchContextMap.actionIndex)).toInt(),
                                    (event.getY(aTouchContextMap.actionIndex)).toInt(),
                                    event.eventTime
                                )
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        for (aTouchContext in touchContextMap) {
                            aTouchContext?.cancelTouch()
                            aTouchContext?.setPointerCount(0)
                        }
                    }
                }

            }

        })
    }
}