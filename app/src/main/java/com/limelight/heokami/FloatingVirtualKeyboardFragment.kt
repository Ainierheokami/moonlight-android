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
import android.util.DisplayMetrics
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
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
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val unSnapThreshold = 15  // dp，拖动超过此距离就解除吸附
    
    // 大小调整相关变量
    private var isResizingEnabled = false
    private var isResizing = false
    private var currentWidth = 400 // dp
    private var currentHeight = 300 // dp
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeMode = ResizeMode.NONE
    
    // 边缘检测相关常量 (dp)
    private val edgeDetectionWidth = 20
    private val cornerDetectionSize = 30
    private val resizeTouchExtension = 12
    private val snapThreshold = 20  // 降低吸附阈值，避免吸附后难以拖动
    
    // 尺寸限制 (dp)
    private val minWidth = 300
    private var maxWidth = 700  // 将在初始化时设置为屏幕宽度
    private val minHeight = 200
    private val maxHeight = 500
    
    // 调整大小模式枚举
    enum class ResizeMode {
        NONE, LEFT, RIGHT, TOP, BOTTOM, 
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }
    
    companion object {
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private const val KEY_NUMERIC_MODE = "numeric_mode"
        private const val KEY_FUNCTION_MODE = "function_mode"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_DRAGGING_MODE = "dragging_mode"
        private const val KEY_RESIZE_ENABLED = "resize_enabled"
        private const val KEY_KEYBOARD_WIDTH = "keyboard_width"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
        
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
        
        // 初始化屏幕相关的限制
        initializeScreenConstraints()
        
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
            
            // 禁用背景变暗效果，实现完全透明
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0.0f)
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
        updateResizeButtonState(view) // 恢复调整大小按钮状态
        
        // 应用按键高度自适应
        applyKeyHeightAdaptation()
        
        Log.d("FloatingKeyboard", "onViewCreated() completed")
    }

    override fun onResume() {
        super.onResume()

        // 在 onResume 中设置可确保覆盖所有布局的 wrap_content 属性
        dialog?.window?.let { window ->
            val params = window.attributes

            // 恢复尺寸
            params.width = dpToPx(currentWidth)
            params.height = dpToPx(currentHeight)

            // 恢复位置
            val savedX = prefs.getInt(KEY_POSITION_X, -1) // -1 用于检测首次运行
            val savedY = prefs.getInt(KEY_POSITION_Y, -1)
            
            if (savedX == -1 && savedY == -1) {
                params.gravity = android.view.Gravity.CENTER
            } else {
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                params.x = savedX
                params.y = savedY
            }
            
            window.attributes = params
            Log.d("FloatingKeyboard", "Applied final window attributes in onResume")
        }
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

        // 大小调整按钮
        view.findViewById<ImageButton>(R.id.btn_resize_keyboard)?.setOnClickListener {
            isResizingEnabled = !isResizingEnabled
            updateResizeButtonState(view)
            // 立即保存调整大小状态
            prefs.edit().putBoolean(KEY_RESIZE_ENABLED, isResizingEnabled).apply()
            Log.d("FloatingKeyboard", "Resize mode ${if (isResizingEnabled) "enabled" else "disabled"}, saved to prefs")
        } ?: Log.e("FloatingKeyboard", "btn_resize_keyboard not found in layout")

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
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handleKeyPress(button, true)
                    button.isSelected = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isPointInsideView(event.x, event.y, button) && button.isSelected) {
                        handleKeyPress(button, false)
                        button.isSelected = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (button.isSelected) {
                        handleKeyPress(button, false)
                        button.isSelected = false
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    if (button.isSelected) {
                        handleKeyPress(button, false)
                        button.isSelected = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 判断坐标是否仍在控件内部
     */
    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        return x >= 0 && x <= view.width && y >= 0 && y <= view.height
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
     * 设置拖拽监听器（支持移动和大小调整）
     */
    private fun setupDragListener(view: View) {
        // 获取根容器和功能栏
        val rootContainer = view as LinearLayout  // 主布局容器
        val functionBar = view.findViewById<LinearLayout>(R.id.function_bar)
        
        // 为整个键盘设置触摸监听器（用于大小调整）
        rootContainer.setOnTouchListener { _, event ->
            if (isResizingEnabled && !isDragging) {
                handleResizeTouch(event)
            } else false
        }
        
        // 为功能条设置触摸监听器（用于移动）
        functionBar.setOnTouchListener { _, event ->
            if (isDragging && !isResizing) {
                handleMoveTouch(event)
            } else false
        }
    }
    
    /**
     * 处理移动触摸事件（带智能边缘吸附）
     */
    private fun handleMoveTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录触摸开始位置
                lastX = event.rawX
                lastY = event.rawY
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                Log.d("FloatingKeyboard", "Move started at: rawX=$lastX, rawY=$lastY")
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // 计算移动距离
                val deltaX = event.rawX - lastX
                val deltaY = event.rawY - lastY
                
                // 计算从初始位置的总移动距离
                val totalMoveX = abs(event.rawX - initialTouchX)
                val totalMoveY = abs(event.rawY - initialTouchY)
                val totalMove = kotlin.math.sqrt((totalMoveX * totalMoveX + totalMoveY * totalMoveY).toDouble()).toFloat()
                val unSnapThresholdPx = dpToPx(unSnapThreshold).toFloat()
                
                // 获取当前窗口属性
                val window = dialog.window
                val attributes = window?.attributes
                
                attributes?.let {
                    // 计算新位置
                    val newX = it.x + deltaX.toInt()
                    val newY = it.y + deltaY.toInt()
                    
                    // 简化的智能吸附逻辑
                    val finalPos = if (totalMove < unSnapThresholdPx) {
                        // 拖动距离较小，应用吸附
                        applyEdgeSnapping(newX, newY)
                    } else {
                        // 拖动距离较大，暂时禁用吸附，允许自由移动
                        Pair(newX, newY)
                    }
                    
                    // 应用新位置
                    it.x = finalPos.first
                    it.y = finalPos.second
                    window.attributes = it
                    
                    Log.d("FloatingKeyboard", "Moving keyboard: deltaX=$deltaX, deltaY=$deltaY, newX=${finalPos.first}, newY=${finalPos.second}, totalMove=${totalMove.toInt()}px")
                }
                
                // 更新上次位置
                lastX = event.rawX
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 拖拽结束，保存位置
                Log.d("FloatingKeyboard", "Move ended at: rawX=${event.rawX}, rawY=${event.rawY}")
                saveCurrentPosition()
                return true
            }
        }
        return false
    }
    
    /**
     * 处理大小调整触摸事件
     */
    private fun handleResizeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检测触摸位置，确定调整模式
                resizeMode = detectResizeMode(event.x, event.y)
                if (resizeMode != ResizeMode.NONE) {
                    isResizing = true
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    Log.d("FloatingKeyboard", "Resize started: mode=$resizeMode")
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing && resizeMode != ResizeMode.NONE) {
                    val deltaX = event.rawX - resizeStartX
                    val deltaY = event.rawY - resizeStartY
                    
                    applyResize(deltaX, deltaY)
                    
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (isResizing) {
                    isResizing = false
                    resizeMode = ResizeMode.NONE
                    saveCurrentSize()
                    Log.d("FloatingKeyboard", "Resize ended")
                    return true
                }
                return false
            }
        }
        return false
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
     * 更新调整大小按钮状态
     */
    private fun updateResizeButtonState(view: View) {
        val resizeButton = view.findViewById<ImageButton>(R.id.btn_resize_keyboard)
        resizeButton.isActivated = isResizingEnabled
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
        
        // 应用按键高度自适应
        applyKeyHeightAdaptation()
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
        
        // 应用按键高度自适应
        applyKeyHeightAdaptation()
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
        
        // 应用按键高度自适应
        applyKeyHeightAdaptation()
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
                // 使用自适应高度
                val keyHeight = ((currentHeight - 50) / 7).coerceAtLeast(32)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(keyHeight),
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
                // 使用自适应高度
                val keyHeight = ((currentHeight - 50) / 7).coerceAtLeast(32)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(keyHeight),
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
        isResizingEnabled = prefs.getBoolean(KEY_RESIZE_ENABLED, false)
        currentWidth = prefs.getInt(KEY_KEYBOARD_WIDTH, 400)
        currentHeight = prefs.getInt(KEY_KEYBOARD_HEIGHT, 300)
        
        Log.d("FloatingKeyboard", "Loaded states from Prefs: numeric=$isNumericMode, function=$isFunctionMode, opacity=$currentOpacity, dragging=$isDragging, resizing=$isResizingEnabled, size=${currentWidth}x${currentHeight}dp")
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
                putBoolean(KEY_RESIZE_ENABLED, isResizingEnabled)
                putInt(KEY_KEYBOARD_WIDTH, currentWidth)
                putInt(KEY_KEYBOARD_HEIGHT, currentHeight)
                apply()
            }
            Log.d("FloatingKeyboard", "Saved states: pos(${it.x},${it.y}), numeric=$isNumericMode, function=$isFunctionMode, opacity=$currentOpacity, dragging=$isDragging, resizing=$isResizingEnabled, size=${currentWidth}x${currentHeight}dp")
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

    /**
     * 保存当前大小（调整大小结束时调用）
     */
    private fun saveCurrentSize() {
        prefs.edit().apply {
            putInt(KEY_KEYBOARD_WIDTH, currentWidth)
            putInt(KEY_KEYBOARD_HEIGHT, currentHeight)
            apply()
        }
        Log.d("FloatingKeyboard", "Size saved: ${currentWidth}x${currentHeight}dp")
    }

    /**
     * 检测调整大小模式
     */
    private fun detectResizeMode(x: Float, y: Float): ResizeMode {
        val view = dialog?.window?.decorView ?: return ResizeMode.NONE
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        
        val extensionPx = dpToPx(resizeTouchExtension).toFloat()
        val edgeWidthRaw = dpToPx(edgeDetectionWidth).toFloat()
        val cornerSizeRaw = dpToPx(cornerDetectionSize).toFloat()
        
        val cornerSizePx = min(cornerSizeRaw + extensionPx, min(width, height) / 2f)
        val horizontalEdgePx = min(edgeWidthRaw + extensionPx, width / 2f)
        val verticalEdgePx = min(edgeWidthRaw + extensionPx, height / 2f)
        
        // 检测角落区域（优先级高）
        if (x <= cornerSizePx && y <= cornerSizePx) return ResizeMode.TOP_LEFT
        if (x >= width - cornerSizePx && y <= cornerSizePx) return ResizeMode.TOP_RIGHT
        if (x <= cornerSizePx && y >= height - cornerSizePx) return ResizeMode.BOTTOM_LEFT
        if (x >= width - cornerSizePx && y >= height - cornerSizePx) return ResizeMode.BOTTOM_RIGHT
        
        // 检测边缘区域
        if (x <= horizontalEdgePx) return ResizeMode.LEFT
        if (x >= width - horizontalEdgePx) return ResizeMode.RIGHT
        if (y <= verticalEdgePx) return ResizeMode.TOP
        if (y >= height - verticalEdgePx) return ResizeMode.BOTTOM
        
        return ResizeMode.NONE
    }

    /**
     * 应用大小调整
     */
    private fun applyResize(deltaX: Float, deltaY: Float) {
        val window = dialog?.window ?: return
        val attributes = window.attributes
        
        val deltaXDp = pxToDp(deltaX.toInt())
        val deltaYDp = pxToDp(deltaY.toInt())
        
        var newWidth = currentWidth
        var newHeight = currentHeight
        
        // 根据调整模式计算新尺寸
        when (resizeMode) {
            ResizeMode.RIGHT -> {
                newWidth += deltaXDp
            }
            ResizeMode.LEFT -> {
                newWidth -= deltaXDp
                // 左边调整时需要移动位置
                attributes.x += dpToPx(deltaXDp)
            }
            ResizeMode.BOTTOM -> {
                newHeight += deltaYDp
            }
            ResizeMode.TOP -> {
                newHeight -= deltaYDp
                // 上边调整时需要移动位置
                attributes.y += dpToPx(deltaYDp)
            }
            ResizeMode.BOTTOM_RIGHT -> {
                newWidth += deltaXDp
                newHeight += deltaYDp
            }
            ResizeMode.BOTTOM_LEFT -> {
                newWidth -= deltaXDp
                newHeight += deltaYDp
                attributes.x += dpToPx(deltaXDp)
            }
            ResizeMode.TOP_RIGHT -> {
                newWidth += deltaXDp
                newHeight -= deltaYDp
                attributes.y += dpToPx(deltaYDp)
            }
            ResizeMode.TOP_LEFT -> {
                newWidth -= deltaXDp
                newHeight -= deltaYDp
                attributes.x += dpToPx(deltaXDp)
                attributes.y += dpToPx(deltaYDp)
            }
            else -> return
        }
        
        // 应用尺寸限制
        newWidth = newWidth.coerceIn(minWidth, maxWidth)
        newHeight = newHeight.coerceIn(minHeight, maxHeight)
        
        // 更新当前尺寸
        currentWidth = newWidth
        currentHeight = newHeight
        
        // 应用新尺寸
        attributes.width = dpToPx(currentWidth)
        attributes.height = dpToPx(currentHeight)
        window.attributes = attributes
        
        // 应用按键高度自适应
        applyKeyHeightAdaptation()
        
        Log.d("FloatingKeyboard", "Resized to: ${currentWidth}x${currentHeight}dp, mode=$resizeMode")
    }

    /**
     * 应用边缘吸附
     */
    private fun applyEdgeSnapping(x: Int, y: Int): Pair<Int, Int> {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val keyboardWidth = dpToPx(currentWidth)
        val keyboardHeight = dpToPx(currentHeight)
        val snapThresholdPx = dpToPx(snapThreshold)
        
        var newX = x
        var newY = y
        
        // 水平吸附
        if (x <= snapThresholdPx) {
            newX = 0  // 左边缘
            Log.d("FloatingKeyboard", "Snapped to left edge")
        } else if (x + keyboardWidth >= screenWidth - snapThresholdPx) {
            newX = screenWidth - keyboardWidth  // 右边缘
            Log.d("FloatingKeyboard", "Snapped to right edge")
        }
        
        // 垂直吸附
        if (y <= snapThresholdPx) {
            newY = 0  // 上边缘
            Log.d("FloatingKeyboard", "Snapped to top edge")
        } else if (y + keyboardHeight >= screenHeight - snapThresholdPx) {
            newY = screenHeight - keyboardHeight  // 下边缘
            Log.d("FloatingKeyboard", "Snapped to bottom edge")
        }
        
        return Pair(newX, newY)
    }

    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /**
     * px转dp
     */
    private fun pxToDp(px: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (px / density).toInt()
    }

    /**
     * 初始化屏幕约束
     */
    private fun initializeScreenConstraints() {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidthPx = displayMetrics.widthPixels
        maxWidth = pxToDp(screenWidthPx) // 设置最大宽度为屏幕宽度
        
        Log.d("FloatingKeyboard", "Screen constraints initialized: maxWidth=${maxWidth}dp (${screenWidthPx}px)")
    }

    /**
     * 应用按键高度自适应
     */
    private fun applyKeyHeightAdaptation() {
        val rootView = this.view ?: return
        val keyboardContainer = rootView.findViewById<LinearLayout>(R.id.keyboard_container) ?: run {
            Log.w("FloatingKeyboard", "Keyboard container not found")
            return
        }

        val rowCount = countButtonRowsRecursive(keyboardContainer)
        if (rowCount == 0) {
            Log.w("FloatingKeyboard", "No button rows detected, skip height adaptation")
            return
        }

        val totalKeyboardHeight = currentHeight - 50 // 减去功能条和边距
        val keyHeight = (totalKeyboardHeight / rowCount).coerceAtLeast(32) // 最小32dp

        updateButtonHeights(keyboardContainer, keyHeight)

        Log.d("FloatingKeyboard", "Key height adapted: ${keyHeight}dp for ${rowCount} rows at keyboard height ${currentHeight}dp")
    }

    /**
     * 统计含按钮的行数
     */
    private fun countButtonRowsRecursive(container: ViewGroup): Int {
        var rows = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is LinearLayout) {
                if (containsButtonChild(child)) {
                    rows++
                } else {
                    rows += countButtonRowsRecursive(child)
                }
            }
        }
        return rows
    }

    private fun containsButtonChild(layout: LinearLayout): Boolean {
        for (i in 0 until layout.childCount) {
            if (layout.getChildAt(i) is Button) {
                return true
            }
        }
        return false
    }

    /**
     * 更新所有按钮的高度
     */
    private fun updateButtonHeights(viewGroup: ViewGroup, keyHeight: Int) {
        val keyHeightPx = dpToPx(keyHeight)
        
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is Button -> {
                    val params = child.layoutParams
                    params.height = keyHeightPx
                    child.layoutParams = params
                }
                is ViewGroup -> updateButtonHeights(child, keyHeight)
            }
        }
    }

}
