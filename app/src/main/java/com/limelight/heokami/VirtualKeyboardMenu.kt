package com.limelight.heokami

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Toast
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader

class VirtualKeyboardMenu(private val context: Context, private val virtualKeyboard: VirtualKeyboard) {

//    init {
//        showMenu()
//    }

    fun createListView(dialog: AlertDialog): ListView {
        val listView = ListView(context)
        val actionMap = createActionMap() // 假设 createActionMap() 返回一个 Map<String, Runnable>
        val items = actionMap.keys.toList().toTypedArray()
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter

        // 设置点击事件监听器
        listView.setOnItemClickListener { parent, view, position, id ->
            val item = adapter.getItem(position) as String
            actionMap[item]?.invoke() // 执行对应的 Runnable
            dialog.dismiss()
            Toast.makeText(context, "点击了 $item", Toast.LENGTH_SHORT).show()
        }
        return listView
    }

    private fun createAddButtonDialog() {
        val scrollView = ScrollView(context)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        scrollView.layoutParams = scrollParams

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        scrollView.addView(layout)

        val buttonIdEditText = EditText(context)
        buttonIdEditText.hint = "按钮ID(数字)"
        layout.addView(buttonIdEditText)

        val buttonTextEditText = EditText(context)
        buttonTextEditText.hint = "显示文本"
        layout.addView(buttonTextEditText)

        val vkCodeEditText = EditText(context)
        vkCodeEditText.hint = "VK按钮编码(数字)"
        layout.addView(vkCodeEditText)

        val builder = AlertDialog.Builder(context)
        val dialog = builder.setTitle("添加按钮")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setNeutralButton("确认", { dialogInterface, which ->
                addElement(
                    buttonIdEditText.text.toString().toInt(),
                    buttonTextEditText.text.toString(),
                    vkCodeEditText.text.toString()
                )
            })
            .setCancelable(true)
            .create()

        dialog.show()
    }

    private fun addElement(buttonId: Int, text: String, vkCode: String) {
        val configurationLoader = VirtualKeyboardConfigurationLoader()

        configurationLoader.addButton(virtualKeyboard, context, buttonId, vkCode, text)
//        Toast.makeText(context, "添加按钮A，" + VirtualKeyboardVkCode.VKCode.VK_BACK.code.toString() , Toast.LENGTH_SHORT).show()

    }

    private fun createActionMap(): Map<String, () -> Unit> {
        val actionMap = mutableMapOf<String, () -> Unit>()
        actionMap[context.getString(R.string.game_menu_enable_keyboard)] = {
//            Toast.makeText(context, "启用虚拟键盘", Toast.LENGTH_SHORT).show()
            addElement(888,
                "A",
                VirtualKeyboardVkCode.VKCode.VK_A.code.toString(),
                )
        }
        actionMap["添加按钮"] = {
//            Toast.makeText(context, "启用虚拟键盘", Toast.LENGTH_SHORT).show()
            createAddButtonDialog()
        }
        return actionMap
    }

    fun showMenu(){
        val builder = AlertDialog.Builder(context)
        builder.setTitle("菜单")
            .setCancelable(true)
        val dialog = builder.create()
        dialog.setView(createListView(dialog));
        dialog.show()
    }
}