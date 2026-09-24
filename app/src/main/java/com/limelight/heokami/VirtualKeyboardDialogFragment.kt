package com.limelight.heokami

import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.util.DisplayMetrics
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.utils.OverlayContainer
import com.limelight.utils.OverlayManager

/**
 * Full-screen virtual keyboard backed by the Activity overlay hierarchy.
 *
 * <p>The historical implementation extended {@code DialogFragment} and created a separate
 * {@code Dialog} window. Keeping it in the same OverlayContainer as app dialogs and toasts means
 * diagnostics can remain visible above the keyboard and no window-level z-order race is involved.
 */
class VirtualKeyboardDialogFragment private constructor(
    private val game: Game
) : OverlayManager.LifecycleListener {

    private val activity: Game
        get() = game

    private lateinit var virtualKeyboard: VirtualKeyboard
    private val pressedButtons = mutableSetOf<Button>()
    private var scrollView: HorizontalScrollView? = null
    private var rootView: View? = null
    private var overlayHandle: OverlayContainer.DialogHandle? = null

    companion object {
        private var activeInstance: VirtualKeyboardDialogFragment? = null

        @JvmStatic
        fun show(game: Game): VirtualKeyboardDialogFragment {
            activeInstance?.dismiss()
            val controller = VirtualKeyboardDialogFragment(game)
            activeInstance = controller
            controller.showOverlay()
            return controller
        }
    }

    private fun showOverlay() {
        virtualKeyboard = game.getVirtualKeyboard()

        // 使用标准 108 键布局。
        val content = LayoutInflater.from(game).inflate(R.layout.virtual_keyboard_108, null, false)
        val root = if (content is HorizontalScrollView) {
            // 记录滚动容器，用于阻止按钮触摸时的横向拦截。
            scrollView = content

            // 包一层全屏容器，将键盘整体置底显示。
            FrameLayout(game).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
                addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ))
            }
        } else {
            content
        }
        rootView = root

        setupKeyboardButtons(root)

        // 调大容器高度并按区自适应行高，参考悬浮键盘的上下拉伸对齐。
        resizeAndAdaptFullScreenKeyboard(root)

        val overlayManager = OverlayManager.getInstance()
        overlayManager.initialize(game)
        overlayManager.addLifecycleListener(this)
        overlayHandle = overlayManager.showFullScreenDialog(
            game,
            root,
            true,
            false,
            ::dismiss
        )
        if (overlayHandle == null) {
            overlayManager.removeLifecycleListener(this)
            rootView = null
            activeInstance = null
        }
    }

    fun dismiss() {
        if (overlayHandle == null && rootView == null) {
            return
        }
        resetAllModifierKeys()
        overlayHandle?.remove()
        overlayHandle = null
        OverlayManager.getInstance().removeLifecycleListener(this)
        rootView = null
        scrollView = null
        pressedButtons.clear()
        if (activeInstance === this) {
            activeInstance = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity === game && overlayHandle?.isShowing == true) {
            OverlayManager.getInstance().getContainer(game)?.bringToFront()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === game) {
            resetAllModifierKeys()
            overlayHandle = null
            rootView = null
            scrollView = null
            pressedButtons.clear()
            OverlayManager.getInstance().removeLifecycleListener(this)
            if (activeInstance === this) {
                activeInstance = null
            }
        }
    }

    private fun resetAllModifierKeys() {
        if (!::virtualKeyboard.isInitialized) {
            return
        }
        try {
            val inputContext = virtualKeyboard.getKeyboardInputContext()
            Log.d("VirtualKeyboard", "Resetting modifier keys, current modifier: 0x${inputContext.modifier.toString(16).uppercase()}")

            // 释放所有修饰键。
            if (inputContext.modifier != 0.toByte()) {
                inputContext.modifier = 0.toByte()
                Log.d("VirtualKeyboard", "All modifier keys released")
            }
        } catch (e: Exception) {
            Log.e("VirtualKeyboard", "Error resetting modifier keys", e)
        }
    }

    private fun pressButton(button: Button, vkCode: Int) {
        try {
            Log.d("VirtualKeyboard", "Button pressed: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")

            // 记录按下的按钮。
            pressedButtons.add(button)

            val inputContext = virtualKeyboard.getKeyboardInputContext()
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())
            Log.d("VirtualKeyboard", "Modifier mask: 0x${modifierMask.toString(16).uppercase()}")

            if (modifierMask != 0.toByte()) {
                inputContext.modifier = (inputContext.modifier.toInt() or modifierMask.toInt()).toByte()
                Log.d("VirtualKeyboard", "Modifier key pressed: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                Log.d("VirtualKeyboard", "Sending regular key: ${button.text}")
            }
            button.setBackgroundResource(R.drawable.keyboard_key_pressed_bg)
            virtualKeyboard.sendDownKey(vkCode.toShort())
        } catch (e: Exception) {
            Log.e("VirtualKeyboard", "Error pressing button: ${button.text}", e)
        }
    }

    private fun releaseButton(button: Button, vkCode: Int) {
        try {
            Log.d("VirtualKeyboard", "Button released: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")

            val inputContext = virtualKeyboard.getKeyboardInputContext()
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())

            if (modifierMask != 0.toByte()) {
                inputContext.modifier = (inputContext.modifier.toInt() and modifierMask.toInt().inv()).toByte()
                Log.d("VirtualKeyboard", "Modifier key released: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                Log.d("VirtualKeyboard", "Releasing regular key: ${button.text}")
            }
            virtualKeyboard.sendUpKey(vkCode.toShort())
        } catch (e: Exception) {
            Log.e("VirtualKeyboard", "Error in releaseButton logic for ${button.text}", e)
        } finally {
            // 无论如何都恢复视觉状态并从集合中移除。
            button.setBackgroundResource(R.drawable.keyboard_key_bg)
            pressedButtons.remove(button)
        }
    }

    private fun setupKeyboardButtons(view: View) {
        val keyboardLayout = view.findViewById<LinearLayout>(R.id.keyboard_layout)
        setupButtonClickListeners(keyboardLayout)
    }

    private fun setupButtonClickListeners(layout: ViewGroup) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is Button) {
                setupButton(child)
            } else if (child is ViewGroup) {
                setupButtonClickListeners(child)
            }
        }
    }

    private fun setupButton(button: Button) {
        val vkCodeString = button.tag?.toString() ?: return

        try {
            val vkCode = vkCodeString.removePrefix("0x").toInt(16)

            if (vkCode == 0x00) {
                // Fn 键作为“锁定模式”开关。
                button.setOnClickListener {
                    val inputContext = virtualKeyboard.getKeyboardInputContext()
                    val wasLocked = (inputContext.key.toInt() and 0x8000) != 0
                    inputContext.key = if (wasLocked) {
                        (inputContext.key.toInt() and 0x7FFF).toShort()
                    } else {
                        (inputContext.key.toInt() or 0x8000).toShort()
                    }
                    button.isActivated = !wasLocked
                }
                return
            }

            // 使用触摸事件来处理按下和释放。
            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        scrollView?.requestDisallowInterceptTouchEvent(true)
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        val lockMode = (inputContext.key.toInt() and 0x8000) != 0
                        if (lockMode) {
                            if (pressedButtons.contains(button)) {
                                releaseButton(button, vkCode)
                                pressedButtons.remove(button)
                                button.isPressed = false
                            } else {
                                pressButton(button, vkCode)
                                button.isPressed = true
                            }
                        } else {
                            pressButton(button, vkCode)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        val lockMode = (inputContext.key.toInt() and 0x8000) != 0
                        if (!lockMode && pressedButtons.contains(button)) {
                            releaseButton(button, vkCode)
                        }
                        scrollView?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> false
                }
            }
        } catch (e: NumberFormatException) {
            Log.e("VirtualKeyboard", "Invalid vkCode format: $vkCodeString", e)
        } catch (e: Exception) {
            Log.e("VirtualKeyboard", "Error setting up button: ${button.text}", e)
        }
    }

    private fun resizeAndAdaptFullScreenKeyboard(root: View) {
        val scroll = root.findViewById<HorizontalScrollView>(R.id.keyboard_scroll) ?: return

        // 目标高度：屏幕高度的 75%。
        val dm = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(dm)
        val targetHeightPx = (dm.heightPixels * 0.75f).toInt()

        val topPad = dpToPx(8)
        val bottomPad = dpToPx(8)
        scroll.layoutParams = scroll.layoutParams.apply { height = targetHeightPx }
        scroll.setPadding(scroll.paddingLeft, topPad, scroll.paddingRight, bottomPad)

        val availableHeight = targetHeightPx - topPad - bottomPad
        val left = root.findViewById<LinearLayout>(R.id.left_block)
        val middle = root.findViewById<LinearLayout>(R.id.middle_block)
        val right = root.findViewById<LinearLayout>(R.id.right_block)

        adaptBlockRowHeights(left, availableHeight)
        adaptBlockRowHeights(middle, availableHeight)
        adaptBlockRowHeights(right, availableHeight)
    }

    private fun adaptBlockRowHeights(block: LinearLayout?, totalHeightPx: Int) {
        block ?: return
        val rowViews = (0 until block.childCount)
            .map { block.getChildAt(it) }
            .filterIsInstance<LinearLayout>()

        if (rowViews.isEmpty()) return

        val perRowMargins = rowViews.map { row ->
            var maxMargins = 0
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                val mlp = child.layoutParams as? ViewGroup.MarginLayoutParams
                if (mlp != null) {
                    val margin = mlp.topMargin + mlp.bottomMargin
                    if (margin > maxMargins) maxMargins = margin
                }
            }
            maxMargins
        }

        val totalMargins = perRowMargins.sum()
        val rowCount = rowViews.size
        val keyHeightPx = ((totalHeightPx - totalMargins) / rowCount).coerceAtLeast(dpToPx(28))

        rowViews.forEach { row ->
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                if (child is Button) {
                    val lp = child.layoutParams
                    lp.height = keyHeightPx
                    child.layoutParams = lp
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
