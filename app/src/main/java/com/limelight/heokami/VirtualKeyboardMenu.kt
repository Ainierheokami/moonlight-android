package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardElement

class VirtualKeyboardMenu(private val context: Context, private val virtualKeyboard: VirtualKeyboard) {

//    init {
//        showMenu()
//    }
    private var element: VirtualKeyboardElement? = null

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

    fun setElement(element: VirtualKeyboardElement) {
        this.element = element
    }

    @SuppressLint("SetTextI18n")
    private fun showVKCodeDialog(context: Context, buttonTextEditText: EditText, vkCodeEditText: EditText) {

        val scrollView = ScrollView(context)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        scrollView.layoutParams = scrollParams

        val gridLayout = GridLayout(context).apply {
            rowCount = (VirtualKeyboardVkCode.VKCode.entries.size + 3) / 4 // 计算行数，向上取整
            columnCount = 4 // 每行 4 列
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val builder = AlertDialog.Builder(context)
        val dialog = builder.setTitle("VK_CODE")
            .setView(scrollView)
            .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
            .setCancelable(true)
            .create()

        dialog.show()

        VirtualKeyboardVkCode.VKCode.entries.forEach { vkCode ->
            val button = Button(context).apply {
                text = VirtualKeyboardVkCode.replaceVkName(vkCode.name)
                setOnClickListener {
                    if (buttonTextEditText.text.toString() == ""){
                        buttonTextEditText.setText(VirtualKeyboardVkCode.replaceVkName(vkCode.name))
                    }
                    vkCodeEditText.setText(vkCode.code.toString())
                    dialog.dismiss()
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) // 每个按钮占1列，权重1
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                }
            }
            gridLayout.addView(button)
        }
        scrollView.addView(gridLayout)
    }

    @SuppressLint("SetTextI18n")
    fun setButtonDialog() {
        val scrollView = ScrollView(context)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        scrollView.layoutParams = scrollParams

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        scrollView.addView(layout)

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_button_id_hint) // "按钮ID(数字)"
        })

        val buttonIdEditText = EditText(context)
        buttonIdEditText.setInputType(InputType.TYPE_CLASS_NUMBER)
        layout.addView(buttonIdEditText)
        buttonIdEditText.setText((virtualKeyboard.lastElementId + 1).toString())

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_button_text_hint) // "按钮文本"
        })

        val buttonTextEditText = EditText(context)
        layout.addView(buttonTextEditText)

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_vk_code_hint) // "VK按钮编码(数字)"
        })

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val vkCodeEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
        }

        val button = Button(context).apply {
            text = "VK表"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                showVKCodeDialog(context, buttonTextEditText, vkCodeEditText)
            }
        }

        // 将EditText和Button添加到水平排列的LinearLayout中
        linearLayout.addView(vkCodeEditText)
        linearLayout.addView(button)

        // 将水平排列的LinearLayout添加到主布局中
        layout.addView(linearLayout)


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

        if (element != null) {
            buttonIdEditText.setText(element?.elementId.toString())
            buttonTextEditText.setText(element?.text)
            vkCodeEditText.setText(element?.vk_code)
            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_button_title))
                .setView(scrollView)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setPositiveButton(R.string.virtual_keyboard_menu_remove_button, { dialogInterface, which ->
                    virtualKeyboard.removeElementByElement(element)
                })
                .setNeutralButton(R.string.virtual_keyboard_menu_save_button, { dialogInterface, which ->
                    element?.text = buttonTextEditText.text.toString()
                    element?.vk_code = vkCodeEditText.text.toString()
                    element?.elementId = buttonIdEditText.text.toString().toInt()
                    element?.invalidate()
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
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
            val intent = Intent(context, SaveFileActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
//            Toast.makeText(context, "保存配置文件", Toast.LENGTH_SHORT).show()
        }
        actionMap[context.getString(R.string.virtual_keyboard_menu_load_profile)] = {
            val intent = Intent(context, LoadFileActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
//            Toast.makeText(context, "加载配置文件", Toast.LENGTH_SHORT).show()
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
            .setNeutralButton(context.getString(R.string.virtual_keyboard_menu_quash_history), { dialogInterface, which ->
                virtualKeyboard.quashHistory()
            })
            .setPositiveButton(context.getString(R.string.virtual_keyboard_menu_forward_history), { dialogInterface, which ->
                virtualKeyboard.forwardHistory()
            })
        val dialog = builder.create()
        dialog.setView(createListView(dialog));
        dialog.show()
        if (virtualKeyboard.historyIndex == virtualKeyboard.historyElements.size - 1) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
        if (virtualKeyboard.historyIndex == 0) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
    }
}