package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.limelight.Game
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboard
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardElement
import com.limelight.preferences.PreferenceConfiguration
import org.json.JSONObject
import java.lang.Long.parseLong


class VirtualKeyboardMenu(private val context: Context, private val virtualKeyboard: VirtualKeyboard) {

//    init {
//        showMenu()
//    }
    private var element: VirtualKeyboardElement? = null
    private var game: Game? = null

    fun setGameView(game: Game) {
        this.game = game
    }

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

    private fun createSpinner(context: Context): Spinner {
        val spinner = Spinner(context)
        val items = VirtualKeyboardElement.ButtonType.entries.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
        spinner.adapter = adapter
        return spinner
    }

    fun setElement(element: VirtualKeyboardElement) {
        this.element = element
    }

    @SuppressLint("SetTextI18n")
    private fun showGridLinesDialog() {
        val gridLines = game?.gameGridLines
        val scrollView = ScrollView(context)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        scrollView.layoutParams = scrollParams

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val enableGridLayout = pref.getBoolean(PreferenceConfiguration.ENABLE_GRID_LAYOUT_PREF_STRING, PreferenceConfiguration.DEFAULT_ENABLE_GRID_LAYOUT)
        val checkBot = CheckBox(context).apply {
            text = context.getString(R.string.grid_lines_enable)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isChecked = enableGridLayout
            setOnClickListener {
                if (pref != null) {
                    if (!enableGridLayout){
                        gridLines?.show()
                        pref.edit().putBoolean(PreferenceConfiguration.ENABLE_GRID_LAYOUT_PREF_STRING, true).apply()
                        game?.prefConfig?.enableGridLayout = true
                    }else{
                        gridLines?.hide()
                        pref.edit().putBoolean(PreferenceConfiguration.ENABLE_GRID_LAYOUT_PREF_STRING, false).apply()
                        game?.prefConfig?.enableGridLayout = false
                    }

                }
            }
        }
        layout.addView(checkBot)

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val xTextView = TextView(context).apply {
            text = context.getString(R.string.grid_lines_x_axis_count)
        }

        val xEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.getColumnCount()?.toString())
        }

        val yTextView = TextView(context).apply {
            text = context.getString(R.string.grid_lines_y_axis_count)
        }

        val yEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.getRowCount()?.toString())
        }

        linearLayout.addView(xTextView)
        linearLayout.addView(xEditText)
        linearLayout.addView(yTextView)
        linearLayout.addView(yEditText)
        layout.addView(linearLayout)

        val linearLayout2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        linearLayout2.addView(TextView(context).apply {
            text = "A"
        })
        val opacityEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.opacity?.toString())
        }
        linearLayout2.addView(opacityEditText)


        linearLayout2.addView(TextView(context).apply {
            text = "R"
        })
        val redEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.red?.toString())
        }
        linearLayout2.addView(redEditText)

        linearLayout2.addView(TextView(context).apply {
            text = "G"
        })
        val greenEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.green?.toString())
        }
        linearLayout2.addView(greenEditText)

        linearLayout2.addView(TextView(context).apply {
            text = "B"
        })
        val blueEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            setText(gridLines?.blue?.toString())
        }
        linearLayout2.addView(blueEditText)

        layout.addView(linearLayout2)

        val snapThresholdTextView = TextView(context).apply {
            text = context.getString(R.string.grid_lines_snap_threshold)
        }
        val snapThresholdEditText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(gridLines?.getSnapThreshold()?.toString())
        }

        layout.addView(snapThresholdTextView)
        layout.addView(snapThresholdEditText)

        scrollView.addView(layout)

        val builder = AlertDialog.Builder(context)
        val dialog = builder.setTitle(context.getString(R.string.menu_title_grid_lines))
            .setView(scrollView)
            .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
            .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button, { dialogInterface, which ->
                gridLines?.setGridSize(xEditText.text.toString().toInt(), yEditText.text.toString().toInt())
                gridLines?.setSnapThreshold(snapThresholdEditText.text.toString().toInt())
                gridLines?.setGridOpacity(opacityEditText.text.toString().toInt())
                gridLines?.setGridRGB(redEditText.text.toString().toInt(), greenEditText.text.toString().toInt(), blueEditText.text.toString().toInt())
                pref.edit().putString("gridLinesConfig", gridLines?.getConfig().toString()).apply()
            })
            .setCancelable(true)
            .create()
        dialog.show()
    }

    fun toHexString(value: Int): String {
        return String.format("%08X", value)
    }

    fun getHexValue(hexString: String): Long {
        // 处理 # 号前缀，如果存在
        val hex = if (hexString.startsWith("#")) {
            hexString.substring(1)
        } else {
            hexString
        }
        return parseLong(hex, 16)
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
            text = context.getString(R.string.virtual_keyboard_menu_vk_code_button)
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

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_normal_color_hint)
        })

        val normaColorEditText = EditText(context)
        layout.addView(normaColorEditText)

        layout.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_pressed_color_hint)
        })

        val pressedColorEditText = EditText(context)
        layout.addView(pressedColorEditText)

        val opacityTextView = TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_opacity_hint)

        }
        layout.addView(opacityTextView)

        val opacitySeekBar = SeekBar(context).apply {
            max = 100
            progress = 100
        }
        layout.addView(opacitySeekBar)
        // 滑块监听
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 处理进度变化
                opacityTextView.text = context.getString(R.string.virtual_keyboard_menu_opacity_hint) + " $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val radiusTextView = TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_radius_hint)
        }
        layout.addView(radiusTextView)
        val radiusSeekBar = SeekBar(context).apply {
            max = 255
            progress = 10
        }
        layout.addView(radiusSeekBar)
        // 滑块监听
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 处理进度变化
                radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        var selectedButtonType = VirtualKeyboardElement.ButtonType.Button
        val linearLayout3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        linearLayout3.addView(TextView(context).apply {
            text = "按钮类型"
        })

        val buttonTypeSpinner = createSpinner(context)
        Log.d("buttonType", "前elementButtonType: "+ element?.buttonType + " selectedButtonType: "+ selectedButtonType)
        buttonTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position) as String
                val selectedEnum = VirtualKeyboardElement.ButtonType.valueOf(selectedItem)

                // Do something with the selected enum value
                when (selectedEnum) {
                    VirtualKeyboardElement.ButtonType.Button -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.Button
                        Log.d("buttonType", "点击了 Button")
                    }
                    VirtualKeyboardElement.ButtonType.Manage -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.Manage
                        Log.d("buttonType", "点击了 Manage")
                    }
                    VirtualKeyboardElement.ButtonType.HotKeys -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.HotKeys
                        Log.d("buttonType", "点击了 HotKeys")
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        linearLayout3.addView(buttonTypeSpinner)
        layout.addView(linearLayout3)


        if (element != null) {
            selectedButtonType = element?.buttonType!!
            buttonTypeSpinner.setSelection(selectedButtonType.ordinal)
            layout.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_copy_button)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.addButton2(
                        virtualKeyboard,
                        context,
                        virtualKeyboard.lastElementId + 1,
                        vkCodeEditText.text.toString(),
                        buttonTextEditText.text.toString(),
                        element?.buttonType,
                        element?.buttonData,
                        element?.leftMargin,
                        element?.topMargin,
                        element?.width,
                        element?.height
                    )
                }
                Log.d("vk", "复制按钮"+ element?.leftMargin+","+element?.topMargin+","+element?.width+","+element?.height)
            })

            layout.addView(Button(context).apply {
                text = "宏编辑"
                setOnClickListener {
                    val macroEditor = MacroEditor(context, element!!.buttonData, object : OnMacroDataChangedListener {
                        override fun onMacroDataChanged(newData: JSONObject) {
                            element!!.buttonData = newData
                            element!!.invalidate()
                            VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                            Log.d("MacroEditor", "onMacroDataChanged: $newData")
                        }
                    })
                    macroEditor.showMacroEditor()
                }
            })
        }


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
                    buttonTextEditText.text.toString(),
                    VirtualKeyboardElement.ButtonType.Button,
                    JSONObject("{}")
                )
            })
            .setCancelable(true)
            .create()

        if (element != null) {
            buttonIdEditText.setText(element?.elementId.toString())
            buttonTextEditText.setText(element?.text)
            vkCodeEditText.setText(element?.vk_code)
            normaColorEditText.setText(element?.normalColor?.let { toHexString(it) })
            pressedColorEditText.setText(element?.pressedColor?.let { toHexString(it) })
            opacitySeekBar.progress = element?.opacity!!.toInt()
            radiusSeekBar.progress = element?.radius!!.toInt()

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
                    element?.normalColor = getHexValue(normaColorEditText.text.toString()).toInt()
                    element?.pressedColor = getHexValue(pressedColorEditText.text.toString()).toInt()
                    element?.opacity = opacitySeekBar.progress
                    element?.setOpacity(opacitySeekBar.progress)
                    element?.radius = radiusSeekBar.progress.toFloat()
                    element?.buttonType = selectedButtonType
                    element?.invalidate()
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                    virtualKeyboard.refreshLayout()
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
                VirtualKeyboardElement.ButtonType.Button,
                JSONObject("{}")
                )
        }
        // 添加按钮
        actionMap[context.getString(R.string.virtual_keyboard_menu_add_button_title)] = {
            setButtonDialog()
        }
        actionMap[context.getString(R.string.menu_title_grid_lines)] = {
            showGridLinesDialog()
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

    @SuppressLint("SetTextI18n")
    companion object {
        fun showVKCodeDialog(context: Context, buttonTextEditText: EditText?, vkCodeEditText: EditText?) {
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
                        if (buttonTextEditText?.text.toString() == ""){
                            buttonTextEditText?.setText(VirtualKeyboardVkCode.replaceVkName(vkCode.name))
                        }
                        vkCodeEditText?.setText(vkCode.code.toString())
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
    }
}