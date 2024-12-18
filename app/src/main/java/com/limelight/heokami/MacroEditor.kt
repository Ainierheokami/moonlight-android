package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.heokami.VirtualKeyboardVkCode.replaceSpecialKeys
import org.json.JSONObject
import java.lang.reflect.Type
import kotlin.experimental.inv

data class MacroAction(var type: String, var data: Int)
enum class MacroType(val displayName: String) { // 添加 displayName 方便显示
    KEY_UP("按键抬起"),
    KEY_DOWN("按键按下"),
    SLEEP("延迟");
}

interface OnMacroDataChangedListener {
    fun onMacroDataChanged(newData: JSONObject)
}

fun getIndexByDisplayName(displayName: String): Int {
    return MacroType.entries.toTypedArray().indexOfFirst { it.displayName == displayName }
}

class MacroEditor(private val context: Context, private var jsonData: JSONObject, private val listener: OnMacroDataChangedListener?) {

    private val gson = Gson()
    private val macroActions = loadMacro()

    private fun loadMacro(): MutableList<MacroAction> {
        try {
            val type: Type = TypeToken.getParameterized(Map::class.java, String::class.java, MacroAction::class.java).type
            val map: Map<String, MacroAction>? = gson.fromJson(jsonData.toString(), type)
            return map?.values?.toMutableList() ?: mutableListOf() // 将 Map 的值转换为 List
        } catch (e: Exception) {
            Toast.makeText(context, "加载宏失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MacroEditor", "加载宏失败: ${e.message}", e)
            return mutableListOf()
        }
    }

    private fun saveMacro(macroActions:List<MacroAction>) {
        //将macroActions转换成json数据
        val macroMap = macroActions.mapIndexed { index, macroAction ->
            index.toString() to macroAction
        }.toMap()
        jsonData = JSONObject(gson.toJson(macroMap))
        listener?.onMacroDataChanged(jsonData) // 调用回调函数，传递修改后的数据
    }

    @SuppressLint("SetTextI18n")
    fun showMacroEditor() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("宏编辑器")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(layout)
        }
        builder.setView(scrollView)

        builder.setPositiveButton(context.getString(R.string.macro_editor_add_button)) { _, _ ->
            showAddMacroDialog()
        }

        builder.setNeutralButton(context.getString(R.string.virtual_keyboard_menu_save_button)) { _, _ ->
            saveMacro(macroActions) // 保存修改后的副本

        }

        builder.setNegativeButton(context.getString(R.string.cancel_button), null)
        val dialog = builder.create()
        updateMacroDisplay(layout, macroActions, dialog)
        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun updateMacroDisplay(layout: LinearLayout, actions: MutableList<MacroAction>, dialog: AlertDialog) {
        Log.d("MacroEditor", "更新显示: $actions")
        layout.removeAllViews() // 每次更新前先清空所有 View
        actions.forEachIndexed { index, action ->
            val horizontalLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val editText = EditText(context).apply {
                setText("Index: $index, Type: ${action.type}, Data: ${action.data}")
                isFocusable = false // 不允许编辑，只用于展示
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val deleteButton = Button(context).apply {
                text = context.getString(R.string.del_button)
                setOnClickListener {
                    actions.removeAt(index)
                    updateMacroDisplay(layout, actions, dialog) // 重新绘制 UI
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val editButton = Button(context).apply {
                text = context.getString(R.string.edit_button)
                setOnClickListener {
                    showAddMacroDialog(index)
                    dialog.dismiss()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            horizontalLayout.addView(editText)
            horizontalLayout.addView(editButton)
            horizontalLayout.addView(deleteButton)
            layout.addView(horizontalLayout)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showAddMacroDialog(index: Int = -1) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("编辑宏操作")

        var macroType = MacroType.KEY_UP

        val contextThemeWrapper = ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)
        val tabLayout = TabLayout(contextThemeWrapper).apply {
            addTab(newTab().setText(MacroType.KEY_UP.displayName))
            addTab(newTab().setText(MacroType.KEY_DOWN.displayName))
            addTab(newTab().setText(MacroType.SLEEP.displayName))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        // 设置 TabLayout 的监听器
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 当选项卡被选中时调用
                if (tab != null) {
                    macroType = MacroType.entries.toTypedArray()[tab.position]
                    // 在这里执行与所选选项卡相关的操作
                    when (tab.position) {
                        0 -> macroType = MacroType.KEY_UP
                        1 -> macroType = MacroType.KEY_DOWN
                        2 -> macroType = MacroType.SLEEP
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // 当选项卡取消选中时调用 (通常在切换选项卡时会触发)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 当已选中的选项卡再次被点击时调用
            }
        })

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val macroData = EditText(context).apply{
            hint="Data(int)"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )}
        val vkButton = Button(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_vk_code_button)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                VirtualKeyboardMenu.showVKCodeDialog(context, null, macroData)
            }
        }
        linearLayout.addView(macroData)
        linearLayout.addView(vkButton)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabLayout)
            addView(linearLayout)
        }
        builder.setView(layout)

        if(index != -1 && index <= macroActions.size){
            macroData.setText(macroActions[index].data.toString())
            tabLayout.selectTab(tabLayout.getTabAt(getIndexByDisplayName(macroActions[index].type)))
            macroType = MacroType.valueOf(macroActions[index].type)
        }

        builder.setPositiveButton(context.getString(R.string.confirm_button)) { _, _ ->
            val type = macroType.toString()
            val data = macroData.text.toString().toIntOrNull()?:0
            if (type.isNotBlank()){
                Log.d("MacroEditor", "添加宏操作前: $macroActions")
                if(index == -1){
                    macroActions.add(MacroAction(type, data))
                }else{
                    macroActions[index].type = type
                    macroActions[index].data = data
                }
                Log.d("MacroEditor", "添加宏操作: Type: $type, Data: $data")
                Log.d("MacroEditor", "添加宏操作: $macroActions")
                showMacroEditor()
            }

        }
        builder.setNegativeButton(context.getString(R.string.cancel_button), null)
        builder.setNeutralButton(context.getString(R.string.virtual_keyboard_menu_copy_button), { _, _ ->
            val type = macroType.toString()
            val data = macroData.text.toString().toIntOrNull()?:0
            macroActions.add(MacroAction(type, data))
            showMacroEditor()
        })
        builder.show()
    }

    private val handler = Handler(Looper.getMainLooper())

    fun runMacroAction(virtualKeyboard: VirtualKeyboard) {
        Log.d("MacroEditor", "开始执行宏操作，总共有 ${macroActions.size} 个操作") // 添加日志
        executeMacroAction(virtualKeyboard, 0)
    }

    private fun executeMacroAction(virtualKeyboard: VirtualKeyboard, index: Int) {
        Log.d("MacroEditor", "executeMacroAction: 当前执行索引：$index") // 添加日志

        if (index >= macroActions.size) {
            Log.d("MacroEditor", "所有宏操作执行完毕") // 添加日志
            return
        }

        val action = macroActions[index]
        Log.d("MacroEditor", "$index-执行宏操作: Type: ${action.type}, Data: ${action.data}")
        val inputContext = virtualKeyboard.controllerInputContext

        when (action.type) {
            MacroType.KEY_UP.toString() -> {
                inputContext.modifier = (inputContext.modifier.toInt() and replaceSpecialKeys(action.data.toShort()).inv().toInt()).toByte()
                virtualKeyboard.sendUpKey(action.data.toShort())
                executeNextActionWithDelay(virtualKeyboard, index, 0) // KEY_UP 后立即执行下一个
            }
            MacroType.KEY_DOWN.toString() -> {
                inputContext.modifier = (inputContext.modifier.toInt() or replaceSpecialKeys(action.data.toShort()).toInt()).toByte()
                virtualKeyboard.sendDownKey(action.data.toShort())
                executeNextActionWithDelay(virtualKeyboard, index, 0) // KEY_DOWN 后立即执行下一个
            }
            MacroType.SLEEP.toString() -> {
                val delay = action.data.toLong()
                Log.d("MacroEditor", "延迟 $delay 毫秒") // 添加日志，检查延迟值
                executeNextActionWithDelay(virtualKeyboard, index, delay) // SLEEP 后延迟执行下一个
            }
        }
    }

    private fun executeNextActionWithDelay(virtualKeyboard: VirtualKeyboard, currentIndex: Int, delay: Long) {
        handler.postDelayed({
            Log.d("MacroEditor", "延迟结束，准备执行下一个操作，当前索引：${currentIndex + 1}") // 添加日志
            executeMacroAction(virtualKeyboard, currentIndex + 1)
        }, delay)
    }
}