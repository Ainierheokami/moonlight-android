package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardElement
import com.limelight.heokami.VirtualKeyboardVkCode.replaceSpecialKeys
import com.limelight.heokami.FloatingVirtualKeyboardFragment
import org.json.JSONObject
import java.util.Collections
import kotlin.experimental.inv

data class MacroAction(var type: String, var data: Int)
enum class MacroType(@StringRes val displayNameRes: Int) {
    KEY_UP(R.string.macro_type_key_up),
    KEY_DOWN(R.string.macro_type_key_down),
    SLEEP(R.string.macro_type_sleep),
    KEY_TOGGLE(R.string.macro_type_key_toggle),
    KEY_TOGGLE_GROUP(R.string.macro_type_key_toggle_group),
    TOUCH_TOGGLE(R.string.macro_type_touch_toggle),
    FLOATING_KEYBOARD_TOGGLE(R.string.macro_type_floating_keyboard_toggle),
    TOUCHPAD_TOGGLE(R.string.macro_type_touchpad_toggle),
    PORTAL_TOGGLE(R.string.macro_type_portal_toggle);

    fun getDisplayName(context: Context): String {
        return context.getString(displayNameRes)
    }
}

interface OnMacroDataChangedListener {
    fun onMacroDataChanged(newData: JSONObject)
}

fun getIndexByDisplayName(displayName: String, context: Context): Int {
    return MacroType.entries.toTypedArray().indexOfFirst { it.getDisplayName(context) == displayName }
}

fun getDisplayNameByType(type: String, context: Context): String {
    return MacroType.entries.toTypedArray().find { it.toString() == type }?.getDisplayName(context) ?: ""
}

class MacroEditor(private val context: Context, private var jsonData: JSONObject, private val listener: OnMacroDataChangedListener?) {

    private val gson = Gson()
    private val macroActions: MutableList<MacroAction>

    // 约定：宏数据专属容器键。为避免与按键外观样式（同存于 buttonData）冲突，
    // 新版本将宏持久化到 buttonData.MACROS 下；若不存在则向后兼容读取顶层数值键（旧格式）。
    private val MACRO_CONTAINER_KEY = "MACROS"

    private val elements = mutableListOf<VirtualKeyboardElement>()

    init {
        Log.d("MacroEditor", "--- MacroEditor Initializing ---")
        Log.d("MacroEditor", "Constructor received jsonData: ${jsonData.toString(2)}")
        macroActions = loadMacro()
        Log.d("MacroEditor", "Initial macroActions loaded: $macroActions")
        Log.d("MacroEditor", "--- MacroEditor Initialization Complete ---")
    }

    fun setElements(elements: List<VirtualKeyboardElement>) {
        this.elements.clear()
        this.elements.addAll(elements)
    }

    private fun loadMacro(): MutableList<MacroAction> {
        Log.d("MacroEditor", "--- loadMacro started ---")
        val actions = mutableListOf<MacroAction>()
        try {
            Log.d("MacroEditor", "loadMacro: incoming jsonData: ${jsonData.toString(2)}")
            // 1) 优先从嵌套容器 MACROS 读取（新格式）
            val container = jsonData.optJSONObject(MACRO_CONTAINER_KEY) ?: jsonData
            Log.d("MacroEditor", "loadMacro: using container: ${container.toString(2)}")


            // --- BUGFIX: 确保旧格式宏的加载顺序 ---
            // 获取所有键并进行排序，以确保旧格式宏的加载顺序正确
            val keysIterator = container.keys()
            val keyList = mutableListOf<String>()
            while (keysIterator.hasNext()) {
                keyList.add(keysIterator.next())
            }
            Log.d("MacroEditor", "loadMacro: unsorted keys: $keyList")


            // 尝试按数字大小排序；如果键不是纯数字（新格式或非宏数据），则按原序
            try {
                keyList.sortBy { it.toInt() }
                Log.d("MacroEditor", "loadMacro: sorted keys numerically: $keyList")
            } catch (e: NumberFormatException) {
                // 包含非数字键，可能是新格式或包含其他数据，无需特殊排序
                Log.d("MacroEditor", "loadMacro: keys contain non-numeric values, not sorting.")
            }

            for (key in keyList) {
                val value = container.opt(key)
                // 仅解析形如 {"type":"KEY_DOWN","data":13} 的对象；忽略外观样式等非宏字段
                if (value is JSONObject) {
                    val hasType = value.has("type") && !value.optString("type").isNullOrBlank()
                    val hasData = value.has("data")
                    if (hasType && hasData) {
                        try {
                            val newAction = gson.fromJson(value.toString(), MacroAction::class.java)
                            actions.add(newAction)
                            Log.d("MacroEditor", "loadMacro: successfully parsed and added action for key '$key': $newAction")
                        } catch (e: Exception) {
                            // 单条宏不合法时跳过，避免整体失败
                            Log.w("MacroEditor", "跳过非法宏项 key=$key: ${e.message}")
                        }
                    } else {
                        Log.d("MacroEditor", "loadMacro: skipping key '$key' as it's not a valid macro action object.")
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "加载宏失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MacroEditor", "加载宏失败: ${e.message}", e)
        }
        Log.d("MacroEditor", "--- loadMacro finished, actions count: ${actions.size} ---")
        return actions
    }

    private fun saveMacro(macroActions: List<MacroAction>) {
        try {
            Log.d("MacroEditor", "--- saveMacro started ---")
            Log.d("MacroEditor", "saveMacro: received actions to save: $macroActions")
            // --- BUGFIX: 修复宏保存失败问题 (最终修复) ---
            // 直接在传入的 jsonData 对象上进行修改，而不是创建一个新对象。
            // 这可以避免因引用变更导致的数据更新失败。
            val dataToModify = this.jsonData
            Log.d("MacroEditor", "saveMacro: modifying jsonData object in-place: ${dataToModify.toString(2)}")


            // 1. 从原始 jsonData 中移除所有旧格式的、以数字为键的宏条目以及旧的宏容器
            val keysToRemove = mutableListOf<String>()
            val it = dataToModify.keys()
            while (it.hasNext()) {
                val k = it.next()
                // 移除所有旧的数字键和 MACROS 键，为写入新数据做准备
                if (k.matches(Regex("^\\d+$")) || k == MACRO_CONTAINER_KEY) {
                    keysToRemove.add(k)
                }
            }
            Log.d("MacroEditor", "saveMacro: keys to remove (old macros): $keysToRemove")
            for (key in keysToRemove) {
                dataToModify.remove(key)
            }
            Log.d("MacroEditor", "saveMacro: jsonData after removing old macros: ${dataToModify.toString(2)}")


            // 2. 如果有新的宏需要保存，创建 JSON 并添加到对象中
            if (macroActions.isNotEmpty()) {
                val macroMap = macroActions.mapIndexed { index, macroAction ->
                    index.toString() to macroAction
                }.toMap()
                val macrosJson = JSONObject(gson.toJson(macroMap))
                dataToModify.put(MACRO_CONTAINER_KEY, macrosJson)
                Log.d("MacroEditor", "saveMacro: added new macros container: ${macrosJson.toString(2)}")
            } else {
                Log.d("MacroEditor", "saveMacro: no new macros to save, MACROS container will be absent.")
            }

            // 3. 调用监听器。我们传递回的是被“就地修改”过的同一个对象实例。
            // 外部接收到后，只需触发保存即可，无需再进行赋值操作。
            Log.d("MacroEditor", "Calling onMacroDataChanged with modified jsonData: ${dataToModify.toString(2)}")
            listener?.onMacroDataChanged(dataToModify)
            Log.d("MacroEditor", "--- saveMacro finished ---")

        } catch (e: Exception) {
            Toast.makeText(context, "保存宏失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MacroEditor", "保存宏失败: ${e.message}", e)
        }
    }

    @SuppressLint("SetTextI18n")
    fun showMacroEditor() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.macro_editor_title))

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
        builder.setCancelable(false)
        val dialog = builder.create()
        updateMacroDisplay(layout, macroActions, dialog)
        dialog.show()
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var macroAdapter: MacroAdapter

    @SuppressLint("SetTextI18n")
    private fun updateMacroDisplay(layout: LinearLayout, actions: MutableList<MacroAction>, dialog: AlertDialog) {
        layout.removeAllViews() // 每次更新前先清空所有 View

        Log.d("MacroEditor", "更新显示: $actions")

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(this)
        }

        macroAdapter = MacroAdapter(actions, dialog, ::showAddMacroDialog, context).apply {
            recyclerView.adapter = this
        }

        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                macroAdapter.onItemMove(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不需要滑动删除
            }
        }).apply {
            attachToRecyclerView(recyclerView)
        }
        macroAdapter.submitList(actions.toList())
    }

    private fun hotKeyMacro() {
        // 将macroActions重新排序使全部按键先按下，并延迟35后释放
        val keyDownActions = mutableListOf<MacroAction>()
        val keyUpActions = mutableListOf<MacroAction>()
        val hotKeyActions = mutableListOf<MacroAction>()

        for (action in macroActions) {
            when (action.type) {
                MacroType.KEY_DOWN.toString() -> {
                    keyDownActions.add(action)
                }
                MacroType.KEY_UP.toString() -> {
                    keyUpActions.add(action)
                }
            }
        }

        // keyup 倒序
        keyUpActions.reverse()

        hotKeyActions.addAll(keyDownActions)
        hotKeyActions.add(MacroAction(MacroType.SLEEP.toString(), 35))
        hotKeyActions.addAll(keyUpActions)

        macroActions.clear()
        macroActions.addAll(hotKeyActions)
    }

    private var fastKeyDownAndUp = true
    private var fastHotKey = !fastKeyDownAndUp

    @SuppressLint("SetTextI18n")
    private fun showAddMacroDialog(index: Int = -1) {
        // dp 转 px 辅助函数
        fun dpToPx(dp: Int): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.macro_editor_add_title))
        var macroType = MacroType.KEY_UP
        lateinit var hintTextView: TextView

        val fastKeyDownUpCheckBox = CheckBox(context).apply {
            text = context.getString(R.string.macro_editor_fast_key_down_up)
            isChecked = fastKeyDownAndUp
        }

        val fastHotKeyCheckBox = CheckBox(context).apply {
            text = context.getString(R.string.macro_editor_fast_hot_key)
            isChecked = fastHotKey
        }

        fastKeyDownUpCheckBox.setOnClickListener {
            fastKeyDownAndUp = !fastKeyDownAndUp
            fastKeyDownUpCheckBox.isChecked = fastKeyDownAndUp
            // close fastHotKey
            if (fastKeyDownAndUp){
                fastHotKey = false
                fastHotKeyCheckBox.isChecked = fastHotKey
            }

        }

        fastHotKeyCheckBox.setOnClickListener {
            fastHotKey = !fastHotKey
            fastHotKeyCheckBox.isChecked = fastHotKey
            // close fastKeyDownAndUp
            if (fastHotKey){
                fastKeyDownAndUp = false
                fastKeyDownUpCheckBox.isChecked = fastKeyDownAndUp
            }
        }

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
        val idButton = Button(context).apply {
            text = "已有按键"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        linearLayout.addView(macroData)
        linearLayout.addView(vkButton)
        linearLayout.addView(idButton)

        val contextThemeWrapper = ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)
        val tabLayout = TabLayout(contextThemeWrapper).apply {
            for (type in MacroType.entries) {
                addTab(newTab().setText(type.getDisplayName(context)))
            }
            tabMode = TabLayout.MODE_SCROLLABLE
        }
        // 设置 TabLayout 的监听器
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 当选项卡被选中时调用
                if (tab != null) {
                    macroType = MacroType.entries.toTypedArray()[tab.position]
                    when(macroType){
                        MacroType.KEY_UP -> {
                            fastKeyDownUpCheckBox.visibility = View.VISIBLE
                            fastHotKeyCheckBox.visibility = View.VISIBLE
                            vkButton.visibility = View.VISIBLE
                            idButton.visibility = View.GONE
                            hintTextView.text = ""
                        }
                        MacroType.KEY_DOWN -> {
                            fastKeyDownUpCheckBox.visibility = View.VISIBLE
                            fastHotKeyCheckBox.visibility = View.VISIBLE
                            vkButton.visibility = View.VISIBLE
                            idButton.visibility = View.GONE
                            hintTextView.text = ""
                        }
                        MacroType.KEY_TOGGLE -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.VISIBLE
                            idButton.setOnClickListener {
                                VirtualKeyboardMenu.showHasButtonDialog(context, elements, macroData, null)
                            }
                            hintTextView.text = ""
                        }
                        MacroType.KEY_TOGGLE_GROUP -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.VISIBLE
                            idButton.setOnClickListener {
                                VirtualKeyboardMenu.showHasButtonDialog(context, elements, null, macroData)
                            }
                            hintTextView.text = ""
                        }
                        MacroType.TOUCH_TOGGLE -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.GONE
                            hintTextView.text = context.getString(R.string.macro_type_touch_toggle_hint)
                        }
                        MacroType.FLOATING_KEYBOARD_TOGGLE -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.GONE
                            hintTextView.text = ""
                        }
                        MacroType.TOUCHPAD_TOGGLE -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.GONE
                            hintTextView.text = context.getString(R.string.macro_type_touchpad_toggle_hint)
                        }
                        MacroType.PORTAL_TOGGLE -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.GONE
                            hintTextView.text = context.getString(R.string.macro_type_portal_toggle_hint)
                        }
                        else -> {
                            fastKeyDownUpCheckBox.visibility = View.GONE
                            fastHotKeyCheckBox.visibility = View.GONE
                            vkButton.visibility = View.GONE
                            idButton.visibility = View.GONE
                            hintTextView.text = ""
                        }
                    }
                    fastKeyDownUpCheckBox.invalidate()
                    fastHotKeyCheckBox.invalidate()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // 当选项卡取消选中时调用 (通常在切换选项卡时会触发)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 当已选中的选项卡再次被点击时调用
            }
        })



        hintTextView = TextView(context).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabLayout)
            addView(linearLayout)
            addView(hintTextView)
        }
        builder.setView(layout)

        if(index != -1 && index <= macroActions.size){
            macroData.setText(macroActions[index].data.toString())
            tabLayout.selectTab(tabLayout.getTabAt(getIndexByDisplayName(macroActions[index].type, context)))
            macroType = MacroType.valueOf(macroActions[index].type)
        }else{
            layout.addView(fastKeyDownUpCheckBox)
            layout.addView(fastHotKeyCheckBox)
        }

        builder.setPositiveButton(context.getString(R.string.confirm_button)) { _, _ ->
            val type = macroType.toString()
            val data = macroData.text.toString().toIntOrNull()?:0
            if (type.isNotBlank()){
                Log.d("MacroEditor", "添加宏操作前: $macroActions")
                if(index == -1){
                    if (fastKeyDownAndUp && fastKeyDownUpCheckBox.visibility == View.VISIBLE){
                        macroActions.add(MacroAction(MacroType.KEY_DOWN.toString(), data))
                        macroActions.add(MacroAction(MacroType.KEY_UP.toString(), data))
                    }else if(fastHotKey && fastHotKeyCheckBox.visibility == View.VISIBLE){
                        macroActions.add(MacroAction(MacroType.KEY_DOWN.toString(), data))
                        macroActions.add(MacroAction(MacroType.KEY_UP.toString(), data))
                        hotKeyMacro()
                    }
                    else{
                        macroActions.add(MacroAction(type, data))
                    }
                }else{
                    macroActions[index].type = type
                    macroActions[index].data = data
                }
                Log.d("MacroEditor", "添加宏操作: Type: $type, Data: $data")
                Log.d("MacroEditor", "添加宏操作: $macroActions")
                showMacroEditor()
            }

        }
        builder.setNegativeButton(context.getString(R.string.cancel_button)) { _, _ ->
            showMacroEditor()
        }
        builder.setNeutralButton(context.getString(R.string.virtual_keyboard_menu_copy_button)) { _, _ ->
            val type = macroType.toString()
            val data = macroData.text.toString().toIntOrNull() ?: 0
            macroActions.add(MacroAction(type, data))
            showMacroEditor()
        }
        builder.setCancelable(false)
        val dialog = builder.create()
        dialog.show()
        if (index == -1) {
            val button = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            button.isEnabled = false
        }
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
        val inputContext = virtualKeyboard.keyboardInputContext

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
            MacroType.KEY_TOGGLE.toString() -> {
                val element = virtualKeyboard.getElementByElementId(action.data)
                if (element != null) {
                    element.setHide(!element.isHide)
                    element.invalidate()
                }
                executeNextActionWithDelay(virtualKeyboard, index, 0) // KEY_TOGGLE 后立即执行下一个
            }
            MacroType.KEY_TOGGLE_GROUP.toString() -> {
                val elements = virtualKeyboard.elements
                for (element in elements) {
                    if (element.group == action.data) {
                        element.setHide(!element.isHide)
                        element.invalidate()
                    }
                }
                executeNextActionWithDelay(virtualKeyboard, index, 0) // KEY_TOGGLE 后立即执行下一个
            }
            MacroType.TOUCH_TOGGLE.toString() -> {
                val game = virtualKeyboard.gameContext
                val mode = action.data
                game.changeTouchMode(mode)
                executeNextActionWithDelay(virtualKeyboard, index, 0) // KEY_TOGGLE 后立即执行下一个
            }
            MacroType.FLOATING_KEYBOARD_TOGGLE.toString() -> {
                val game = virtualKeyboard.gameContext
                game.toggleFloatingKeyboard()
                executeNextActionWithDelay(virtualKeyboard, index, 0)
            }
            MacroType.TOUCHPAD_TOGGLE.toString() -> {
                val game = virtualKeyboard.gameContext
                game.setTouchpadBlock(action.data)
                executeNextActionWithDelay(virtualKeyboard, index, 0)
            }
            MacroType.PORTAL_TOGGLE.toString() -> {
                val game = virtualKeyboard.gameContext
                when (action.data) {
                    1 -> game.setPortalsEnabled(true)
                    2 -> game.setPortalsEnabled(false)
                    else -> game.togglePortalsEnabled()
                }
                executeNextActionWithDelay(virtualKeyboard, index, 0)
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
