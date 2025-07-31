package com.limelight.heokami

import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.content.Context
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.heokami.VirtualKeyboardVkCode
import com.limelight.nvstream.input.KeyboardPacket

/**
 * 悬浮虚拟键盘Fragment
 * 提供可移动的悬浮键盘界面，包含功能条和优化的键盘布局
 */
class FloatingVirtualKeyboardFragment : DialogFragment() {

    private lateinit var game: Game
    private lateinit var virtualKeyboard: VirtualKeyboard
    private val pressedButtons = mutableSetOf<Button>()
    
    // 键盘状态
    private var isNumericMode = false
    private var isFunctionMode = false
    
    // 移动相关变量
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(activity, R.style.FloatingDialog).apply {
            // 设置对话框为悬浮模式，支持全屏移动
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )
            // 添加全屏移动标志，允许移动到状态栏区域
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            
            // 设置初始位置居中
            window?.attributes?.apply {
                gravity = android.view.Gravity.CENTER
                x = 0
                y = 0
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.floating_virtual_keyboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        game = activity as Game
        virtualKeyboard = game.getVirtualKeyboard()
        
        setupFunctionBar(view)
        setupKeyboardButtons(view)
        setupDragListener(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 重置所有修饰键状态
        resetAllModifierKeys()
    }

    /**
     * 设置功能条按钮
     */
    private fun setupFunctionBar(view: View) {
        // 移动键盘按钮
        view.findViewById<ImageButton>(R.id.btn_move_keyboard).setOnClickListener {
            isDragging = !isDragging
            updateMoveButtonState(view)
            Log.d("FloatingKeyboard", "Drag mode ${if (isDragging) "enabled" else "disabled"}")
        }

        // 数字键盘切换按钮
        view.findViewById<ImageButton>(R.id.btn_toggle_numeric).setOnClickListener {
            isNumericMode = !isNumericMode
            updateKeyboardLayout(view)
            updateNumericButtonState(view)
        }

        // 功能键切换按钮
        view.findViewById<ImageButton>(R.id.btn_toggle_function).setOnClickListener {
            isFunctionMode = !isFunctionMode
            updateKeyboardLayout(view)
            updateFunctionButtonState(view)
        }

        // 设置按钮
        view.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            showKeyboardSettings()
        }

        // 关闭按钮
        view.findViewById<ImageButton>(R.id.btn_close_keyboard).setOnClickListener {
            dismiss()
        }
    }

    /**
     * 设置键盘按钮
     */
    private fun setupKeyboardButtons(view: View) {
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboard_container)
        
        // 为所有按钮设置点击事件
        for (i in 0 until keyboardContainer.childCount) {
            val row = keyboardContainer.getChildAt(i) as? LinearLayout
            row?.let { setupRowButtons(it) }
        }
    }

    /**
     * 设置行内按钮
     */
    private fun setupRowButtons(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val button = row.getChildAt(i) as? Button
            button?.let { setupButton(it) }
        }
    }

    /**
     * 设置单个按钮
     */
    private fun setupButton(button: Button) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleKeyPress(button, true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleKeyPress(button, false)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 处理按键事件
     */
    private fun handleKeyPress(button: Button, isPressed: Boolean) {
        val vkCodeString = button.tag?.toString() ?: return
        val vkCode = try {
            vkCodeString.removePrefix("0x").toInt(16)
        } catch (e: NumberFormatException) {
            Log.e("FloatingKeyboard", "Invalid VK code format: $vkCodeString", e)
            return
        }
        
        if (isPressed) {
            pressedButtons.add(button)
            button.alpha = 0.7f
            pressButton(button, vkCode)
        } else {
            pressedButtons.remove(button)
            button.alpha = 0.95f
            releaseButton(button, vkCode)
        }
    }

    /**
     * 按下按钮
     */
    private fun pressButton(button: Button, vkCode: Int) {
        try {
            Log.d("FloatingKeyboard", "Button pressed: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")

            // 获取键盘输入上下文
            val inputContext = virtualKeyboard.getKeyboardInputContext()

            // 检查是否是修饰键
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())
            Log.d("FloatingKeyboard", "Modifier mask: 0x${modifierMask.toString(16).uppercase()}")

            if (modifierMask != 0.toByte()) {
                // 这是修饰键，按下时设置修饰符
                inputContext.modifier = (inputContext.modifier.toInt() or modifierMask.toInt()).toByte()
                Log.d("FloatingKeyboard", "Modifier key pressed: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                // 这是普通键
                Log.d("FloatingKeyboard", "Sending regular key: ${button.text}")
            }
            virtualKeyboard.sendDownKey(vkCode.toShort())
        } catch (e: Exception) {
            Log.e("FloatingKeyboard", "Error pressing button: ${button.text}", e)
        }
    }

    /**
     * 释放按钮
     */
    private fun releaseButton(button: Button, vkCode: Int) {
        try {
            Log.d("FloatingKeyboard", "Button released: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")

            // 获取键盘输入上下文
            val inputContext = virtualKeyboard.getKeyboardInputContext()

            // 检查是否是修饰键
            val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())

            if (modifierMask != 0.toByte()) {
                // 这是修饰键，释放时清除修饰符
                inputContext.modifier = (inputContext.modifier.toInt() and modifierMask.toInt().inv()).toByte()
                Log.d("FloatingKeyboard", "Modifier key released: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            } else {
                // 这是普通键
                Log.d("FloatingKeyboard", "Releasing regular key: ${button.text}")
            }
            virtualKeyboard.sendUpKey(vkCode.toShort())
        } catch (e: Exception) {
            Log.e("FloatingKeyboard", "Error in releaseButton logic for ${button.text}", e)
        }
    }

    /**
     * 重置所有修饰键状态
     */
    private fun resetAllModifierKeys() {
        try {
            val inputContext = virtualKeyboard.getKeyboardInputContext()
            Log.d("FloatingKeyboard", "Resetting modifier keys, current modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
            
            // 释放所有修饰键
            if (inputContext.modifier != 0.toByte()) {
                inputContext.modifier = 0.toByte()
                Log.d("FloatingKeyboard", "All modifier keys released")
            }
            
            // 重置按钮视觉状态
            pressedButtons.forEach { button ->
                button.alpha = 0.95f
            }
            pressedButtons.clear()
        } catch (e: Exception) {
            Log.e("FloatingKeyboard", "Error resetting modifier keys", e)
        }
    }

    /**
     * 设置拖拽监听器
     */
    private fun setupDragListener(view: View) {
        val functionBar = view.findViewById<LinearLayout>(R.id.function_bar)
        
        functionBar.setOnTouchListener { _, event ->
            if (!isDragging) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录触摸开始位置
                    lastX = event.rawX
                    lastY = event.rawY
                    Log.d("FloatingKeyboard", "Drag started at: rawX=$lastX, rawY=$lastY")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离
                    val deltaX = event.rawX - lastX
                    val deltaY = event.rawY - lastY
                    
                    // 获取当前窗口属性
                    val window = dialog.window
                    val attributes = window?.attributes
                    
                    attributes?.let {
                        // 更新坐标
                        val newX = it.x + deltaX.toInt()
                        val newY = it.y + deltaY.toInt()
                        
                        // 应用新位置
                        it.x = newX
                        it.y = newY
                        window.attributes = it
                        
                        Log.d("FloatingKeyboard", "Moving keyboard: deltaX=$deltaX, deltaY=$deltaY, newX=$newX, newY=$newY")
                    }
                    
                    // 更新上次位置
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 拖拽结束，记录最终位置
                    Log.d("FloatingKeyboard", "Drag ended at: rawX=${event.rawX}, rawY=${event.rawY}")
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 更新移动按钮状态
     */
    private fun updateMoveButtonState(view: View) {
        val moveButton = view.findViewById<ImageButton>(R.id.btn_move_keyboard)
        moveButton.alpha = if (isDragging) 0.7f else 1.0f
    }

    /**
     * 更新数字键盘按钮状态
     */
    private fun updateNumericButtonState(view: View) {
        val numericButton = view.findViewById<ImageButton>(R.id.btn_toggle_numeric)
        numericButton.alpha = if (isNumericMode) 0.7f else 1.0f
    }

    /**
     * 更新功能键按钮状态
     */
    private fun updateFunctionButtonState(view: View) {
        val functionButton = view.findViewById<ImageButton>(R.id.btn_toggle_function)
        functionButton.alpha = if (isFunctionMode) 0.7f else 1.0f
    }

    /**
     * 更新键盘布局
     */
    private fun updateKeyboardLayout(view: View) {
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboard_container)
        
        if (isNumericMode) {
            // 切换到数字键盘模式
            switchToNumericKeyboard(view)
        } else if (isFunctionMode) {
            // 切换到功能键模式
            switchToFunctionKeyboard(view)
        } else {
            // 切换到标准键盘模式
            switchToStandardKeyboard(view)
        }
    }

    /**
     * 切换到数字键盘
     */
    private fun switchToNumericKeyboard(view: View) {
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboard_container)
        keyboardContainer.removeAllViews()
        
                 // 添加数字键盘布局
         val numericLayout = LayoutInflater.from(activity).inflate(R.layout.numeric_keyboard, keyboardContainer, false)
        keyboardContainer.addView(numericLayout)
        
        // 设置数字键盘按钮事件
        setupNumericKeyboardButtons(numericLayout)
    }

    /**
     * 切换到功能键键盘
     */
    private fun switchToFunctionKeyboard(view: View) {
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboard_container)
        keyboardContainer.removeAllViews()
        
        // 创建功能键布局
        val functionLayout = createFunctionKeyboardLayout()
        keyboardContainer.addView(functionLayout)
        
        // 设置功能键按钮事件
        setupFunctionKeyboardButtons(functionLayout)
    }

    /**
     * 切换到标准键盘
     */
    private fun switchToStandardKeyboard(view: View) {
        val keyboardContainer = view.findViewById<LinearLayout>(R.id.keyboard_container)
        keyboardContainer.removeAllViews()
        
                 // 重新加载标准键盘布局
         val standardLayout = LayoutInflater.from(activity).inflate(R.layout.floating_virtual_keyboard, null)
        val standardKeyboardContainer = standardLayout.findViewById<LinearLayout>(R.id.keyboard_container)
        
        // 复制标准键盘内容
        while (standardKeyboardContainer.childCount > 0) {
            val child = standardKeyboardContainer.getChildAt(0)
            standardKeyboardContainer.removeViewAt(0)
            keyboardContainer.addView(child)
        }
        
        // 设置标准键盘按钮事件
        setupKeyboardButtons(view)
    }

    /**
     * 创建功能键键盘布局
     */
         private fun createFunctionKeyboardLayout(): LinearLayout {
         val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

                 // F1-F12行
         val fRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

                 for (i in 1..12) {
             val button = Button(activity).apply {
                text = "F$i"
                tag = (0x6F + i).toString(16) // F1 = 0x70, F2 = 0x71, etc.
                setTextSize(10f)
                setBackgroundResource(R.drawable.floating_key_button_bg)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(1, 1, 1, 1)
                }
            }
            fRow.addView(button)
        }
        layout.addView(fRow)

                 // 其他功能键行
         val otherRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val functionKeys = listOf(
            "ESC" to 0x1B,
            "Tab" to 0x09,
            "Caps" to 0x14,
            "Shift" to 0xA0,
            "Ctrl" to 0xA2,
            "Alt" to 0xA4,
            "Win" to 0x5B,
            "Menu" to 0x5D,
            "Ins" to 0x2D,
            "Del" to 0x2E
        )

                 functionKeys.forEach { (text, code) ->
             val button = Button(activity).apply {
                this.text = text
                tag = code.toString(16)
                setTextSize(10f)
                setBackgroundResource(R.drawable.floating_key_button_bg)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(1, 1, 1, 1)
                }
            }
            otherRow.addView(button)
        }
        layout.addView(otherRow)

        return layout
    }

    /**
     * 设置数字键盘按钮事件
     */
    private fun setupNumericKeyboardButtons(layout: View) {
        val container = layout as? LinearLayout ?: return
        
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout
            row?.let { setupRowButtons(it) }
        }
    }

    /**
     * 设置功能键键盘按钮事件
     */
    private fun setupFunctionKeyboardButtons(layout: LinearLayout) {
        for (i in 0 until layout.childCount) {
            val row = layout.getChildAt(i) as? LinearLayout
            row?.let { setupRowButtons(it) }
        }
    }

    /**
     * 显示键盘设置
     */
    private fun showKeyboardSettings() {
        val keyboardMenu = VirtualKeyboardMenu(game, virtualKeyboard)
        keyboardMenu.setGameView(game)
        keyboardMenu.showMenu()
    }

    companion object {
        /**
         * 显示悬浮键盘
         */
        fun show(game: Game) {
            val fragment = FloatingVirtualKeyboardFragment()
            fragment.show(game.getFragmentManager(), "floating_keyboard")
        }
    }
} 