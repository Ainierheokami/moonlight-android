package com.limelight.heokami

import android.app.Dialog
import android.app.DialogFragment
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences
    
    // 键盘状态
    private var isNumericMode = false
    private var isFunctionMode = false
    private var currentOpacity = 0.95f
    
    // 移动相关变量
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f
    
    companion object {
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private const val KEY_NUMERIC_MODE = "numeric_mode"
        private const val KEY_FUNCTION_MODE = "function_mode"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_DRAGGING_MODE = "dragging_mode"
        
        @JvmStatic
        fun show(game: Game) {
            Log.d("FloatingKeyboard", "show() method called")
            try {
                val fragment = FloatingVirtualKeyboardFragment()
                fragment.show(game.fragmentManager, "floating_keyboard")
                Log.d("FloatingKeyboard", "Fragment shown successfully")
            } catch (e: Exception) {
                Log.e("FloatingKeyboard", "Error showing fragment", e)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d("FloatingKeyboard", "onCreateDialog() called")
        
        // 初始化SharedPreferences
        prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d("FloatingKeyboard", "SharedPreferences initialized")
        
        // 加载保存的状态
        loadSavedStates()
        
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
            
            // 恢复保存的位置，如果没有保存则使用默认居中位置
            window?.attributes?.apply {
                val savedX = prefs.getInt(KEY_POSITION_X, 0)
                val savedY = prefs.getInt(KEY_POSITION_Y, 0)
                
                if (savedX == 0 && savedY == 0) {
                    // 首次使用，居中显示
                    gravity = android.view.Gravity.CENTER
                    x = 0
                    y = 0
                } else {
                    // 恢复上次位置
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    x = savedX
                    y = savedY
                }
                
                Log.d("FloatingKeyboard", "Restored position: x=$x, y=$y")
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
        
        Log.d("FloatingKeyboard", "onViewCreated() called")
        
        game = activity as Game
        virtualKeyboard = game.getVirtualKeyboard()
        
        // 应用保存的透明度
        Log.d("FloatingKeyboard", "Applying initial opacity: $currentOpacity")
        applyOpacity(view)
        
        setupFunctionBar(view)
        setupKeyboardButtons(view)
        setupDragListener(view)
        
        // 恢复键盘布局状态
        Log.d("FloatingKeyboard", "Restoring layout state: numeric=$isNumericMode, function=$isFunctionMode")
        updateKeyboardLayout(view)
        
        // 更新按钮状态以反映当前模式
        updateNumericButtonState(view)
        updateFunctionButtonState(view)
        updateMoveButtonState(view) // 恢复拖拽按钮状态
        
        Log.d("FloatingKeyboard", "onViewCreated() completed")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 保存当前状态
        saveCurrentStates()
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
            // 立即保存拖拽状态
            prefs.edit().putBoolean(KEY_DRAGGING_MODE, isDragging).apply()
            Log.d("FloatingKeyboard", "Drag mode ${if (isDragging) "enabled" else "disabled"}, saved to prefs")
        }

        // 数字键盘切换按钮
        view.findViewById<ImageButton>(R.id.btn_toggle_numeric).setOnClickListener {
            isNumericMode = !isNumericMode
            isFunctionMode = false // 切换到数字模式时关闭功能模式
            updateKeyboardLayout(view)
            updateNumericButtonState(view)
            updateFunctionButtonState(view)
            // 立即保存状态
            saveKeyboardModeStates()
            Log.d("FloatingKeyboard", "Numeric mode: $isNumericMode, saved to prefs")
        }

        // 功能键切换按钮
        view.findViewById<ImageButton>(R.id.btn_toggle_function).setOnClickListener {
            isFunctionMode = !isFunctionMode
            isNumericMode = false // 切换到功能模式时关闭数字模式
            updateKeyboardLayout(view)
            updateNumericButtonState(view)
            updateFunctionButtonState(view)
            // 立即保存状态
            saveKeyboardModeStates()
            Log.d("FloatingKeyboard", "Function mode: $isFunctionMode, saved to prefs")
        }

        // 透明度调整按钮
        view.findViewById<ImageButton>(R.id.btn_opacity)?.setOnClickListener {
            Log.d("FloatingKeyboard", "Opacity button clicked")
            adjustOpacity(view)
        } ?: Log.e("FloatingKeyboard", "btn_opacity not found in layout")

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
            // 手动设置按下状态，让背景选择器的 state_pressed 生效
            button.isPressed = true
            pressButton(button, vkCode)
        } else {
            pressedButtons.remove(button)
            // 手动恢复正常状态
            button.isPressed = false
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
            
            // 重置所有按钮的按下状态
            pressedButtons.forEach { button ->
                button.isPressed = false
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
                    // 拖拽结束，记录最终位置并保存
                    Log.d("FloatingKeyboard", "Drag ended at: rawX=${event.rawX}, rawY=${event.rawY}")
                    saveCurrentPosition()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 更新移动按钮状态 - 使用背景选择器的激活状态代替alpha修改
     */
    private fun updateMoveButtonState(view: View) {
        val moveButton = view.findViewById<ImageButton>(R.id.btn_move_keyboard)
        moveButton.isActivated = isDragging
    }

    /**
     * 更新数字键盘按钮状态 - 使用背景选择器的激活状态代替alpha修改
     */
    private fun updateNumericButtonState(view: View) {
        val numericButton = view.findViewById<ImageButton>(R.id.btn_toggle_numeric)
        numericButton.isActivated = isNumericMode
    }

    /**
     * 更新功能键按钮状态 - 使用背景选择器的激活状态代替alpha修改
     */
    private fun updateFunctionButtonState(view: View) {
        val functionButton = view.findViewById<ImageButton>(R.id.btn_toggle_function)
        functionButton.isActivated = isFunctionMode
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
                setBackgroundResource(R.drawable.floating_key_button_bg_enhanced)
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
                setBackgroundResource(R.drawable.floating_key_button_bg_enhanced)
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

    /**
     * 加载保存的状态
     */
    private fun loadSavedStates() {
        isNumericMode = prefs.getBoolean(KEY_NUMERIC_MODE, false)
        isFunctionMode = prefs.getBoolean(KEY_FUNCTION_MODE, false)
        currentOpacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        isDragging = prefs.getBoolean(KEY_DRAGGING_MODE, false)
        
        Log.d("FloatingKeyboard", "Loaded states: numeric=$isNumericMode, function=$isFunctionMode, opacity=$currentOpacity, dragging=$isDragging")
    }

    /**
     * 保存当前状态
     */
    private fun saveCurrentStates() {
        // 保存位置
        val window = dialog?.window
        val attributes = window?.attributes
        attributes?.let {
            prefs.edit().apply {
                putInt(KEY_POSITION_X, it.x)
                putInt(KEY_POSITION_Y, it.y)
                putBoolean(KEY_NUMERIC_MODE, isNumericMode)
                putBoolean(KEY_FUNCTION_MODE, isFunctionMode)
                putFloat(KEY_OPACITY, currentOpacity)
                putBoolean(KEY_DRAGGING_MODE, isDragging)
                apply()
            }
            Log.d("FloatingKeyboard", "Saved states: pos(${it.x},${it.y}), numeric=$isNumericMode, function=$isFunctionMode, opacity=$currentOpacity, dragging=$isDragging")
        }
    }

    /**
     * 保存当前位置（拖拽结束时调用）
     */
    private fun saveCurrentPosition() {
        val window = dialog?.window
        val attributes = window?.attributes
        attributes?.let {
            prefs.edit().apply {
                putInt(KEY_POSITION_X, it.x)
                putInt(KEY_POSITION_Y, it.y)
                apply()
            }
            Log.d("FloatingKeyboard", "Position saved: x=${it.x}, y=${it.y}")
        }
    }

    /**
     * 保存键盘模式状态（切换模式时调用）
     */
    private fun saveKeyboardModeStates() {
        prefs.edit().apply {
            putBoolean(KEY_NUMERIC_MODE, isNumericMode)
            putBoolean(KEY_FUNCTION_MODE, isFunctionMode)
            apply()
        }
        Log.d("FloatingKeyboard", "Keyboard modes saved: numeric=$isNumericMode, function=$isFunctionMode")
    }

    /**
     * 调整透明度
     */
    private fun adjustOpacity(view: View) {
        val oldOpacity = currentOpacity
        
        // 循环切换透明度: 0.95 -> 0.8 -> 0.6 -> 0.4 -> 0.95
        currentOpacity = when {
            currentOpacity > 0.9f -> 0.8f
            currentOpacity > 0.7f -> 0.6f
            currentOpacity > 0.5f -> 0.4f
            else -> 0.95f
        }
        
        Log.d("FloatingKeyboard", "Opacity changed from $oldOpacity to $currentOpacity")
        applyOpacity(view)
        
        // 立即保存新的透明度设置
        prefs.edit().putFloat(KEY_OPACITY, currentOpacity).apply()
        Log.d("FloatingKeyboard", "Opacity saved to preferences")
    }

    /**
     * 应用透明度到整个键盘
     */
    private fun applyOpacity(view: View) {
        view.alpha = currentOpacity
        Log.d("FloatingKeyboard", "Applied opacity $currentOpacity to view")
    }

} 