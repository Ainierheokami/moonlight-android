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
import android.widget.Toast
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
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.tabs.TabLayout


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
        val actionMap = createActionMap(dialog) // 传入 dialog 引用
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

        // 统一使用默认 SharedPreferences，确保与 Game.java 读取一致
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        var enableGridLayout = pref.getBoolean(PreferenceConfiguration.ENABLE_GRID_LAYOUT_PREF_STRING, PreferenceConfiguration.DEFAULT_ENABLE_GRID_LAYOUT)
        val checkBot = CheckBox(context).apply {
            text = context.getString(R.string.grid_lines_enable)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isChecked = enableGridLayout
            setOnClickListener {
                // 以当前勾选状态为准，避免使用旧的 enableGridLayout 变量导致需多次切换
                val nowChecked = (it as CheckBox).isChecked
                enableGridLayout = nowChecked
                if (nowChecked) {
                    gridLines?.show()
                } else {
                    gridLines?.hide()
                }
                pref.edit().putBoolean(PreferenceConfiguration.ENABLE_GRID_LAYOUT_PREF_STRING, nowChecked).apply()
                game?.prefConfig?.enableGridLayout = nowChecked
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
        // 统一内边距，改善可读性
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        // 顶部/底部内边距略微收紧，减少首屏留白
        layout.setPadding(dp(16), dp(8), dp(16), dp(6))
        fun addSpacer(heightDp: Int) {
            layout.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(heightDp)
                )
            })
        }
        fun addSpacerTo(parent: LinearLayout, heightDp: Int) {
            parent.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(heightDp)
                )
            })
        }
        fun addSectionTitle(title: String) {
            layout.addView(TextView(context).apply {
                text = title
                setTextColor(Color.parseColor("#666666"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            addSpacer(6)
        }
        fun addCollapsibleSectionTo(parent: LinearLayout, title: String, defaultExpanded: Boolean = true): LinearLayout {
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(context).apply {
                text = title
                setTextColor(Color.parseColor("#666666"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val arrowView = TextView(context).apply {
                text = if (defaultExpanded) "▼" else "▶"
                setTextColor(Color.parseColor("#999999"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(8), 0, dp(8), 0)
            }
            header.addView(titleView)
            header.addView(arrowView)

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (defaultExpanded) View.VISIBLE else View.GONE
            }
            header.setOnClickListener {
                val expanded = content.visibility == View.VISIBLE
                content.visibility = if (expanded) View.GONE else View.VISIBLE
                arrowView.text = if (expanded) "▶" else "▼"
            }
            container.addView(header)
            container.addView(content)
            parent.addView(container)
            return content
        }
        fun addCollapsibleSection(title: String, defaultExpanded: Boolean = true): LinearLayout {
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(context).apply {
                text = title
                setTextColor(Color.parseColor("#666666"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val arrowView = TextView(context).apply {
                text = if (defaultExpanded) "▼" else "▶"
                setTextColor(Color.parseColor("#999999"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(8), 0, dp(8), 0)
            }
            header.addView(titleView)
            header.addView(arrowView)

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (defaultExpanded) View.VISIBLE else View.GONE
            }
            header.setOnClickListener {
                val expanded = content.visibility == View.VISIBLE
                content.visibility = if (expanded) View.GONE else View.VISIBLE
                arrowView.text = if (expanded) "▶" else "▼"
            }
            container.addView(header)
            container.addView(content)
            layout.addView(container)
            return content
        }
        scrollView.addView(layout)

        // 顶部页签：行为 / 外观 / 预览
        val topTabLayout = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)).apply {
            // 压缩 Tab 的上下内边距与最小高度，进一步降低整体上下留白
            setPadding(0, dp(2), 0, dp(2))
            minimumHeight = 0
            try {
                @Suppress("DEPRECATION")
                setSelectedTabIndicatorHeight(dp(1))
            } catch (_: Throwable) {}
        }
        fun addTopTab(text: String) { topTabLayout.addTab(topTabLayout.newTab().setText(text)) }
        addTopTab("行为")
        addTopTab("外观")
        addTopTab("预览")
        layout.addView(topTabLayout)

        val behaviorTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val appearanceTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val previewTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        layout.addView(behaviorTabContent)
        layout.addView(appearanceTabContent)
        layout.addView(previewTabContent)

        // 收紧 Tab 与内容区之间的间距
        (behaviorTabContent.layoutParams as? LinearLayout.LayoutParams)?.let { it.topMargin = dp(4); behaviorTabContent.layoutParams = it }
        (appearanceTabContent.layoutParams as? LinearLayout.LayoutParams)?.let { it.topMargin = dp(4); appearanceTabContent.layoutParams = it }
        (previewTabContent.layoutParams as? LinearLayout.LayoutParams)?.let { it.topMargin = dp(4); previewTabContent.layoutParams = it }

        topTabLayout.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { behaviorTabContent.visibility = View.VISIBLE; appearanceTabContent.visibility = View.GONE; previewTabContent.visibility = View.GONE }
                    1 -> { behaviorTabContent.visibility = View.GONE; appearanceTabContent.visibility = View.VISIBLE; previewTabContent.visibility = View.GONE }
                    2 -> { behaviorTabContent.visibility = View.GONE; appearanceTabContent.visibility = View.GONE; previewTabContent.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 进一步压缩每个 Tab 的内部上下 padding（子视图），消除底部多余留白
        topTabLayout.post {
            val strip = topTabLayout.getChildAt(0) as? ViewGroup
            val childCount = strip?.childCount ?: 0
            for (i in 0 until childCount) {
                val tabView = strip!!.getChildAt(i)
                tabView.setPadding(tabView.paddingLeft, dp(2), tabView.paddingRight, dp(2))
                (tabView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.bottomMargin = 0
                    lp.topMargin = 0
                    tabView.layoutParams = lp
                }
            }
        }

        // 基础设置（行为页签内的折叠）
        val baseSection = addCollapsibleSectionTo(behaviorTabContent, "基础设置（通用）", true)
        // 折叠标题与内容之间不再额外插 spacer，保证紧凑
        baseSection.addView(TextView(context).apply {
            text = context.getString(R.string.virtual_keyboard_menu_button_id_hint)
        })

        val buttonIdEditText = EditText(context)
        buttonIdEditText.setInputType(InputType.TYPE_CLASS_NUMBER)
        baseSection.addView(buttonIdEditText)
        buttonIdEditText.setText((virtualKeyboard.lastElementId + 1).toString())

        // 减少基础设置内部行间距
        baseSection.addView(TextView(context).apply {
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
        baseSection.addView(LinearLayout(context).apply {
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

        // 类型与编码（可折叠）
        // 控件组之间的垂直间距收紧（作用于行为容器内部，避免在根布局产生空白）
        addSpacerTo(behaviorTabContent, 6)
        val behaviorSection = addCollapsibleSectionTo(behaviorTabContent, "类型与编码（行为）", true)
        // 操作按钮容器（编辑态下使用）：置于“类型与编码（行为）”分组内部，避免与 Tab 之间产生跨容器空白
        var actionsContainer: LinearLayout? = null

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val vkCodeEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            // 仅允许数字输入，避免误输
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.virtual_keyboard_menu_vk_code_hint)
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

        // Tab 选择器替代下拉框：按钮/宏与热键/摇杆/触摸板
        var selectedButtonType = VirtualKeyboardElement.ButtonType.Button
        val typeTabLayout = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat))
        // 触摸板灵敏度控件（稍后添加到布局中）
        val touchpadSectionTitle = TextView(context).apply { text = "触摸板灵敏度"; visibility = View.GONE }
        val touchpadSensitivityText = TextView(context).apply { text = "100"; visibility = View.GONE }
        val touchpadSensitivity = SeekBar(context).apply { max = 300; progress = 100; visibility = View.GONE }
        touchpadSensitivity.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { touchpadSensitivityText.text = progress.toString() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        fun addTypeTab(text: String) { typeTabLayout.addTab(typeTabLayout.newTab().setText(text)) }
        addTypeTab(context.getString(R.string.button_type_button))
        addTypeTab(context.getString(R.string.button_type_hot_keys))
        addTypeTab(context.getString(R.string.button_type_joystick))
        addTypeTab(context.getString(R.string.button_type_touch_pad))

        typeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.Button
                        vkCodeButton.text = context.getString(R.string.virtual_keyboard_menu_vk_code_button)
                        vkCodeButton.setOnClickListener {
                            showVKCodeDialog(context, buttonTextEditText, vkCodeEditText)
                        }
                        vkCodeButton.visibility = View.VISIBLE
                        vkCodeEditText.visibility = View.VISIBLE
                        // 隐藏触摸板灵敏度
                        touchpadSectionTitle.visibility = View.GONE
                        touchpadSensitivity.visibility = View.GONE
                        touchpadSensitivityText.visibility = View.GONE
                    }
                    1 -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.HotKeys
                        // 宏/热键不需要 VK 表，隐藏按钮，但仍保留数值输入框
                        vkCodeButton.visibility = View.GONE
                        vkCodeEditText.visibility = View.GONE
                        // 隐藏触摸板灵敏度
                        touchpadSectionTitle.visibility = View.GONE
                        touchpadSensitivity.visibility = View.GONE
                        touchpadSensitivityText.visibility = View.GONE
                    }
                    2 -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.JoyStick
                        vkCodeButton.text = "JOY_CODE"
                        vkCodeButton.setOnClickListener {
                            showJoyStickVKCodeDialog(context, buttonTextEditText, vkCodeEditText)
                        }
                        vkCodeButton.visibility = View.VISIBLE
                        vkCodeEditText.visibility = View.VISIBLE
                        // 隐藏触摸板灵敏度
                        touchpadSectionTitle.visibility = View.GONE
                        touchpadSensitivity.visibility = View.GONE
                        touchpadSensitivityText.visibility = View.GONE
                    }
                    3 -> {
                        selectedButtonType = VirtualKeyboardElement.ButtonType.TouchPad
                        // 触摸板无需 VK 表，隐藏按钮并显示灵敏度
                        vkCodeButton.visibility = View.GONE
                        vkCodeEditText.visibility = View.GONE
                        touchpadSectionTitle.visibility = View.VISIBLE
                        touchpadSensitivity.visibility = View.VISIBLE
                        touchpadSensitivityText.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 初始化 Tab 为当前元素类型，确保类型相关控件（如触摸板灵敏度）正确显示，且保存时不会误改类型
        if (element != null) {
            selectedButtonType = element!!.buttonType
            val initialIndex = when (selectedButtonType) {
                VirtualKeyboardElement.ButtonType.Button -> 0
                VirtualKeyboardElement.ButtonType.HotKeys -> 1
                VirtualKeyboardElement.ButtonType.JoyStick -> 2
                VirtualKeyboardElement.ButtonType.TouchPad -> 3
            }
            typeTabLayout.getTabAt(initialIndex)?.select()
        }

        // 放置类型切换和 VK 输入区
        behaviorSection.addView(typeTabLayout)
        behaviorSection.addView(linearLayout)
        // 触摸板灵敏度模块（初始隐藏，由 Tab 切换控制）
        addSpacerTo(behaviorSection, 6)
        behaviorSection.addView(touchpadSectionTitle)
        behaviorSection.addView(touchpadSensitivity)
        behaviorSection.addView(touchpadSensitivityText)

        // 样式与外观（外观页签内折叠，默认展开）
        addSpacerTo(appearanceTabContent, 6)
        val appearanceSection = addCollapsibleSectionTo(appearanceTabContent, "样式与外观（外观）", true)
        // 外观页签顶部：全局状态切换（常态｜按下）
        val appearanceStateTabs = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat))
        fun addStateTab(text: String) { appearanceStateTabs.addTab(appearanceStateTabs.newTab().setText(text)) }
        addStateTab("常态")
        addStateTab("按下")
        appearanceSection.addView(appearanceStateTabs)
        var isAppearancePressed = false
        // 监听器将在 updateAppearanceStateVisibility 定义后再注册
        // 基础色（作为 BG_* 的回退色）
        // ============ 外观 Tab 分组（常态｜按下切换）===========
        // 1) 颜色（背景颜色、背景透明度）——左右并排
        addSpacerTo(appearanceSection, 6)
        val colorSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        appearanceSection.addView(colorSection)
        colorSection.addView(TextView(context).apply { text = "颜色（背景）" })
        val bgRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bgColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val bgColorSwatch = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }
            setBackgroundColor(Color.parseColor("#888888"))
        }
        val bgAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val bgAlphaLabel = TextView(context).apply { text = "背景透明度 100"; setPadding(dp(8), 0, 0, 0) }
        bgAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { bgAlphaLabel.text = "背景透明度 $progress" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // 左：颜色输入+色块；右：透明度滑杆
        val bgLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val bgRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        bgLeft.addView(bgColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bgLeft.addView(bgColorSwatch)
        bgRight.addView(bgAlphaLabel)
        bgRight.addView(bgAlphaSeek)
        bgRow.addView(bgLeft)
        bgRow.addView(bgRight)
        colorSection.addView(bgRow)
        // 延后绑定：确保 showColorPalette 已声明

        // 按下态对应字段（共用控件对象命名：bgPressed*）
        val bgPressedColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val bgPressedColorSwatch = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }
            setBackgroundColor(Color.parseColor("#0000FF"))
        }
        val bgPressedAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val bgPressedAlphaLabel = TextView(context).apply { text = "背景透明度 100"; setPadding(dp(8), 0, 0, 0) }
        bgPressedAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { bgPressedAlphaLabel.text = "背景透明度 $progress" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val bgPressedRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bgPressedLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val bgPressedRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        bgPressedLeft.addView(bgPressedColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bgPressedLeft.addView(bgPressedColorSwatch)
        bgPressedRight.addView(bgPressedAlphaLabel)
        bgPressedRight.addView(bgPressedAlphaSeek)
        bgPressedRow.addView(bgPressedLeft)
        bgPressedRow.addView(bgPressedRight)
        colorSection.addView(bgPressedRow)
        // 延后绑定：确保 showColorPalette 已声明

        // 边框设置（描边）
        addSpacerTo(appearanceSection, 6)
        val borderEnableCheck = CheckBox(context).apply { text = "启用描边"; isChecked = true }
        appearanceSection.addView(borderEnableCheck)

        val borderWidthText = TextView(context).apply { text = "描边大小" }
        appearanceSection.addView(borderWidthText)
        val borderWidthSeek = SeekBar(context).apply { max = 24; progress = (context.resources.displayMetrics.heightPixels*0.004f).toInt().coerceAtMost(24) }
        appearanceSection.addView(borderWidthSeek)

        addSpacerTo(appearanceSection, 6)
        appearanceSection.addView(TextView(context).apply { text = "描边" })
        val borderRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val borderLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val borderRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val borderColorEditText = EditText(context).apply { hint = "#AARRGGBB" }
        val borderColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#888888")) }
        val borderAlphaText = TextView(context).apply { text = "描边透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val borderAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        borderAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { borderAlphaText.text = "描边透明度 $progress" }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        borderLeft.addView(borderColorEditText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        borderLeft.addView(borderColorSwatch)
        borderRight.addView(borderAlphaText)
        borderRight.addView(borderAlphaSeek)
        borderRow.addView(borderLeft)
        borderRow.addView(borderRight)
        appearanceSection.addView(borderRow)
        borderAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { borderAlphaText.text = "描边透明度 $progress" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // 按下态描边（颜色/透明度）
        addSpacerTo(appearanceSection, 6)
        val borderRowPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val borderLeftPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val borderRightPressed = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val borderColorEditTextPressed = EditText(context).apply { hint = "#AARRGGBB" }
        val borderColorSwatchPressed = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#888888")) }
        val borderAlphaTextPressed = TextView(context).apply { text = "描边透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val borderAlphaSeekPressed = SeekBar(context).apply { max = 100; progress = 100 }
        borderAlphaSeekPressed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { borderAlphaTextPressed.text = "描边透明度 $progress" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        borderLeftPressed.addView(borderColorEditTextPressed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        borderLeftPressed.addView(borderColorSwatchPressed)
        borderRightPressed.addView(borderAlphaTextPressed)
        borderRightPressed.addView(borderAlphaSeekPressed)
        borderRowPressed.addView(borderLeftPressed)
        borderRowPressed.addView(borderRightPressed)
        appearanceSection.addView(borderRowPressed)

        // 开关联动描边设置的启用状态
        fun setBorderSectionEnabled(enabled: Boolean) {
            borderWidthText.isEnabled = enabled
            borderWidthSeek.isEnabled = enabled
            borderRow.isEnabled = enabled
            borderColorEditText.isEnabled = enabled
            borderColorSwatch.isEnabled = enabled
            borderAlphaText.isEnabled = enabled
            borderAlphaSeek.isEnabled = enabled
            borderRowPressed.isEnabled = enabled
            borderColorEditTextPressed.isEnabled = enabled
            borderColorSwatchPressed.isEnabled = enabled
            borderAlphaTextPressed.isEnabled = enabled
            borderAlphaSeekPressed.isEnabled = enabled
        }
        setBorderSectionEnabled(borderEnableCheck.isChecked)
        // 暂不调用 updatePreview，等预览函数定义后统一刷新
        borderEnableCheck.setOnCheckedChangeListener { _, checked -> setBorderSectionEnabled(checked) }

        // 增强型颜色选择器：色板 + 色谱（HSV+Alpha），可选择任意颜色
        fun showColorPalette(target: EditText, swatch: View) {
            var dialog: AlertDialog? = null

            // 读取初始颜色（优先使用输入框，其次使用色块）
            fun safeParseHexOrNull(text: String?): Int? {
                return try {
                    if (text.isNullOrBlank()) null else getHexValue(text).toInt()
                } catch (_: Throwable) { null }
            }
            val initialFromText = safeParseHexOrNull(target.text?.toString())
            val initialColor = initialFromText ?: run {
                try {
                    // 尝试从色块背景读取
                    val bg = swatch.background
                    if (bg is GradientDrawable) {
                        // GradientDrawable 不能直接读色，这里退回默认
                        null
                    } else null
                } catch (_: Throwable) { null }
            } ?: Color.parseColor("#FF888888")

            // 顶部 Tab
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val tabs = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)).apply {
                minimumHeight = 0
                setPadding(0, dp(2), 0, dp(2))
                try { @Suppress("DEPRECATION") setSelectedTabIndicatorHeight(dp(1)) } catch (_: Throwable) {}
            }
            fun addTab(text: String) { tabs.addTab(tabs.newTab().setText(text)) }
            addTab("色板")
            addTab("色轮")
            root.addView(tabs)

            // 色板视图
            val paletteScroll = ScrollView(context)
            val paletteGrid = GridLayout(context).apply {
                columnCount = 5
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val presetColors = intArrayOf(
                Color.parseColor("#FFFFFFFF"), Color.parseColor("#FF000000"), Color.parseColor("#FFFF0000"), Color.parseColor("#FF00FF00"), Color.parseColor("#FF0000FF"),
                Color.parseColor("#FFFFFF00"), Color.parseColor("#FF00FFFF"), Color.parseColor("#FFFF00FF"), Color.parseColor("#FF888888"), Color.parseColor("#FF444444"),
                Color.parseColor("#FF2196F3"), Color.parseColor("#FF4CAF50"), Color.parseColor("#FFF44336"), Color.parseColor("#FFFF9800"), Color.parseColor("#FF9C27B0")
            )
            for (c in presetColors) {
                paletteGrid.addView(View(context).apply {
                    layoutParams = GridLayout.LayoutParams().apply { width = dp(36); height = dp(36); setMargins(dp(6), dp(6), dp(6), dp(6)) }
                    background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(c); setStroke(dp(1), Color.parseColor("#33000000")) }
                    setOnClickListener {
                        target.setText(String.format("%08X", c))
                        swatch.setBackgroundColor(c)
                        dialog?.dismiss()
                    }
                })
            }
            paletteScroll.addView(paletteGrid)

            // 圆形色轮视图（HS 圆盘 + V/Alpha 滑杆）
            val spectrum = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val preview = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(initialColor) }
            }
            val hexLabel = TextView(context).apply { text = String.format("#%08X", initialColor); setTextColor(Color.parseColor("#FFCCCCCC")) }
            // 自定义圆形色轮 View：拖动选择 H/S
            class ColorWheelView(ctx: Context): View(ctx) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                private var wheelBitmap: Bitmap? = null
                private var radiusPx: Float = 0f
                var hue: Float = 0f
                var sat: Float = 0f
                var onChanged: ((Float, Float)->Unit)? = null
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    // 让色轮为正方形，边长取可用宽度，避免左右空隙
                    val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
                    val size = widthSize
                    setMeasuredDimension(size, size)
                }
                override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                    super.onSizeChanged(w, h, oldw, oldh)
                    val size = min(w, h)
                    radiusPx = size / 2f
                    wheelBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = Canvas(bmp)
                        val cx = size / 2f
                        val cy = size / 2f
                        for (y in 0 until size) {
                            for (x in 0 until size) {
                                val dx = x - cx
                                val dy = y - cy
                                val r = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                if (r <= radiusPx) {
                                    val hDeg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                                    val sVal = (r / radiusPx).coerceIn(0f, 1f)
                                    val c = Color.HSVToColor(floatArrayOf(hDeg, sVal, 1f))
                                    (bmp as Bitmap).setPixel(x, y, c)
                                } else {
                                    (bmp as Bitmap).setPixel(x, y, Color.TRANSPARENT)
                                }
                            }
                        }
                    }
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    wheelBitmap?.let { bmp ->
                        val left = (width - bmp.width) / 2f
                        val top = (height - bmp.height) / 2f
                        canvas.drawBitmap(bmp, left, top, paint)
                        // 绘制当前选择点
                        val cx = width / 2f
                        val cy = height / 2f
                        val r = sat * (bmp.width / 2f)
                        val rad = Math.toRadians(hue.toDouble())
                        val px = (cx + r * Math.cos(rad)).toFloat()
                        val py = (cy + r * Math.sin(rad)).toFloat()
                        paint.style = Paint.Style.STROKE
                        paint.color = Color.WHITE
                        paint.strokeWidth = dp(2).toFloat()
                        canvas.drawCircle(px, py, dp(6).toFloat(), paint)
                    }
                }
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    val cx = width / 2f
                    val cy = height / 2f
                    val dx = event.x - cx
                    val dy = event.y - cy
                    val r = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                    val size = min(width, height)
                    val maxR = size / 2f
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            val inside = r <= maxR
                            parent?.requestDisallowInterceptTouchEvent(inside)
                            if (inside) {
                                hue = angle
                                sat = (r / maxR).coerceIn(0f, 1f)
                                onChanged?.invoke(hue, sat)
                                invalidate()
                                return true
                            }
                            return false
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                    }
                    return super.onTouchEvent(event)
                }
            }
            // 左右布局：左半色轮（较小），右半控制条（明度/透明度），右下仍预留一定空白可滚动
            val wheel = ColorWheelView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(200), 1f)
            }
            val valSeek = SeekBar(context).apply { max = 100 }
            val alpSeek = SeekBar(context).apply { max = 100 }
            val hsv = FloatArray(3)
            Color.colorToHSV(initialColor, hsv)
            val initialA = (initialColor ushr 24) and 0xFF
            wheel.hue = hsv[0]
            wheel.sat = hsv[1]
            valSeek.progress = (hsv[2] * 100).toInt()
            alpSeek.progress = (initialA * 100 / 255f).toInt()

            fun composeColor(): Int {
                val h = wheel.hue
                val s = wheel.sat
                val v = valSeek.progress / 100f
                val a = (alpSeek.progress.coerceIn(0, 100) * 255f / 100f).toInt().coerceIn(0, 255)
                val rgb = Color.HSVToColor(floatArrayOf(h, s, v))
                val out = (a shl 24) or (rgb and 0x00FFFFFF)
                return out
            }
            fun refreshPreview() {
                val c = composeColor()
                (preview.background as? GradientDrawable)?.setColor(c)
                hexLabel.text = String.format("#%08X", c)
            }
            val listener = object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { refreshPreview() }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
            wheel.onChanged = { _, _ -> refreshPreview() }
            valSeek.setOnSeekBarChangeListener(listener)
            alpSeek.setOnSeekBarChangeListener(listener)

            fun addLabeledSeek(label: String, seek: SeekBar): LinearLayout {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(TextView(context).apply { text = label; setTextColor(Color.parseColor("#FFBBBBBB")); setPadding(0, dp(8), dp(8), dp(8)) })
                row.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                return row
            }
            val rightControls = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rightControls.addView(addLabeledSeek("明度", valSeek))
            rightControls.addView(addLabeledSeek("透明度", alpSeek))

            val wheelRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            wheelRow.addView(wheel)
            wheelRow.addView(rightControls)

            spectrum.addView(preview)
            spectrum.addView(hexLabel)
            spectrum.addView(wheelRow)

        // 内容容器：随 Tab 切换（保持取色器现有逻辑，不再额外优化）
            val content = FrameLayout(context)
            content.addView(paletteScroll)
            content.addView(spectrum)
            spectrum.visibility = View.GONE
            root.addView(content)

            tabs.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener{
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    if (tab?.position == 0) { paletteScroll.visibility = View.VISIBLE; spectrum.visibility = View.GONE }
                    else { paletteScroll.visibility = View.GONE; spectrum.visibility = View.VISIBLE }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            dialog = AlertDialog.Builder(context)
                .setTitle("选择颜色")
                .setView(ScrollView(context).apply { addView(root) })
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button) { _, _ ->
                    // 确定：使用色谱当前值（若用户停留在色板页，通常已即时应用并关闭，这里作为兜底）
                    val c = composeColor()
                    target.setText(String.format("%08X", c))
                    swatch.setBackgroundColor(c)
                }
                .create()
            dialog?.show()
        }

        // 现在绑定颜色块的点击（在 showColorPalette 定义之后）
        bgColorSwatch.setOnClickListener { showColorPalette(bgColorEdit, bgColorSwatch) }
        bgPressedColorSwatch.setOnClickListener { showColorPalette(bgPressedColorEdit, bgPressedColorSwatch) }
        borderColorSwatch.setOnClickListener { showColorPalette(borderColorEditText, borderColorSwatch) }
        borderColorSwatchPressed.setOnClickListener { showColorPalette(borderColorEditTextPressed, borderColorSwatchPressed) }

        // 文本颜色与透明度（受全局状态切换控制，左右并排）
        addSpacerTo(appearanceSection, 6)
        val textSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        appearanceSection.addView(textSection)
        textSection.addView(TextView(context).apply { text = "字体" })
        // 常态行：左=颜色，右=透明度
        val textRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val textLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val textRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val textColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val textColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#FFFFFFFF")) }
        val textAlphaText = TextView(context).apply { text = "字体透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val textAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        textAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaText.text = "字体透明度 $progress" }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        textLeft.addView(textColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        textLeft.addView(textColorSwatch)
        textRight.addView(textAlphaText)
        textRight.addView(textAlphaSeek)
        textRow.addView(textLeft)
        textRow.addView(textRight)
        textSection.addView(textRow)
        textColorSwatch.setOnClickListener { showColorPalette(textColorEdit, textColorSwatch) }
        // 按下行
        val textRowPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val textLeftPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val textRightPressed = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val textColorEditPressed = EditText(context).apply { hint = "#AARRGGBB" }
        val textColorSwatchPressed = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#FFFFFFFF")) }
        val textAlphaTextPressed = TextView(context).apply { text = "字体透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val textAlphaSeekPressed = SeekBar(context).apply { max = 100; progress = 100 }
        textAlphaSeekPressed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaTextPressed.text = "字体透明度 $progress" }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        textLeftPressed.addView(textColorEditPressed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        textLeftPressed.addView(textColorSwatchPressed)
        textRightPressed.addView(textAlphaTextPressed)
        textRightPressed.addView(textAlphaSeekPressed)
        textRowPressed.addView(textLeftPressed)
        textRowPressed.addView(textRightPressed)
        textSection.addView(textRowPressed)
        textAlphaSeekPressed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaTextPressed.text = "字体透明度 $progress（按下）" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 根据“常态｜按下”切换隐藏/显示对应的字段
        fun updateAppearanceStateVisibility() {
            val showNormal = !isAppearancePressed
            fun setVisible(v: View, visible: Boolean) { v.visibility = if (visible) View.VISIBLE else View.GONE }

            // 颜色（背景）：仅显示当前状态的行
            setVisible(bgRow, showNormal)
            setVisible(bgPressedRow, !showNormal)

            // 描边（常态/按下）仅切换颜色与透明度行，宽度/开关始终显示
            setVisible(borderRow, showNormal)
            setVisible(borderRowPressed, !showNormal)

            // 文本（常态/按下）整体行切换
            setVisible(textRow, showNormal)
            setVisible(textRowPressed, !showNormal)
        }

        // 初始化一次可见性
        updateAppearanceStateVisibility()
        // 现在注册 Tab 监听，避免在函数未定义时调用
        appearanceStateTabs.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isAppearancePressed = (tab?.position == 1)
                updateAppearanceStateVisibility()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        appearanceStateTabs.getTabAt(0)?.select()
        textAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaText.text = "字体透明度 $progress" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 背景（旧实现）已被统一“颜色”/“字体”分组取代，删除避免重复声明

        // 已移除“整体颜色覆盖”功能，避免与 BG_* 产生概念冲突

        // 可收纳分组：其他（整体透明度/整体圆角）
        // 将“其他”分组提到与“样式与外观（外观）”同级
        val othersSection = addCollapsibleSectionTo(appearanceTabContent, "其他", true)
        // 移除整体透明度（判断为无用项）

        // 圆角
        val radiusTextView = TextView(context).apply { text = "整体圆角" }
        othersSection.addView(radiusTextView)
        val radiusSeekBar = SeekBar(context).apply {
            max = 255
            progress = 10
        }
        othersSection.addView(radiusSeekBar)
        radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " " + radiusSeekBar.progress
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 编组输入（紧跟行为设置，减少与外观区块的间距）
        // 将“编组”内容移入行为页签并调小前导间距
        val groupingSection = addCollapsibleSectionTo(behaviorSection, "编组（行为）", false)
        val groupingRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val groupingLabel = TextView(context).apply { text = context.getString(R.string.virtual_keyboard_menu_grouping) }
        val groupEditText = EditText(context).apply { hint = context.getString(R.string.virtual_keyboard_menu_group_hint_id); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        groupingRow.addView(groupingLabel)
        groupingRow.addView(groupEditText)
        groupingSection.addView(groupingRow)

        // 触摸板灵敏度模块已在上文紧随 Tab 后加入，这里不再重复添加

        // 预览面板：同时显示常态与按下两种效果（预览页签）
        // 收紧分组之间的垂直间距，减少视觉留白（作用于预览容器内部）
        addSpacerTo(previewTabContent, 6)
        // 预览（效果）小节
        // 使用容器 + 背景视图 + 文字视图，贴近 DigitalButton 的真实渲染
        fun buildSinglePreview(): Pair<FrameLayout, Pair<View, TextView>> {
            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        }
            val bg = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
            val txt = TextView(context).apply {
                val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            lp.gravity = android.view.Gravity.CENTER
            layoutParams = lp
            text = ""
            textSize = 16f
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = android.view.Gravity.CENTER
        }
            container.addView(bg)
            container.addView(txt)
            return container to (bg to txt)
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val (normalContainer, normalPair) = buildSinglePreview()
        val (pressedContainer, pressedPair) = buildSinglePreview()
        // 为了区分，右侧加一点起始间距
        (pressedContainer.layoutParams as? LinearLayout.LayoutParams)?.setMargins(dp(8), 0, 0, 0)
        row.addView(normalContainer)
        row.addView(pressedContainer)

        fun parseColorOrDefault(input: String, fallback: Int): Int {
            return try {
                if (input.isBlank()) fallback else getHexValue(input).toInt()
            } catch (_: Throwable) { fallback }
        }
        fun applyAlphaToColor(color: Int, alphaPercent: Int): Int {
            val pct = alphaPercent.coerceIn(0, 100) / 100f
            val baseA = (color ushr 24) and 0xFF
            val newA = (baseA * pct).toInt().coerceIn(0, 255)
            return (newA shl 24) or (color and 0x00FFFFFF)
        }

        // 先声明一个空函数引用，稍后绑定真实实现
        var updatePreview: () -> Unit = {}
        val realUpdate: () -> Unit = {

            // 1) 计算基础填充色：直接使用 BG_*（若缺失则回退到默认）
            val normalBase = parseColorOrDefault(bgColorEdit.text.toString(), 0xF0888888.toInt())
            val pressedBase = parseColorOrDefault(bgPressedColorEdit.text.toString(), 0xF00000FF.toInt())
            // 常态
            var normalFill = normalBase
            // 按下
            var pressedFill = pressedBase
            // 若用户填写了 BG_*，优先覆盖基础色并应用 BG_ALPHA*
            val hasBgNormal = bgColorEdit.text?.isNotBlank() == true
            val hasBgPressed = bgPressedColorEdit.text?.isNotBlank() == true
            normalFill = if (hasBgNormal) applyAlphaToColor(
                parseColorOrDefault(bgColorEdit.text.toString(), normalFill),
                bgAlphaSeek.progress
            ) else normalFill
            pressedFill = if (hasBgPressed) applyAlphaToColor(
                parseColorOrDefault(bgPressedColorEdit.text.toString(), pressedFill),
                bgPressedAlphaSeek.progress
            ) else pressedFill

            // 2) 移除整体覆盖：不再额外叠加 OVERALL_*

            // 3) 边框
            val borderC0 = parseColorOrDefault(borderColorEditText.text.toString(), normalFill)
            val borderC = applyAlphaToColor(borderC0, borderAlphaSeek.progress)
            val strokeW = if (borderEnableCheck.isChecked) borderWidthSeek.progress else 0

            // 4) 背景绘制（常态）
            val normalDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusSeekBar.progress.toFloat()
                setColor(normalFill)
                if (strokeW > 0) setStroke(strokeW, borderC) else setStroke(0, borderC)
            }
            normalPair.first.background = normalDrawable
            // 背景绘制（按下）
            val pressedDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusSeekBar.progress.toFloat()
                setColor(pressedFill)
                if (strokeW > 0) setStroke(strokeW, borderC) else setStroke(0, borderC)
            }
            pressedPair.first.background = pressedDrawable

            // 5) 文本预览（颜色+透明度），与 DigitalButton.onElementDraw 一致
            val txt = buttonTextEditText.text?.toString() ?: ""
            val baseTextColor = parseColorOrDefault(textColorEdit.text.toString(), Color.WHITE)
            val baseA = (baseTextColor ushr 24) and 0xFF
            val textA = (baseA * (textAlphaSeek.progress.coerceIn(0, 100) / 100f)).toInt().coerceIn(0, 255)
            val composedTextColor = (textA shl 24) or (baseTextColor and 0x00FFFFFF)
            normalPair.second.text = txt
            normalPair.second.setTextColor(composedTextColor)
            pressedPair.second.text = txt
            pressedPair.second.setTextColor(composedTextColor)

            // 同步色板提示
            val bgNormal = parseColorOrDefault(bgColorEdit.text.toString(), 0xF0888888.toInt())
            val bgPressed = parseColorOrDefault(bgPressedColorEdit.text.toString(), 0xF00000FF.toInt())
            bgColorSwatch.setBackgroundColor(bgNormal)
            bgPressedColorSwatch.setBackgroundColor(bgPressed)
        }
        updatePreview = realUpdate
        // 监听预览变化（所有相关输入改变均刷新）
        // 背景色输入变化刷新预览
        bgColorEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        bgPressedColorEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        buttonTextEditText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        textColorEdit.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        bgColorEdit.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        bgPressedColorEdit.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) {}
        })
        // 去除“预览按下”开关，改为同时显示两个预览
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " $progress"; updatePreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // 已移除整体透明度功能
        textAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 文本标签与预览同时更新，避免被后续监听覆盖导致文本不同步
                textAlphaText.text = "字体透明度 $progress"
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // 移除整体覆盖相关监听
        previewTabContent.addView(row)



        if (element != null) {
            // 在“类型与编码（行为）”分组内追加一个紧凑的操作容器，避免产生新的折叠头部间距
            actionsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            behaviorSection.addView(actionsContainer!!)

            val setJoyStickButton = Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_set_the_handle_arrow_keys)
                setOnClickListener {
                    setJoyStickVKCodeDialog(context, element!!)
                }
                visibility = View.GONE
            }
            // 将手柄方向键设置按钮放入操作容器
            actionsContainer!!.addView(setJoyStickButton)
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
            typeTabLayout.getTabAt(selectedButtonType.ordinal)?.select()
            // actionsContainer 已在上方创建
            actionsContainer!!.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_copy_button)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.copyButton(virtualKeyboard, element, context)
                    game?.postNotification(context.getString(R.string.virtual_keyboard_menu_copy_button) + "\n" + element?.elementId, 2000)
                    // 复制后立即持久化，避免用户未退出编辑模式导致丢失
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                }
                Log.d("vk", "复制按钮"+ element?.leftMargin+","+element?.topMargin+","+element?.width+","+element?.height)
            })

            // 外观功能按钮分组（可收纳）
            // 将“样式管理”分组提到与“样式与外观（外观）”同级
            val styleActionsSection = addCollapsibleSectionTo(appearanceTabContent, "样式管理", true)
            styleActionsSection.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_copy_appearance_style)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.copyAppearanceStyle(virtualKeyboard, element, context)
                }
            })

            // 粘贴外观样式
            styleActionsSection.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_paste_appearance_style)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.pasteAppearanceStyle(virtualKeyboard, element, context)
                    // 粘贴后刷新以确保立即可视
                    virtualKeyboard.refreshLayout()
                }
            })

            // 应用该样式到相同组
            styleActionsSection.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_apply_style_to_same_group)
                setOnClickListener {
                    VirtualKeyboardConfigurationLoader.applyAppearanceStyleToSameGroup(virtualKeyboard, element, context)
                    virtualKeyboard.refreshLayout()
                }
            })

            // 粘贴外观样式（填充对话框，不立即保存）
            styleActionsSection.addView(Button(context).apply {
                text = context.getString(R.string.virtual_keyboard_menu_paste_appearance_style_to_form)
                setOnClickListener {
                    try {
                        val style = VirtualKeyboardConfigurationLoader.getAppearanceStyleFromClipboard(context)
                        if (style == null) {
                            Toast.makeText(context, "样式剪贴板为空", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        fun setEditIfHas(key: String, et: EditText, swatch: View? = null) {
                            if (style.has(key)) {
                                val c = style.getInt(key)
                                et.setText(String.format("%08X", c))
                                swatch?.setBackgroundColor(c)
                            }
                        }
                        // 兼容旧字段：NORMAL_COLOR/PRESSED_COLOR → 新的 BG_* 控件
                        setEditIfHas("NORMAL_COLOR", bgColorEdit, null)
                        setEditIfHas("PRESSED_COLOR", bgPressedColorEdit, null)
                        // 圆角（保留），整体透明度已移除
                        if (style.has("RADIUS")) {
                            val r = style.optDouble("RADIUS", radiusSeekBar.progress.toDouble())
                            radiusSeekBar.progress = r.toInt().coerceIn(0, radiusSeekBar.max)
                            radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " " + radiusSeekBar.progress
                        }
                        // buttonData 子集
                        val data = style.optJSONObject("BUTTON_DATA")
                        if (data != null) {
                            if (data.has("BORDER_ENABLED")) borderEnableCheck.isChecked = data.getBoolean("BORDER_ENABLED")
                            borderWidthSeek.progress = data.optInt("BORDER_WIDTH_PX", borderWidthSeek.progress).coerceIn(0, borderWidthSeek.max)
                            setEditIfHas("BORDER_COLOR", borderColorEditText, borderColorSwatch)
                            borderAlphaSeek.progress = data.optInt("BORDER_ALPHA", borderAlphaSeek.progress).coerceIn(0, 100)

                            setEditIfHas("TEXT_COLOR", textColorEdit, textColorSwatch)
                            textAlphaSeek.progress = data.optInt("TEXT_ALPHA", textAlphaSeek.progress).coerceIn(0, 100)

                            setEditIfHas("BG_COLOR", bgColorEdit, bgColorSwatch)
                            bgAlphaSeek.progress = data.optInt("BG_ALPHA", bgAlphaSeek.progress).coerceIn(0, 100)
                            setEditIfHas("BG_COLOR_PRESSED", bgPressedColorEdit, bgPressedColorSwatch)
                            bgPressedAlphaSeek.progress = data.optInt("BG_ALPHA_PRESSED", bgPressedAlphaSeek.progress).coerceIn(0, 100)

                            // 忽略 OVERALL_* 旧字段（已废弃）
                        }
                        Toast.makeText(context, "已粘贴到表单，确认后保存", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("vk", "paste to form failed", e)
                        Toast.makeText(context, "粘贴失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            actionsContainer!!.addView(Button(context).apply {
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

        // 操作分组（编辑时展示）
        // 收紧“操作分组”与上文内容的间距
        addSpacer(6)

        val builder = AlertDialog.Builder(context)
        val dialog: AlertDialog
        if (element != null) {
            // 预填充已有值
            buttonIdEditText.setText(element?.elementId.toString())
            buttonTextEditText.setText(element?.text)
            vkCodeEditText.setText(element?.vk_code)
            // 整体透明度已移除，不再从 element 读取
            radiusSeekBar.progress = element?.radius!!.toInt()
            // 整体透明度已移除
            radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " " + radiusSeekBar.progress
            // 同步类型 Tab
            typeTabLayout.getTabAt(element!!.buttonType.ordinal)?.select()
            updatePreview()
            // 预填充边框与灵敏度
            try {
                val data = element!!.buttonData
                if (data != null) {
                    if (data.has("BORDER_ENABLED")) borderEnableCheck.isChecked = data.getBoolean("BORDER_ENABLED")
                    if (data.has("BORDER_WIDTH_PX")) borderWidthSeek.progress = data.getInt("BORDER_WIDTH_PX").coerceIn(0, 24)
                    if (data.has("BORDER_COLOR")) { val c = data.getInt("BORDER_COLOR"); borderColorEditText.setText(String.format("%08X", c)); borderColorSwatch.setBackgroundColor(c) }
                    if (data.has("BORDER_ALPHA")) borderAlphaSeek.progress = data.getInt("BORDER_ALPHA").coerceIn(0, 100)
                    if (data.has("TOUCHPAD_SENSITIVITY")) { val v = data.getInt("TOUCHPAD_SENSITIVITY"); touchpadSensitivity.progress = v; touchpadSensitivityText.text = v.toString() }
                    // 文本/背景/整体 预填
                    if (data.has("TEXT_COLOR")) { val c = data.getInt("TEXT_COLOR"); textColorEdit.setText(String.format("%08X", c)); textColorSwatch.setBackgroundColor(c) }
                    if (data.has("TEXT_ALPHA")) { textAlphaSeek.progress = data.getInt("TEXT_ALPHA").coerceIn(0,100) }
                    if (data.has("BG_COLOR")) { val c = data.getInt("BG_COLOR"); bgColorEdit.setText(String.format("%08X", c)); bgColorSwatch.setBackgroundColor(c) }
                    if (data.has("BG_ALPHA")) { bgAlphaSeek.progress = data.getInt("BG_ALPHA").coerceIn(0,100) }
                    if (data.has("BG_COLOR_PRESSED")) { val c = data.getInt("BG_COLOR_PRESSED"); bgPressedColorEdit.setText(String.format("%08X", c)); bgPressedColorSwatch.setBackgroundColor(c) }
                    if (data.has("BG_ALPHA_PRESSED")) { bgPressedAlphaSeek.progress = data.getInt("BG_ALPHA_PRESSED").coerceIn(0,100) }
                    // 忽略 OVERALL_* 旧字段（已废弃）
                }
            } catch (_: Exception) {}

            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_button_title))
                .setView(scrollView)
                .setCancelable(false)
                // 编辑模式：正向=删除（直接执行删除，沿用原逻辑），中立=保存，负向=取消
                .setPositiveButton(R.string.virtual_keyboard_menu_remove_button) { _, _ ->
                    try {
                        virtualKeyboard.removeElementByElement(element)
                        // 删除后立即保存
                        VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                    } catch (e: Exception) {
                        Log.e("vk", "delete failed", e)
                        Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(R.string.virtual_keyboard_menu_save_button, null)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .create()

            if (element?.group != -1) {
                groupEditText.setText(element?.group.toString())
                // 添加删除组按钮
                // 删除组按钮也归入操作容器，保持区域内聚
                actionsContainer?.addView(Button(context).apply {
                    text = context.getString(R.string.virtual_keyboard_menu_delete_group_button)
                    setOnClickListener {
                        val elements = virtualKeyboard.elements.filter { it.group == element?.group }
                        elements.forEach { virtualKeyboard.removeElementByElement(it) }
                        VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                        dialog.dismiss()
                    }
                })
            }
        } else {
            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_add_button_title))
                .setView(scrollView)
                .setCancelable(true)
                .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button, null)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .create()
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        // 修复：编辑态“保存”按钮监听需在 show() 之后直接绑定，否则不会触发
        if (element != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                try {
                    fun parseColorSafely(input: String, fallback: Int): Int {
                        if (input.isBlank()) return fallback
                        return try { getHexValue(input).toInt() } catch (_: Throwable) { fallback }
                    }
                    val el = element ?: return@setOnClickListener
                    el.text = buttonTextEditText.text.toString()
                    el.vk_code = vkCodeEditText.text.toString()
                    el.elementId = buttonIdEditText.text.toString().toInt()
                    // 不再使用整体透明度
                    el.radius = radiusSeekBar.progress.toFloat()
                    el.buttonType = selectedButtonType
                    if (!groupEditText.text.isNullOrEmpty()) {
                        el.group = groupEditText.text.toString().toInt()
                    }
                    val data = el.buttonData ?: JSONObject()
                    data.put("BORDER_ENABLED", borderEnableCheck.isChecked)
                    data.put("BORDER_WIDTH_PX", borderWidthSeek.progress)
                    val borderC = parseColorSafely(borderColorEditText.text.toString(), el.normalColor)
                    data.put("BORDER_COLOR", borderC)
                    data.put("BORDER_ALPHA", borderAlphaSeek.progress)
                    if (selectedButtonType == VirtualKeyboardElement.ButtonType.TouchPad) {
                        data.put("TOUCHPAD_SENSITIVITY", touchpadSensitivity.progress)
                    }
                    fun parseColorOrNull(s: String): Int? = try { getHexValue(s).toInt() } catch (_: Throwable) { null }
                    parseColorOrNull(textColorEdit.text.toString())?.let { data.put("TEXT_COLOR", it) }
                    data.put("TEXT_ALPHA", textAlphaSeek.progress)
                    parseColorOrNull(bgColorEdit.text.toString())?.let { data.put("BG_COLOR", it) }
                    data.put("BG_ALPHA", bgAlphaSeek.progress)
                    parseColorOrNull(bgPressedColorEdit.text.toString())?.let { data.put("BG_COLOR_PRESSED", it) }
                    data.put("BG_ALPHA_PRESSED", bgPressedAlphaSeek.progress)
                    // 不再写入 OVERALL_*（已废弃）
                    // 使用 setter 以便触发特定元素的覆盖逻辑（如 RelativeTouchPad 应用灵敏度）
                    el.setButtonData(data)
                    el.invalidate()
                    // 保存并刷新布局，确保切换即时生效；刷新后保持在编辑模式（由 VirtualKeyboard.refreshLayout 恢复遮罩）
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                    virtualKeyboard.refreshLayout()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e("vk", "save failed", e)
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 按钮点击：保存/确认（仅新增模式绑定正向按钮）
        if (element == null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                // 新增模式：确认新增
                val defaultButtonId = virtualKeyboard.lastElementId + 1 // 默认按钮 ID
                val defaultVkCode = "0"   // 默认 VK 代码
                val defaultButtonText = "New Button" // 默认按钮文本

                val buttonId = buttonIdEditText.text.toString().toIntOrNull() ?: defaultButtonId
                val vkCode = vkCodeEditText.text.toString().ifEmpty { defaultVkCode }
                val buttonText = buttonTextEditText.text.toString().ifEmpty { defaultButtonText }

                    // 组装扩展外观/灵敏度参数
                    val data = JSONObject().apply {
                        put("BORDER_ENABLED", borderEnableCheck.isChecked)
                        put("BORDER_WIDTH_PX", borderWidthSeek.progress)
                        val borderC = parseColorOrDefault(borderColorEditText.text.toString(), 0xFF888888.toInt())
                        put("BORDER_COLOR", borderC)
                        put("BORDER_ALPHA", borderAlphaSeek.progress)
                        if (selectedButtonType == VirtualKeyboardElement.ButtonType.TouchPad) put("TOUCHPAD_SENSITIVITY", touchpadSensitivity.progress)
                    }
                VirtualKeyboardConfigurationLoader.addButton(
                    virtualKeyboard,
                    context,
                    buttonId,
                    vkCode,
                    buttonText,
                    selectedButtonType,
                        data
                    )
                    // 新增立即保存
                    VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                    dialog.dismiss()
            } catch (e: Exception) {
                Log.e("vk", "save/confirm failed", e)
                Toast.makeText(context, "输入无效，请检查数值与颜色格式", Toast.LENGTH_SHORT).show()
            }
        }
        }

        // 移除原先的 setOnShowListener 绑定方式，避免 show() 之后无法触发
    }

    fun createActionMap(): Map<String, () -> Unit> = createActionMap(null)

    fun createActionMap(dialog: AlertDialog?): Map<String, () -> Unit> {
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
        // 编组移动开关：点击后切换开关状态并立即关闭菜单，同时显示状态通知
        actionMap[context.getString(R.string.title_enable_group_move) + "(" + virtualKeyboard.groupMove + ")"] = {
            virtualKeyboard.groupMove = !virtualKeyboard.groupMove
            // 显示状态通知后关闭当前菜单
            game?.postNotification(context.getString(R.string.title_enable_group_move) + ":"+ virtualKeyboard.groupMove, 2000)
            // 根据菜单类型选择关闭方式
            if (dialog != null) {
                dialog.dismiss() // AlertDialog 关闭方式
            } else {
                // 在Fragment中，通过game对象关闭EditMenu（参考Game.java中的onBackPressed逻辑）
                val menuFragment = game?.fragmentManager?.findFragmentByTag("EditMenu")
                if (menuFragment is com.limelight.heokami.EditMenuFragment) {
                    menuFragment.hideMenuWithAnimation()
                }
            }
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
        // 复用游戏菜单的Fragment样式与动画
        if (game == null) return
        val fragment = EditMenuFragment.newInstance(game!!, virtualKeyboard)
        game!!.fragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment, "EditMenu")
            .commit()
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