package com.limelight.heokami

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
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
import com.limelight.heokami.activity.LoadFileActivity
import com.limelight.heokami.activity.LoadFileActivityAdd
import com.limelight.heokami.activity.SaveFileActivity
import com.limelight.preferences.PreferenceConfiguration
import org.json.JSONException
import org.json.JSONObject
import java.lang.Long.parseLong
import android.widget.FrameLayout
import android.view.WindowManager


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
        val items = VirtualKeyboardElement.ButtonType.entries.map { it.getDisplayName(context) }
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

        // val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val pref = context.getSharedPreferences("moonlight_prefs", Context.MODE_PRIVATE)
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

        val buttonTextEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
        }
//        layout.addView(buttonTextEditText)
        layout.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(buttonTextEditText)
            addView(Button(context).apply {
                text = "X"
                setOnClickListener {
                    buttonTextEditText.setText("")
                }
            })
        })

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
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
        }

        val vkCodeButton = Button(context).apply {
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
        linearLayout.addView(vkCodeButton)

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
        Log.d("opacitySeekBar", "before: "+ opacitySeekBar.progress)
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
            text = context.getString(R.string.virtual_keyboard_menu_button_type_hint)
        })

        val buttonTypeSpinner = createSpinner(context)
        Log.d("buttonType", "前elementButtonType: "+ element?.buttonType + " selectedButtonType: "+ selectedButtonType)
        buttonTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
//                val selectedItem = parent.getItemAtPosition(position)
                val selectedEnum = VirtualKeyboardElement.ButtonType.entries[position]

                // Do something with the selected enum value
                when (selectedEnum) {
                    VirtualKeyboardElement.ButtonType.Button -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.Button
                        vkCodeButton.text = "vkCode"
                        vkCodeButton.setOnClickListener {
                            showVKCodeDialog(context, buttonTextEditText, vkCodeEditText)
                        }
                        Log.d("buttonType", "点击了 Button")
                    }
                    VirtualKeyboardElement.ButtonType.HotKeys -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.HotKeys
                        Log.d("buttonType", "点击了 HotKeys")
                    }
                    VirtualKeyboardElement.ButtonType.JoyStick -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.JoyStick
                        vkCodeButton.text = "JoyStick"
                        vkCodeButton.setOnClickListener {
                            showJoyStickVKCodeDialog(context, buttonTextEditText, vkCodeEditText)
                        }
                        Log.d("buttonType", "点击了 JoyStick")
                    }
                    VirtualKeyboardElement.ButtonType.TouchPad -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.TouchPad
                        Log.d("buttonType", "点击了 TouchPad")
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        linearLayout3.addView(buttonTypeSpinner)

        // 编组
        linearLayout3.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_grouping)
        })
        val groupEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 权重为1，表示EditText占据可用空间的一部分
            )
            hint = context.getString(R.string.virtual_keyboard_menu_group_hint_id)
        }
        linearLayout3.addView(groupEditText)
        layout.addView(linearLayout3)



        if (element != null) {
            val setJoyStickButton = Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_set_the_handle_arrow_keys)
                setOnClickListener {
                    setJoyStickVKCodeDialog(context, element!!)
                }
                visibility = View.GONE
            }
            layout.addView(setJoyStickButton)
            vkCodeEditText.addTextChangedListener(object : TextWatcher {
                val joy_s = VirtualKeyboardVkCode.JoyCode.JOY_PAD.code
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                    if (s.toString() == joy_s){
                        setJoyStickButton.visibility = View.VISIBLE
                    }else{
                        setJoyStickButton.visibility = View.GONE
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.toString() == joy_s){
                        setJoyStickButton.visibility = View.VISIBLE
                    }else{
                        setJoyStickButton.visibility = View.GONE
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (s.toString() == joy_s){
                        setJoyStickButton.visibility = View.VISIBLE
                    }else{
                        setJoyStickButton.visibility = View.GONE
                    }
                }

            })


            selectedButtonType = element?.buttonType!!
            buttonTypeSpinner.setSelection(selectedButtonType.ordinal)
            layout.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_copy_button)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.copyButton(virtualKeyboard, element, context)
                    game?.postNotification(context.getString(R.string.virtual_keyboard_menu_copy_button) + "\n" + element?.elementId, 2000)
                }
                Log.d("vk", "复制按钮"+ element?.leftMargin+","+element?.topMargin+","+element?.width+","+element?.height)
            })

            layout.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_macro_edit_button)
                setOnClickListener {
                    val macroEditor = MacroEditor(context, element!!.buttonData, object : OnMacroDataChangedListener {
                        override fun onMacroDataChanged(newData: JSONObject) {
                            element!!.buttonData = newData
                            element!!.invalidate()
                            VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                            Log.d("MacroEditor", "onMacroDataChanged: $newData")
                        }
                    })
                    macroEditor.setElements(virtualKeyboard.elements)
                    macroEditor.showMacroEditor()
                }
            })
        }


        val builder = AlertDialog.Builder(context)
        var dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_add_button_title))
            .setView(scrollView)
            .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
            .setNeutralButton(R.string.virtual_keyboard_menu_confirm_button, null)
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
                .setCancelable(false)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setPositiveButton(R.string.virtual_keyboard_menu_remove_button, { dialogInterface, which ->
                    virtualKeyboard.removeElementByElement(element)
                })
                .setNeutralButton(R.string.virtual_keyboard_menu_save_button, null)
                .setCancelable(true)
                .create()


            if (element?.group != -1) {
                groupEditText.setText(element?.group.toString())
                // 添加删除组按钮
                layout.addView(Button(context).apply {
                    text = context.getString(R.string.virtual_keyboard_menu_delete_group_button)
                    setOnClickListener {
                        val elements = virtualKeyboard.elements.filter { it.group == element?.group }
                        elements.forEach {
                            virtualKeyboard.removeElementByElement(it)
                        }
                        dialog.dismiss()
                    }
                })
            }
        }
        dialog.show()

        // 确定/保存
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (element == null){
                val defaultButtonId = virtualKeyboard.lastElementId + 1 // 默认按钮 ID
                val defaultVkCode = "0"   // 默认 VK 代码
                val defaultButtonText = "New Button" // 默认按钮文本

                val buttonId = buttonIdEditText.text.toString().toIntOrNull() ?: defaultButtonId
                val vkCode = vkCodeEditText.text.toString().ifEmpty { defaultVkCode }
                val buttonText = buttonTextEditText.text.toString().ifEmpty { defaultButtonText }

                VirtualKeyboardConfigurationLoader.addButton(
                    virtualKeyboard,
                    context,
                    buttonId,
                    vkCode,
                    buttonText,
                    selectedButtonType,
                    JSONObject("{}")
                )

            }else{
                element?.text = buttonTextEditText.text.toString()
                element?.vk_code = vkCodeEditText.text.toString()
                element?.elementId = buttonIdEditText.text.toString().toInt()
                element?.normalColor = getHexValue(normaColorEditText.text.toString()).toInt()
                element?.pressedColor = getHexValue(pressedColorEditText.text.toString()).toInt()
                element?.opacity = opacitySeekBar.progress
                element?.setOpacity(opacitySeekBar.progress)
                element?.radius = radiusSeekBar.progress.toFloat()
                element?.buttonType = selectedButtonType
                if (groupEditText.text != null && groupEditText.text.toString() != ""){
                    element?.group = groupEditText.text.toString().toInt()
                }
                element?.invalidate()
                VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                virtualKeyboard.refreshLayout()
            }
            dialog.dismiss()
        }
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
        val titleGroupMove = context.getString(R.string.title_enable_group_move) + "(" + virtualKeyboard.groupMove + ")"
        actionMap[titleGroupMove] = {
            virtualKeyboard.groupMove = !virtualKeyboard.groupMove
            game?.postNotification(context.getString(R.string.title_enable_group_move) + ":"+ virtualKeyboard.groupMove, 2000)
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
        actionMap[context.getString(R.string.virtual_keyboard_menu_load_profile_add)] = {
            val intent = Intent(context, LoadFileActivityAdd::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
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
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
        }
    }

    @SuppressLint("SetTextI18n")
    companion object {
        fun showVKCodeDialog(context: Context, buttonTextEditText: EditText?, vkCodeEditText: EditText?) {
            val scrollView = ScrollView(context)
            val scrollParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            scrollView.layoutParams = scrollParams
            scrollView.isFillViewport = true

            val builder = AlertDialog.Builder(context)
            val dialog = builder.setTitle("VK_CODE")
                .setView(scrollView)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setCancelable(true)
                .create()

            // 弹窗全屏显示，宽高都接近满屏
            dialog.setOnShowListener {
                val window = dialog.window
                val dm = context.resources.displayMetrics
                window?.setLayout((dm.widthPixels * 0.98f).toInt(),
                    (dm.heightPixels * 0.9f).toInt())
                // 占满可见区域
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }

            // 外层加一层 HorizontalScrollView 让键盘可以左右滑动
            val horizontalScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isFillViewport = true
            }

            // 键盘整体竖直布局（充满弹窗宽度，便于两端对齐与等比拉伸）
            val keyboardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isBaselineAligned = false
            }

            // QWERTY键盘布局及权重
            val qwertyRows = listOf(
                // 第一行：ESC F1~F12
                listOf(
                    Triple("VK_ESCAPE", 1f, null),
                    Triple("VK_F1", 1f, null), Triple("VK_F2", 1f, null), Triple("VK_F3", 1f, null), Triple("VK_F4", 1f, null),
                    Triple("VK_F5", 1f, null), Triple("VK_F6", 1f, null), Triple("VK_F7", 1f, null), Triple("VK_F8", 1f, null),
                    Triple("VK_F9", 1f, null), Triple("VK_F10", 1f, null), Triple("VK_F11", 1f, null), Triple("VK_F12", 1f, null)
                ),
                // 第二行：` 1~0 - = Backspace
                listOf(
                    Triple("VK_OEM_3", 1f, null),
                    Triple("VK_1", 1f, null), Triple("VK_2", 1f, null), Triple("VK_3", 1f, null), Triple("VK_4", 1f, null),
                    Triple("VK_5", 1f, null), Triple("VK_6", 1f, null), Triple("VK_7", 1f, null), Triple("VK_8", 1f, null),
                    Triple("VK_9", 1f, null), Triple("VK_0", 1f, null), Triple("VK_OEM_MINUS", 1f, null), Triple("VK_OEM_PLUS", 1f, null),
                    Triple("VK_BACK", 2f, null)
                ),
                // 第三行：Tab Q~] \
                listOf(
                    Triple("VK_TAB", 1.5f, null),
                    Triple("VK_Q", 1f, null), Triple("VK_W", 1f, null), Triple("VK_E", 1f, null), Triple("VK_R", 1f, null),
                    Triple("VK_T", 1f, null), Triple("VK_Y", 1f, null), Triple("VK_U", 1f, null), Triple("VK_I", 1f, null),
                    Triple("VK_O", 1f, null), Triple("VK_P", 1f, null), Triple("VK_OEM_4", 1f, null), Triple("VK_OEM_6", 1f, null),
                    Triple("VK_OEM_5", 1.5f, null)
                ),
                // 第四行：Caps A~' Enter
                listOf(
                    Triple("VK_CAPITAL", 1.8f, null),
                    Triple("VK_A", 1f, null), Triple("VK_S", 1f, null), Triple("VK_D", 1f, null), Triple("VK_F", 1f, null),
                    Triple("VK_G", 1f, null), Triple("VK_H", 1f, null), Triple("VK_J", 1f, null), Triple("VK_K", 1f, null),
                    Triple("VK_L", 1f, null), Triple("VK_OEM_1", 1f, null), Triple("VK_OEM_7", 1f, null),
                    Triple("VK_RETURN", 2.2f, null)
                ),
                // 第五行：Shift Z~? Shift
                listOf(
                    Triple("VK_LSHIFT", 2.2f, null),
                    Triple("VK_Z", 1f, null), Triple("VK_X", 1f, null), Triple("VK_C", 1f, null), Triple("VK_V", 1f, null),
                    Triple("VK_B", 1f, null), Triple("VK_N", 1f, null), Triple("VK_M", 1f, null), Triple("VK_OEM_COMMA", 1f, null),
                    Triple("VK_OEM_PERIOD", 1f, null), Triple("VK_OEM_2", 1f, null),
                    Triple("VK_RSHIFT", 2.2f, null)
                ),
                // 第六行：Ctrl Win Alt Space Alt Win Menu Ctrl 方向键
                listOf(
                    Triple("VK_LCONTROL", 1.5f, null), Triple("VK_LWIN", 1.2f, null), Triple("VK_LMENU", 1.2f, null),
                    Triple("VK_SPACE", 7f, null),
                    Triple("VK_RMENU", 1.2f, null), Triple("VK_RWIN", 1.2f, null), Triple("VK_APPS", 1.2f, null), Triple("VK_RCONTROL", 1.5f, null),
                    Triple("VK_LEFT", 1f, null), Triple("VK_UP", 1f, null), Triple("VK_DOWN", 1f, null), Triple("VK_RIGHT", 1f, null)
                )
            )

            val vkMap = VirtualKeyboardVkCode.VKCode.entries.associateBy { it.name }

            // 构建每一行：行宽 match_parent，子项 0dp+weight，计算各行总权重确保左右对齐
            for (row in qwertyRows) {
                val totalWeight = row.sumOf { it.second.toDouble() }.toFloat()
            val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                    )
                    isBaselineAligned = false
                    weightSum = totalWeight
                }
                for ((vkName, weight, _) in row) {
                    val button = Button(context)
                    button.layoutParams = LinearLayout.LayoutParams(
                        0,
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45f, context.resources.displayMetrics).toInt(),
                        weight
                    ).apply {
                        setMargins(2, 2, 2, 2)
                    }
                    button.text = vkMap[vkName]?.getVKName() ?: vkName
                    button.textSize = 11f
                    button.setSingleLine(true)
                    button.ellipsize = TextUtils.TruncateAt.END
                    button.setBackgroundResource(R.drawable.keyboard_key_bg_selector)
                    button.setOnClickListener {
                        if (buttonTextEditText?.text.toString() == "") {
                            buttonTextEditText?.setText(vkMap[vkName]?.getVKName() ?: vkName)
                        }
                        vkCodeEditText?.setText(vkMap[vkName]?.code?.toString() ?: "")
                        dialog.dismiss()
                    }
                    rowLayout.addView(button)
                }
                keyboardLayout.addView(rowLayout)
            }
            // 鼠标键一行
            val mouseKeys = listOf(
                Triple("VK_LBUTTON", 1.5f, null),
                Triple("VK_RBUTTON", 1.5f, null),
                Triple("VK_MBUTTON", 1.5f, null),
                Triple("VK_XBUTTON1", 1.5f, null),
                Triple("VK_XBUTTON2", 1.5f, null)
            )
            val mouseRowTotalWeight = mouseKeys.sumOf { it.second.toDouble() }.toFloat()
            val mouseRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                isBaselineAligned = false
                weightSum = mouseRowTotalWeight
            }
            for ((vkName, weight, _) in mouseKeys) {
                val button = Button(context)
                button.layoutParams = LinearLayout.LayoutParams(
                    0,
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45f, context.resources.displayMetrics).toInt(),
                    weight
                ).apply { setMargins(2, 2, 2, 2) }
                button.text = vkMap[vkName]?.getVKName() ?: vkName
                button.textSize = 11f
                button.setSingleLine(true)
                button.ellipsize = TextUtils.TruncateAt.END
                button.setBackgroundResource(R.drawable.keyboard_key_bg_selector)
                button.setOnClickListener {
                    if (buttonTextEditText?.text.toString() == "") {
                        buttonTextEditText?.setText(vkMap[vkName]?.getVKName() ?: vkName)
                    }
                    vkCodeEditText?.setText(vkMap[vkName]?.code?.toString() ?: "")
                    dialog.dismiss()
                }
                mouseRow.addView(button)
            }
            keyboardLayout.addView(mouseRow)

            horizontalScroll.addView(keyboardLayout)
            scrollView.addView(horizontalScroll)

            dialog.show()
        }

        fun showJoyStickVKCodeDialog(context: Context, buttonTextEditText: EditText?, vkCodeEditText: EditText?) {
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
            val dialog = builder.setTitle("JOY_CODE")
                .setView(scrollView)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setCancelable(true)
                .create()

            dialog.show()

            VirtualKeyboardVkCode.JoyCode.entries.forEach { code ->
                val button = Button(context).apply {
                    text = code.name
                    setOnClickListener {
                        if (buttonTextEditText?.text.toString() == ""){
                            buttonTextEditText?.setText(code.name)
                        }
                        vkCodeEditText?.setText(code.code.toString())
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

        fun setJoyStickVKCodeDialog(context: Context, element: VirtualKeyboardElement) {
            // 手柄方向键
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // 上
            val linearLayout1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            linearLayout1.addView(TextView(context).apply {
                text = "手柄方向(上)"
            })
            val upEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // 权重为1，表示EditText占据可用空间的一部分
                )
            }
            linearLayout1.addView(upEditText)
            linearLayout1.addView(Button(context).apply {
                text = "vkCode"
                setOnClickListener {
                    showVKCodeDialog(context, null, upEditText)
                }
            })

            // 下
            val linearLayout2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            linearLayout2.addView(TextView(context).apply {
                text = "手柄方向(下)"
            })
            val downEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // 权重为1，表示EditText占据可用空间的一部分
                )
            }
            linearLayout2.addView(downEditText)
            linearLayout2.addView(Button(context).apply {
                text = "vkCode"
                setOnClickListener {
                    showVKCodeDialog(context, null, downEditText)
                }
            })

            // 左
            val linearLayout3 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            linearLayout3.addView(TextView(context).apply {
                text = "手柄方向(左)"
            })
            val leftEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // 权重为1，表示EditText占据可用空间的一部分
                )
            }
            linearLayout3.addView(leftEditText)
            linearLayout3.addView(Button(context).apply {
                text = "vkCode"
                setOnClickListener {
                    showVKCodeDialog(context, null, leftEditText)
                }
            })

            // 右
            val linearLayout4 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            linearLayout4.addView(TextView(context).apply {
                text = "手柄方向(左)"
            })
            val rightEditText = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f // 权重为1，表示EditText占据可用空间的一部分
                )
            }
            linearLayout4.addView(rightEditText)
            linearLayout4.addView(Button(context).apply {
                text = "vkCode"
                setOnClickListener {
                    showVKCodeDialog(context, null, rightEditText)
                }
            })

            scrollView.addView(layout)
            layout.addView(linearLayout1)
            layout.addView(linearLayout2)
            layout.addView(linearLayout3)
            layout.addView(linearLayout4)

            if (element.buttonData != null) {
                val jsonData = element.buttonData
                if (jsonData.has("UP_VK_CODE")) {
                    upEditText.setText(jsonData.getString("UP_VK_CODE"))
                }
                if (jsonData.has("DOWN_VK_CODE")) {
                    downEditText.setText(jsonData.getString("DOWN_VK_CODE"))
                }
                if (jsonData.has("LEFT_VK_CODE")) {
                    leftEditText.setText(jsonData.getString("LEFT_VK_CODE"))
                }
                if (jsonData.has("RIGHT_VK_CODE")) {
                    rightEditText.setText(jsonData.getString("RIGHT_VK_CODE"))
                }
            }

            val builder = AlertDialog.Builder(context)
            val dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_the_handle_arrow_keys))
                .setView(scrollView)
                .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button) { _, _ ->
                    try {
                        val jsonData = JSONObject()
                        jsonData.put("UP_VK_CODE", upEditText.text.toString())
                        jsonData.put("DOWN_VK_CODE", downEditText.text.toString())
                        jsonData.put("LEFT_VK_CODE", leftEditText.text.toString())
                        jsonData.put("RIGHT_VK_CODE", rightEditText.text.toString())
                        element.buttonData = jsonData
                    } catch (e: JSONException) {
                        Log.e("heokami", e.toString(), e)
                    }
                }
                .setNeutralButton(R.string.default_button){_,_ ->
                    try {
                        val jsonData = JSONObject("{}")
                        element.buttonData = jsonData
                    } catch (e: JSONException) {
                        Log.e("heokami", e.toString(), e)
                    }
                }
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setCancelable(true)
                .create()

            dialog.show()

        }

        fun showHasButtonDialog(context: Context, elements: List<VirtualKeyboardElement>, elementIDEditText: EditText?, groupEditText: EditText?) {
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
            val dialog = builder.setTitle("BUTTON_LIST")
                .setView(scrollView)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setCancelable(true)
                .create()

            dialog.show()
            val groupList = arrayListOf<Int>()
            elements.forEach { element ->
                if (elementIDEditText != null) {
                    gridLayout.addView(Button(context).apply {
                        text = "ID: ${element.elementId}  NAME: ${element.text}"
                        setOnClickListener {
                            elementIDEditText.setText(element.elementId.toString())
                            dialog.dismiss()
                        }
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2f) // 每个按钮占1列，权重1
                            rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                        }
                    })
                }else if (groupEditText != null && element.group != -1 && !groupList.contains(element.group)){
                    groupList.add(element.group)
                    gridLayout.addView(Button(context).apply {
                        text = "GROUP_ID: ${element.group}"
                        setOnClickListener {
                            groupEditText.setText(element.group.toString())
                            dialog.dismiss()
                        }
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2f) // 每个按钮占1列，权重1
                            rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                        }
                    })
                }
            }
            scrollView.addView(gridLayout)
        }
    }

}