package com.limelight.heokami

import android.app.Dialog
import android.app.DialogFragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.nvstream.input.KeyboardPacket

class VirtualKeyboardDialogFragment : DialogFragment() {

    private lateinit var game: Game
    private lateinit var virtualKeyboard: VirtualKeyboard
    private val pressedButtons = mutableSetOf<Button>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(activity, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.virtual_keyboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        game = activity as Game
        virtualKeyboard = game.getVirtualKeyboard()
        
        setupKeyboardButtons(view)
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
            
            // 使用触摸事件来处理按下和释放
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d("VirtualKeyboard", "Button pressed: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")
                        
                        // 记录按下的按钮
                        pressedButtons.add(button)
                        
                        // 获取键盘输入上下文
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        
                        // 检查是否是修饰键
                        val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())
                        Log.d("VirtualKeyboard", "Modifier mask: 0x${modifierMask.toString(16).uppercase()}")
                        
                        if (modifierMask != 0.toByte()) {
                            // 这是修饰键，按下时设置修饰符并发送按键
                            inputContext.modifier = (inputContext.modifier.toInt() or modifierMask.toInt()).toByte()
                            button.setBackgroundResource(R.drawable.keyboard_key_pressed_bg)
                            virtualKeyboard.sendDownKey(vkCode.toShort())
                            Log.d("VirtualKeyboard", "Modifier key pressed: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
                        } else {
                            // 这是普通键，直接发送按键并设置按下状态
                            Log.d("VirtualKeyboard", "Sending regular key: ${button.text}")
                            button.setBackgroundResource(R.drawable.keyboard_key_pressed_bg)
                            virtualKeyboard.sendDownKey(vkCode.toShort())
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d("VirtualKeyboard", "Button released: ${button.text}, vkCode: 0x${vkCode.toString(16).uppercase()}")
                        
                        // 移除按下的按钮
                        pressedButtons.remove(button)
                        
                        // 获取键盘输入上下文
                        val inputContext = virtualKeyboard.getKeyboardInputContext()
                        
                        // 检查是否是修饰键
                        val modifierMask = VirtualKeyboardVkCode.replaceSpecialKeys(vkCode.toShort())
                        
                        if (modifierMask != 0.toByte()) {
                            // 这是修饰键，释放时清除修饰符并发送按键释放
                            inputContext.modifier = (inputContext.modifier.toInt() and modifierMask.toInt().inv()).toByte()
                            button.setBackgroundResource(R.drawable.keyboard_key_bg)
                            virtualKeyboard.sendUpKey(vkCode.toShort())
                            Log.d("VirtualKeyboard", "Modifier key released: ${button.text}, modifier: 0x${inputContext.modifier.toString(16).uppercase()}")
                        } else {
                            // 这是普通键，发送按键释放并恢复背景
                            Log.d("VirtualKeyboard", "Releasing regular key: ${button.text}")
                            button.setBackgroundResource(R.drawable.keyboard_key_bg)
                            virtualKeyboard.sendUpKey(vkCode.toShort())
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 处理触摸取消事件，确保按键状态正确
                        Log.d("VirtualKeyboard", "Button touch cancelled: ${button.text}")
                        pressedButtons.remove(button)
                        button.setBackgroundResource(R.drawable.keyboard_key_bg)
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

    companion object {
        fun newInstance(): VirtualKeyboardDialogFragment {
            return VirtualKeyboardDialogFragment()
        }
    }
} 