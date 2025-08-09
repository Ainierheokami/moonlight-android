package com.limelight.heokami

import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Gravity
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
import com.limelight.nvstream.input.KeyboardPacket

class VirtualKeyboardDialogFragment : DialogFragment() {

    private lateinit var game: Game
    private lateinit var virtualKeyboard: VirtualKeyboard
    private val pressedButtons = mutableSetOf<Button>()
    private var scrollView: HorizontalScrollView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(activity, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 使用标准108键布局
        val content = inflater.inflate(R.layout.virtual_keyboard_108, container, false)

        return if (content is HorizontalScrollView) {
            // 记录滚动容器，用于阻止按钮触摸时的横向拦截
            scrollView = content

            // 包一层全屏容器，将键盘整体置底显示（禁用基线对齐）
            val root = FrameLayout(activity)
            root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.BOTTOM
            root.addView(content, lp)
            root
        } else {
            content
        }
    }

    override fun onStart() {
        super.onStart()
        // 对话框占满屏幕并贴底显示，避免状态栏预留空白
        dialog?.window?.let { win ->
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setGravity(Gravity.BOTTOM)
            // 让内容延伸到状态栏区域，但不隐藏导航栏，避免内容落在导航栏之下
            val decor = win.decorView
            decor.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        game = activity as Game
        virtualKeyboard = game.getVirtualKeyboard()
        
        setupKeyboardButtons(view)

        // 调大容器高度并按区自适应行高，参考悬浮键盘的上下拉伸对齐
        resizeAndAdaptFullScreenKeyboard(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 重置所有修饰键状态
        resetAllModifierKeys()
    }

    private fun resetAllModifierKeys() {
        try {
            val inputContext = virtualKeyboard.getKeyboardInputContext()
            Log.d("VirtualKeyboard", "Resetting modifier keys, current modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            
            // 释放所有修饰键
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

            // 记录按下的按钮
            pressedButtons.add(button)

            // 获取键盘输入上下文
            val inputContext = virtualKeyboard.getKeyboardInputContext()

            // 检查是否是修饰键
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())
            Log.d("VirtualKeyboard", "Modifier mask: 0x${modifierMask.toString(16).uppercase()}")

            if (modifierMask != 0.toByte()) {
                // 这是修饰键，按下时设置修饰符
                inputContext.modifier = (inputContext.modifier.toInt() or modifierMask.toInt()).toByte()
                Log.d("VirtualKeyboard", "Modifier key pressed: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                // 这是普通键
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

            // 获取键盘输入上下文
            val inputContext = virtualKeyboard.getKeyboardInputContext()

            // 检查是否是修饰键
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())

            if (modifierMask != 0.toByte()) {
                // 这是修饰键，释放时清除修饰符
                inputContext.modifier = (inputContext.modifier.toInt() and modifierMask.toInt().inv()).toByte()
                Log.d("VirtualKeyboard", "Modifier key released: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                // 这是普通键
                Log.d("VirtualKeyboard", "Releasing regular key: ${button.text}")
            }
            virtualKeyboard.sendUpKey(vkCode.toShort())
        } catch (e: Exception) {
            Log.e("VirtualKeyboard", "Error in releaseButton logic for ${button.text}", e)
        } finally {
            // 无论如何都恢复视觉状态并从集合中移除
            button.setBackgroundResource(R.drawable.keyboard_key_bg)
            pressedButtons.remove(button)
        }
    }

    private fun setupKeyboardButtons(view: View) {
        // 获取键盘布局
        val keyboardLayout = view.findViewById<LinearLayout>(R.id.keyboard_layout)
        
        // 为所有按钮设置点击事件
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
            // 解析十六进制字符串为整数
            val vkCode = vkCodeString.removePrefix("0x").toInt(16)
            
            if (vkCode == 0x00) {
                // 将 Fn 键作为“锁定模式”开关：点击切换锁定开关状态
                button.setOnClickListener {
                    val inputContext = virtualKeyboard.getKeyboardInputContext()
                    val wasLocked = (inputContext.key.toInt() and 0x8000) != 0
                    // 复用最高位 0x8000 作为“锁定模式标志”；对 Short 先转 Int 做位运算，再转回 Short
                    inputContext.key = if (wasLocked) {
                        (inputContext.key.toInt() and 0x7FFF).toShort()
                    } else {
                        (inputContext.key.toInt() or 0x8000).toShort()
                    }
                    // 视觉反馈
                    button.isActivated = !wasLocked
                }
                return
            }

            // 使用触摸事件来处理按下和释放
            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时禁止父级 HorizontalScrollView 拦截，避免误触左右滑动
                        scrollView?.requestDisallowInterceptTouchEvent(true)
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        val lockMode = (inputContext.key.toInt() and 0x8000) != 0
                        if (lockMode) {
                            // 锁定模式：切换该键按下/松开状态
                            if (pressedButtons.contains(button)) {
                                // 当前认为是按下状态 -> 发送释放并移除
                                releaseButton(button, vkCode)
                                pressedButtons.remove(button)
                                button.isPressed = false
                            } else {
                                // 当前未按下 -> 发送按下并标记
                                pressButton(button, vkCode)
                                button.isPressed = true
                            }
                        } else {
                            // 普通模式
                            pressButton(button, vkCode)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        val lockMode = (inputContext.key.toInt() and 0x8000) != 0
                        if (!lockMode) {
                            // 普通模式下才释放
                            if (pressedButtons.contains(button)) {
                                releaseButton(button, vkCode)
                            }
                        }
                        // 释放时允许父级滚动容器恢复拦截
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

        // 目标高度：屏幕高度的 75%（更大）
        val dm = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(dm)
        val targetHeightPx = (dm.heightPixels * 0.75f).toInt()

        // 设置滚动容器的高度，并留出上下滑动余量
        val topPad = dpToPx(8)
        val bottomPad = dpToPx(8)
        scroll.layoutParams = scroll.layoutParams.apply { height = targetHeightPx }
        scroll.setPadding(scroll.paddingLeft, topPad, scroll.paddingRight, bottomPad)

        val availableHeight = targetHeightPx - topPad - bottomPad

        // 三大区域：按照可用高度进行行高自适应，避免被裁断
        val left = root.findViewById<LinearLayout>(R.id.left_block)
        val middle = root.findViewById<LinearLayout>(R.id.middle_block)
        val right = root.findViewById<LinearLayout>(R.id.right_block)

        adaptBlockRowHeights(left, availableHeight)
        adaptBlockRowHeights(middle, availableHeight)
        adaptBlockRowHeights(right, availableHeight)
    }

    private fun adaptBlockRowHeights(block: LinearLayout?, totalHeightPx: Int) {
        block ?: return
        // 统计该区的行（直接子 LinearLayout 视为一行）
        val rowViews = (0 until block.childCount)
            .map { block.getChildAt(it) }
            .filterIsInstance<LinearLayout>()

        if (rowViews.isEmpty()) return

        // 预估每行的垂直边距（取该行内子 View 最大的上下 margin 之和）
        val perRowMargins = rowViews.map { row ->
            var maxMargins = 0
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                val mlp = child.layoutParams as? ViewGroup.MarginLayoutParams
                if (mlp != null) {
                    val m = mlp.topMargin + mlp.bottomMargin
                    if (m > maxMargins) maxMargins = m
                }
            }
            maxMargins
        }

        val totalMargins = perRowMargins.sum()
        val rowCount = rowViews.size
        val keyHeightPx = ((totalHeightPx - totalMargins) / rowCount).coerceAtLeast(dpToPx(28))

        rowViews.forEachIndexed { index, row ->
            // 设置行内所有 Button 的高度为统一值，实现上下拉伸对齐
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

    companion object {
        fun newInstance(): VirtualKeyboardDialogFragment {
            return VirtualKeyboardDialogFragment()
        }
    }
} 