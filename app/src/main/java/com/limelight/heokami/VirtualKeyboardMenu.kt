package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.Log
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
    private var elementID = -1

    private fun createListView(dialog: AlertDialog): ListView {
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
//            Toast.makeText(context, "点击了 $item", Toast.LENGTH_SHORT).show()
        }
        return listView
    }

    public fun setElementID(elementID: Int) {
        this.elementID = elementID
    }

    @SuppressLint("SetTextI18n")
    public fun setButtonDialog() {
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
        buttonIdEditText.hint = context.getString(R.string.virtual_keyboard_menu_button_id_hint) // "按钮ID(数字)"
        buttonIdEditText.setInputType(InputType.TYPE_CLASS_NUMBER)
        layout.addView(buttonIdEditText)

        val buttonTextEditText = EditText(context)
        buttonTextEditText.hint = context.getString(R.string.virtual_keyboard_menu_button_text_hint) // "按钮文本"
        layout.addView(buttonTextEditText)

        val vkCodeEditText = EditText(context)
        vkCodeEditText.hint = context.getString(R.string.virtual_keyboard_menu_vk_code_hint) // "VK按钮编码(数字)"
        vkCodeEditText.setInputType(InputType.TYPE_CLASS_NUMBER)
        layout.addView(vkCodeEditText)

        val builder = AlertDialog.Builder(context)
        var dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_add_button_title))
            .setView(scrollView)
            .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
            .setNeutralButton(R.string.virtual_keyboard_menu_confirm_button, { dialogInterface, which ->
                VirtualKeyboardConfigurationLoader.addButton(
                    virtualKeyboard,
                    context,
                    buttonIdEditText.text.toString().toInt(),
                    vkCodeEditText.text.toString(),
                    buttonTextEditText.text.toString()
                )
            })
            .setCancelable(true)
            .create()

        if (elementID != -1) {
            buttonIdEditText.setText(elementID.toString())
            buttonTextEditText.setText(virtualKeyboard.getElementByElementId(elementID).text)
            vkCodeEditText.setText(virtualKeyboard.getElementByElementId(elementID).vk_code)
            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_button_title))
                .setView(scrollView)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setPositiveButton(R.string.virtual_keyboard_menu_remove_button, { dialogInterface, which ->
                    virtualKeyboard.removeElementByElementId(elementID)
                })
                .setNeutralButton(R.string.virtual_keyboard_menu_save_button, { dialogInterface, which ->
                    var element = virtualKeyboard.getElementByElementId(elementID)
                    element.text = buttonTextEditText.text.toString()
                    element.vk_code = vkCodeEditText.text.toString()
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                    element.invalidate()
                })
                .setCancelable(true)
                .create()
        }

        dialog.show()
    }

    private fun createActionMap(): Map<String, () -> Unit> {
        val actionMap = mutableMapOf<String, () -> Unit>()
        actionMap[context.getString(R.string.virtual_keyboard_menu_add_button_eg)] = {
//            Toast.makeText(context, "启用虚拟键盘", Toast.LENGTH_SHORT).show()
            VirtualKeyboardConfigurationLoader.addButton(
                virtualKeyboard,
                context,
                888,
                VirtualKeyboardVkCode.VKCode.VK_A.code.toString(),
                "A",
                )
        }
        // 添加按钮
        actionMap[context.getString(R.string.virtual_keyboard_menu_add_button_title)] = {
            setButtonDialog()
        }
        actionMap[context.getString(R.string.virtual_keyboard_menu_save_profile)] = {
            VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
        }
        actionMap[context.getString(R.string.virtual_keyboard_menu_load_profile)] = {
            VirtualKeyboardConfigurationLoader.loadFromPreferences(virtualKeyboard, context)
        }
        actionMap[context.getString(R.string.virtual_keyboard_menu_delete_profile)] = {
            VirtualKeyboardConfigurationLoader.deleteProfile(context)
            virtualKeyboard.refreshLayout()
        }
        return actionMap
    }

    fun showMenu(){
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_button_title))
            .setCancelable(true)
        val dialog = builder.create()
        dialog.setView(createListView(dialog));
        dialog.show()
    }
}