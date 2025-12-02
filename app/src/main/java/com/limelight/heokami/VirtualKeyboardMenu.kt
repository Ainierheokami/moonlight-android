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
        // 定义 dp 辅助函数
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // ----------------- 1. 定义辅助函数 (提前定义以解决 Unresolved reference) -----------------

        // 颜色解析辅助
        fun parseColorSafely(input: String, fallback: Int): Int {
            if (input.isBlank()) return fallback
            return try { getHexValue(input).toInt() } catch (_: Throwable) { fallback }
        }

        // 增强型颜色选择器：色板 + 色谱
        fun showColorPalette(target: EditText, swatch: View) {
            var paletteDialog: AlertDialog? = null

            val initialFromText = try {
                if (target.text.isNullOrBlank()) null else getHexValue(target.text.toString()).toInt()
            } catch (_: Throwable) { null }
            val initialColor = initialFromText ?: Color.parseColor("#FF888888")

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
                        paletteDialog?.dismiss()
                    }
                })
            }
            paletteScroll.addView(paletteGrid)

            val spectrum = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val preview = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(4).toFloat(); setColor(initialColor) }
            }
            val hexLabel = TextView(context).apply { text = String.format("#%08X", initialColor); setTextColor(Color.parseColor("#FFCCCCCC")) }

            // 自定义色轮 View (局部类)
            class ColorWheelView(ctx: Context): View(ctx) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                private var wheelBitmap: Bitmap? = null
                private var radiusPx: Float = 0f
                var hue: Float = 0f
                var sat: Float = 0f
                var onChanged: ((Float, Float)->Unit)? = null
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
                    setMeasuredDimension(widthSize, widthSize)
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

            val wheel = ColorWheelView(context).apply { layoutParams = LinearLayout.LayoutParams(0, dp(200), 1f) }
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
                return (a shl 24) or (rgb and 0x00FFFFFF)
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

            paletteDialog = AlertDialog.Builder(context)
                .setTitle("选择颜色")
                .setView(ScrollView(context).apply { addView(root) })
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button) { _, _ ->
                    val c = composeColor()
                    target.setText(String.format("%08X", c))
                    swatch.setBackgroundColor(c)
                }
                .create()
            paletteDialog?.show()
        }

        // ----------------- 2. 界面构建 -----------------

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val topTabLayout = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)).apply {
            setPadding(0, dp(2), 0, dp(2))
            minimumHeight = 0
            try { @Suppress("DEPRECATION") setSelectedTabIndicatorHeight(dp(1)) } catch (_: Throwable) {}
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        rootLayout.addView(topTabLayout)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        rootLayout.addView(scrollView)

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(6))
        }
        scrollView.addView(contentLayout)

        // UI 辅助函数
        fun addSpacerTo(parent: LinearLayout, heightDp: Int) {
            parent.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)) })
        }
        fun addCollapsibleSectionTo(parent: LinearLayout, title: String, defaultExpanded: Boolean = true): LinearLayout {
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
            val titleView = TextView(context).apply { text = title; setTextColor(Color.parseColor("#666666")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val arrowView = TextView(context).apply { text = if (defaultExpanded) "▼" else "▶"; setTextColor(Color.parseColor("#999999")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setPadding(dp(8), 0, dp(8), 0) }
            header.addView(titleView); header.addView(arrowView)
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = if (defaultExpanded) View.VISIBLE else View.GONE }
            header.setOnClickListener {
                val expanded = content.visibility == View.VISIBLE
                content.visibility = if (expanded) View.GONE else View.VISIBLE
                arrowView.text = if (expanded) "▶" else "▼"
            }
            container.addView(header); container.addView(content)
            parent.addView(container)
            return content
        }

        // 页签内容容器
        val behaviorTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val appearanceTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val previewTabContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        contentLayout.addView(behaviorTabContent); contentLayout.addView(appearanceTabContent); contentLayout.addView(previewTabContent)
        (behaviorTabContent.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)
        (appearanceTabContent.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)
        (previewTabContent.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)

        topTabLayout.addTab(topTabLayout.newTab().setText("行为"))
        topTabLayout.addTab(topTabLayout.newTab().setText("外观"))
        topTabLayout.addTab(topTabLayout.newTab().setText("预览"))
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

        // --- 行为页签内容 ---
        val baseSection = addCollapsibleSectionTo(behaviorTabContent, "基础设置（通用）", true)
        baseSection.addView(TextView(context).apply { text = context.getString(R.string.virtual_keyboard_menu_button_id_hint) })
        val buttonIdEditText = EditText(context).apply { setInputType(InputType.TYPE_CLASS_NUMBER); setText((virtualKeyboard.lastElementId + 1).toString()) }
        baseSection.addView(buttonIdEditText)
        baseSection.addView(TextView(context).apply { text = context.getString(R.string.virtual_keyboard_menu_button_text_hint) })
        val buttonTextEditText = EditText(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        baseSection.addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; addView(buttonTextEditText); addView(Button(context).apply { text = "X"; setOnClickListener { buttonTextEditText.setText("") } }) })

        addSpacerTo(behaviorTabContent, 6)
        val behaviorSection = addCollapsibleSectionTo(behaviorTabContent, "类型与编码（行为）", true)
        val vkLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        val vkCodeEditText = EditText(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); inputType = InputType.TYPE_CLASS_NUMBER; hint = context.getString(R.string.virtual_keyboard_menu_vk_code_hint) }
        val vkCodeButton = Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_vk_code_button); setOnClickListener { showVKCodeDialog(context, buttonTextEditText, vkCodeEditText) } }
        vkLayout.addView(vkCodeEditText); vkLayout.addView(vkCodeButton)

        // 类型选择 Tabs
        var selectedButtonType = VirtualKeyboardElement.ButtonType.Button
        val typeTabLayout = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat))
        val touchpadSectionTitle = TextView(context).apply { text = "触摸板灵敏度"; visibility = View.GONE }
        val touchpadSensitivityText = TextView(context).apply { text = "100"; visibility = View.GONE }
        val touchpadSensitivity = SeekBar(context).apply { max = 300; progress = 100; visibility = View.GONE }
        touchpadSensitivity.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { touchpadSensitivityText.text = progress.toString() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })

        fun addTypeTab(text: String) { typeTabLayout.addTab(typeTabLayout.newTab().setText(text)) }
        addTypeTab(context.getString(R.string.button_type_button)); addTypeTab(context.getString(R.string.button_type_hot_keys)); addTypeTab(context.getString(R.string.button_type_joystick)); addTypeTab(context.getString(R.string.button_type_touch_pad))
        typeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { selectedButtonType = VirtualKeyboardElement.ButtonType.Button; vkCodeButton.text = context.getString(R.string.virtual_keyboard_menu_vk_code_button); vkCodeButton.setOnClickListener { showVKCodeDialog(context, buttonTextEditText, vkCodeEditText) }; vkCodeButton.visibility = View.VISIBLE; vkCodeEditText.visibility = View.VISIBLE; touchpadSectionTitle.visibility = View.GONE; touchpadSensitivity.visibility = View.GONE; touchpadSensitivityText.visibility = View.GONE }
                    1 -> { selectedButtonType = VirtualKeyboardElement.ButtonType.HotKeys; vkCodeButton.visibility = View.GONE; vkCodeEditText.visibility = View.GONE; touchpadSectionTitle.visibility = View.GONE; touchpadSensitivity.visibility = View.GONE; touchpadSensitivityText.visibility = View.GONE }
                    2 -> { selectedButtonType = VirtualKeyboardElement.ButtonType.JoyStick; vkCodeButton.text = "JOY_CODE"; vkCodeButton.setOnClickListener { showJoyStickVKCodeDialog(context, buttonTextEditText, vkCodeEditText) }; vkCodeButton.visibility = View.VISIBLE; vkCodeEditText.visibility = View.VISIBLE; touchpadSectionTitle.visibility = View.GONE; touchpadSensitivity.visibility = View.GONE; touchpadSensitivityText.visibility = View.GONE }
                    3 -> { selectedButtonType = VirtualKeyboardElement.ButtonType.TouchPad; vkCodeButton.visibility = View.GONE; vkCodeEditText.visibility = View.GONE; touchpadSectionTitle.visibility = View.VISIBLE; touchpadSensitivity.visibility = View.VISIBLE; touchpadSensitivityText.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}; override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        if (element != null) { selectedButtonType = element!!.buttonType; typeTabLayout.getTabAt(selectedButtonType.ordinal)?.select() }
        behaviorSection.addView(typeTabLayout); behaviorSection.addView(vkLayout); addSpacerTo(behaviorSection, 6); behaviorSection.addView(touchpadSectionTitle); behaviorSection.addView(touchpadSensitivity); behaviorSection.addView(touchpadSensitivityText)

        val groupingSection = addCollapsibleSectionTo(behaviorSection, "编组（行为）", false)
        val groupingRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val groupingLabel = TextView(context).apply { text = context.getString(R.string.virtual_keyboard_menu_grouping) }
        val groupEditText = EditText(context).apply { hint = context.getString(R.string.virtual_keyboard_menu_group_hint_id); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        groupingRow.addView(groupingLabel); groupingRow.addView(groupEditText); groupingSection.addView(groupingRow)
        val actionsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; behaviorSection.addView(actionsContainer)

        // --- 外观页签内容 ---
        addSpacerTo(appearanceTabContent, 6)
        val appearanceSection = addCollapsibleSectionTo(appearanceTabContent, "样式与外观（外观）", true)
        val appearanceStateTabs = TabLayout(ContextThemeWrapper(context, com.google.android.material.R.style.Theme_AppCompat)).apply { addTab(newTab().setText("常态")); addTab(newTab().setText("按下")) }
        appearanceSection.addView(appearanceStateTabs)
        var isAppearancePressed = false

        addSpacerTo(appearanceSection, 6)
        val colorSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; appearanceSection.addView(colorSection); colorSection.addView(TextView(context).apply { text = "颜色（背景）" })

        // 常态背景
        val bgRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bgColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val bgColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#888888")) }
        val bgAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val bgAlphaLabel = TextView(context).apply { text = "背景透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val bgLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; bgLeft.addView(bgColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); bgLeft.addView(bgColorSwatch)
        val bgRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; bgRight.addView(bgAlphaLabel); bgRight.addView(bgAlphaSeek)
        bgRow.addView(bgLeft); bgRow.addView(bgRight); colorSection.addView(bgRow)
        // 按下背景
        val bgPressedRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bgPressedColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val bgPressedColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#0000FF")) }
        val bgPressedAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val bgPressedAlphaLabel = TextView(context).apply { text = "背景透明度 100"; setPadding(dp(8), 0, 0, 0) }
        val bgPressedLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; bgPressedLeft.addView(bgPressedColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); bgPressedLeft.addView(bgPressedColorSwatch)
        val bgPressedRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; bgPressedRight.addView(bgPressedAlphaLabel); bgPressedRight.addView(bgPressedAlphaSeek)
        bgPressedRow.addView(bgPressedLeft); bgPressedRow.addView(bgPressedRight); colorSection.addView(bgPressedRow)

        // 描边
        addSpacerTo(appearanceSection, 6)
        val borderEnableCheck = CheckBox(context).apply { text = "启用描边"; isChecked = true }; appearanceSection.addView(borderEnableCheck)
        val borderWidthText = TextView(context).apply { text = "描边大小" }; appearanceSection.addView(borderWidthText)
        val borderWidthSeek = SeekBar(context).apply { max = 24; progress = (context.resources.displayMetrics.heightPixels*0.004f).toInt().coerceAtMost(24) }; appearanceSection.addView(borderWidthSeek)
        appearanceSection.addView(TextView(context).apply { text = "描边颜色" })
        // 描边常态
        val borderRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val borderColorEditText = EditText(context).apply { hint = "#AARRGGBB" }
        val borderColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#888888")) }
        val borderAlphaText = TextView(context).apply { text = "描边透明度 100" }
        val borderAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val borderLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; borderLeft.addView(borderColorEditText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); borderLeft.addView(borderColorSwatch)
        val borderRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; borderRight.addView(borderAlphaText); borderRight.addView(borderAlphaSeek)
        borderRow.addView(borderLeft); borderRow.addView(borderRight); appearanceSection.addView(borderRow)
        // 描边按下
        val borderRowPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val borderColorEditTextPressed = EditText(context).apply { hint = "#AARRGGBB" }
        val borderColorSwatchPressed = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#888888")) }
        val borderAlphaTextPressed = TextView(context).apply { text = "描边透明度 100" }
        val borderAlphaSeekPressed = SeekBar(context).apply { max = 100; progress = 100 }
        val borderLeftPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; borderLeftPressed.addView(borderColorEditTextPressed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); borderLeftPressed.addView(borderColorSwatchPressed)
        val borderRightPressed = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; borderRightPressed.addView(borderAlphaTextPressed); borderRightPressed.addView(borderAlphaSeekPressed)
        borderRowPressed.addView(borderLeftPressed); borderRowPressed.addView(borderRightPressed); appearanceSection.addView(borderRowPressed)

        fun setBorderSectionEnabled(enabled: Boolean) {
            borderWidthText.isEnabled = enabled; borderWidthSeek.isEnabled = enabled
            borderRow.isEnabled = enabled; borderColorEditText.isEnabled = enabled; borderColorSwatch.isEnabled = enabled; borderAlphaText.isEnabled = enabled; borderAlphaSeek.isEnabled = enabled
            borderRowPressed.isEnabled = enabled; borderColorEditTextPressed.isEnabled = enabled; borderColorSwatchPressed.isEnabled = enabled; borderAlphaTextPressed.isEnabled = enabled; borderAlphaSeekPressed.isEnabled = enabled
        }
        setBorderSectionEnabled(borderEnableCheck.isChecked); borderEnableCheck.setOnCheckedChangeListener { _, checked -> setBorderSectionEnabled(checked) }

        // 字体
        addSpacerTo(appearanceSection, 6); val textSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; appearanceSection.addView(textSection); textSection.addView(TextView(context).apply { text = "字体" })
        val textRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val textColorEdit = EditText(context).apply { hint = "#AARRGGBB" }
        val textColorSwatch = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#FFFFFFFF")) }
        val textAlphaText = TextView(context).apply { text = "字体透明度 100" }
        val textAlphaSeek = SeekBar(context).apply { max = 100; progress = 100 }
        val textLeft = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; textLeft.addView(textColorEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); textLeft.addView(textColorSwatch)
        val textRight = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; textRight.addView(textAlphaText); textRight.addView(textAlphaSeek)
        textRow.addView(textLeft); textRow.addView(textRight); textSection.addView(textRow)
        val textRowPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val textColorEditPressed = EditText(context).apply { hint = "#AARRGGBB" }
        val textColorSwatchPressed = View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { setMargins(dp(8), dp(4), 0, dp(4)) }; setBackgroundColor(Color.parseColor("#FFFFFFFF")) }
        val textAlphaTextPressed = TextView(context).apply { text = "字体透明度 100" }
        val textAlphaSeekPressed = SeekBar(context).apply { max = 100; progress = 100 }
        val textLeftPressed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; textLeftPressed.addView(textColorEditPressed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); textLeftPressed.addView(textColorSwatchPressed)
        val textRightPressed = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }; textRightPressed.addView(textAlphaTextPressed); textRightPressed.addView(textAlphaSeekPressed)
        textRowPressed.addView(textLeftPressed); textRowPressed.addView(textRightPressed); textSection.addView(textRowPressed)

        // 其他
        val othersSection = addCollapsibleSectionTo(appearanceTabContent, "其他", true)
        val radiusTextView = TextView(context).apply { text = "整体圆角" }; othersSection.addView(radiusTextView)
        val radiusSeekBar = SeekBar(context).apply { max = 255; progress = 10 }; othersSection.addView(radiusSeekBar)

        // 样式管理
        val styleActionsSection = addCollapsibleSectionTo(appearanceTabContent, "样式管理", true)
        styleActionsSection.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_copy_appearance_style); setOnClickListener { VirtualKeyboardConfigurationLoader.copyAppearanceStyle(virtualKeyboard, element, context) } })
        styleActionsSection.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_paste_appearance_style); setOnClickListener { VirtualKeyboardConfigurationLoader.pasteAppearanceStyle(virtualKeyboard, element, context); virtualKeyboard.refreshLayout() } })
        styleActionsSection.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_apply_style_to_same_group); setOnClickListener { VirtualKeyboardConfigurationLoader.applyAppearanceStyleToSameGroup(virtualKeyboard, element, context); virtualKeyboard.refreshLayout() } })
        styleActionsSection.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_paste_appearance_style_to_form); setOnClickListener { try { val style = VirtualKeyboardConfigurationLoader.getAppearanceStyleFromClipboard(context); if (style != null) Toast.makeText(context, "样式已应用到表单", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(context, "粘贴失败", Toast.LENGTH_SHORT).show() } } })

        // --- 预览 ---
        addSpacerTo(previewTabContent, 6)
        fun buildSinglePreview(): Pair<FrameLayout, Pair<View, TextView>> {
            val container = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f) }
            val bg = View(context).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
            val txt = TextView(context).apply { val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); lp.gravity = android.view.Gravity.CENTER; layoutParams = lp; text = ""; textSize = 16f; setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; gravity = android.view.Gravity.CENTER }
            container.addView(bg); container.addView(txt); return container to (bg to txt)
        }
        val previewRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val (normalContainer, normalPair) = buildSinglePreview()
        val (pressedContainer, pressedPair) = buildSinglePreview()
        (pressedContainer.layoutParams as LinearLayout.LayoutParams).setMargins(dp(8), 0, 0, 0)
        previewRow.addView(normalContainer); previewRow.addView(pressedContainer); previewTabContent.addView(previewRow)

        // ----------------- 3. 逻辑绑定 -----------------

        // 辅助：应用颜色与透明度
        fun applyAlphaToColor(color: Int, alphaPercent: Int): Int {
            val pct = alphaPercent.coerceIn(0, 100) / 100f
            val baseA = (color ushr 24) and 0xFF
            val newA = (baseA * pct).toInt().coerceIn(0, 255)
            return (newA shl 24) or (color and 0x00FFFFFF)
        }

        // 定义 updatePreview (使用前面已创建的 Views)
        val updatePreview = {
            val normalBase = parseColorSafely(bgColorEdit.text.toString(), 0xF0888888.toInt())
            val pressedBase = parseColorSafely(bgPressedColorEdit.text.toString(), 0xF00000FF.toInt())
            val hasBgNormal = bgColorEdit.text?.isNotBlank() == true
            val hasBgPressed = bgPressedColorEdit.text?.isNotBlank() == true
            val normalFill = if (hasBgNormal) applyAlphaToColor(parseColorSafely(bgColorEdit.text.toString(), normalBase), bgAlphaSeek.progress) else normalBase
            val pressedFill = if (hasBgPressed) applyAlphaToColor(parseColorSafely(bgPressedColorEdit.text.toString(), pressedBase), bgPressedAlphaSeek.progress) else pressedBase

            val borderC0 = parseColorSafely(borderColorEditText.text.toString(), normalFill)
            val borderC = applyAlphaToColor(borderC0, borderAlphaSeek.progress)
            val strokeW = if (borderEnableCheck.isChecked) borderWidthSeek.progress else 0

            normalPair.first.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = radiusSeekBar.progress.toFloat(); setColor(normalFill); if (strokeW > 0) setStroke(strokeW, borderC) else setStroke(0, borderC) }
            pressedPair.first.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = radiusSeekBar.progress.toFloat(); setColor(pressedFill); if (strokeW > 0) setStroke(strokeW, borderC) else setStroke(0, borderC) }

            val txt = buttonTextEditText.text?.toString() ?: ""
            val baseTextColor = parseColorSafely(textColorEdit.text.toString(), Color.WHITE)
            val baseA = (baseTextColor ushr 24) and 0xFF
            val textA = (baseA * (textAlphaSeek.progress.coerceIn(0, 100) / 100f)).toInt().coerceIn(0, 255)
            val composedTextColor = (textA shl 24) or (baseTextColor and 0x00FFFFFF)

            normalPair.second.text = txt; normalPair.second.setTextColor(composedTextColor)
            pressedPair.second.text = txt; pressedPair.second.setTextColor(composedTextColor)

            // 更新色块
            bgColorSwatch.setBackgroundColor(parseColorSafely(bgColorEdit.text.toString(), 0xF0888888.toInt()))
            bgPressedColorSwatch.setBackgroundColor(parseColorSafely(bgPressedColorEdit.text.toString(), 0xF00000FF.toInt()))
            borderColorSwatch.setBackgroundColor(parseColorSafely(borderColorEditText.text.toString(), 0xFF888888.toInt()))
            borderColorSwatchPressed.setBackgroundColor(parseColorSafely(borderColorEditTextPressed.text.toString(), 0xFF888888.toInt()))
            textColorSwatch.setBackgroundColor(parseColorSafely(textColorEdit.text.toString(), Color.WHITE))
            textColorSwatchPressed.setBackgroundColor(parseColorSafely(textColorEditPressed.text.toString(), Color.WHITE))
        }

        // 绑定监听器
        val watchers = listOf(bgColorEdit, bgPressedColorEdit, buttonTextEditText, textColorEdit, borderColorEditText, borderColorEditTextPressed, textColorEditPressed)
        watchers.forEach { it.addTextChangedListener(object : TextWatcher { override fun afterTextChanged(s: Editable?) {}; override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() } }) }

        radiusSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        borderWidthSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        bgAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { bgAlphaLabel.text = "背景透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        bgPressedAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { bgPressedAlphaLabel.text = "背景透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        textAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaText.text = "字体透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        textAlphaSeekPressed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { textAlphaTextPressed.text = "字体透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        borderAlphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { borderAlphaText.text = "描边透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })
        borderAlphaSeekPressed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { borderAlphaTextPressed.text = "描边透明度 $progress"; updatePreview() }; override fun onStartTrackingTouch(seekBar: SeekBar?) {}; override fun onStopTrackingTouch(seekBar: SeekBar?) {} })

        // 绑定 ShowColorPalette 点击事件 (现在 showColorPalette 已定义)
        bgColorSwatch.setOnClickListener { showColorPalette(bgColorEdit, bgColorSwatch) }
        bgPressedColorSwatch.setOnClickListener { showColorPalette(bgPressedColorEdit, bgPressedColorSwatch) }
        borderColorSwatch.setOnClickListener { showColorPalette(borderColorEditText, borderColorSwatch) }
        borderColorSwatchPressed.setOnClickListener { showColorPalette(borderColorEditTextPressed, borderColorSwatchPressed) }
        textColorSwatch.setOnClickListener { showColorPalette(textColorEdit, textColorSwatch) }
        textColorSwatchPressed.setOnClickListener { showColorPalette(textColorEditPressed, textColorSwatchPressed) }

        fun updateAppearanceStateVisibility() {
            val showNormal = !isAppearancePressed
            fun setVisible(v: View, visible: Boolean) { v.visibility = if (visible) View.VISIBLE else View.GONE }
            setVisible(bgRow, showNormal); setVisible(bgPressedRow, !showNormal)
            setVisible(borderRow, showNormal); setVisible(borderRowPressed, !showNormal)
            setVisible(textRow, showNormal); setVisible(textRowPressed, !showNormal)
        }
        updateAppearanceStateVisibility()
        appearanceStateTabs.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) { isAppearancePressed = (tab?.position == 1); updateAppearanceStateVisibility() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // ================== 数据回填 ==================
        if (element != null) {
            buttonIdEditText.setText(element?.elementId.toString())
            buttonTextEditText.setText(element?.text)
            vkCodeEditText.setText(element?.vk_code)
            radiusSeekBar.progress = element?.radius!!.toInt()
            radiusTextView.text = context.getString(R.string.virtual_keyboard_menu_radius_hint) + " " + radiusSeekBar.progress
            if (element?.group != -1) groupEditText.setText(element?.group.toString())

            try {
                val data = element!!.buttonData
                if (data != null) {
                    if (data.has("BORDER_ENABLED")) borderEnableCheck.isChecked = data.getBoolean("BORDER_ENABLED")
                    if (data.has("BORDER_WIDTH_PX")) borderWidthSeek.progress = data.getInt("BORDER_WIDTH_PX").coerceIn(0, 24)
                    if (data.has("BORDER_COLOR")) { val c = data.getInt("BORDER_COLOR"); borderColorEditText.setText(String.format("%08X", c)) }
                    if (data.has("BORDER_ALPHA")) borderAlphaSeek.progress = data.getInt("BORDER_ALPHA").coerceIn(0, 100)
                    if (data.has("TOUCHPAD_SENSITIVITY")) { val v = data.getInt("TOUCHPAD_SENSITIVITY"); touchpadSensitivity.progress = v; touchpadSensitivityText.text = v.toString() }
                    if (data.has("TEXT_COLOR")) { val c = data.getInt("TEXT_COLOR"); textColorEdit.setText(String.format("%08X", c)) }
                    if (data.has("TEXT_ALPHA")) textAlphaSeek.progress = data.getInt("TEXT_ALPHA").coerceIn(0, 100)
                    if (data.has("BG_COLOR")) { val c = data.getInt("BG_COLOR"); bgColorEdit.setText(String.format("%08X", c)) }
                    if (data.has("BG_ALPHA")) bgAlphaSeek.progress = data.getInt("BG_ALPHA").coerceIn(0, 100)
                    if (data.has("BG_COLOR_PRESSED")) { val c = data.getInt("BG_COLOR_PRESSED"); bgPressedColorEdit.setText(String.format("%08X", c)) }
                    if (data.has("BG_ALPHA_PRESSED")) bgPressedAlphaSeek.progress = data.getInt("BG_ALPHA_PRESSED").coerceIn(0, 100)
                }
            } catch (_: Exception) {}
            updatePreview()

            val setJoyStickButton = Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_set_the_handle_arrow_keys); setOnClickListener { setJoyStickVKCodeDialog(context, element!!) }; visibility = if (vkCodeEditText.text.toString() == VirtualKeyboardVkCode.JoyCode.JOY_PAD.code.toString()) View.VISIBLE else View.GONE }
            actionsContainer.addView(setJoyStickButton)
            vkCodeEditText.addTextChangedListener(object : TextWatcher { override fun afterTextChanged(s: Editable?) { setJoyStickButton.visibility = if (s.toString() == VirtualKeyboardVkCode.JoyCode.JOY_PAD.code.toString()) View.VISIBLE else View.GONE }; override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} })

            actionsContainer.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_copy_button); setOnClickListener { VirtualKeyboardConfigurationLoader.copyButton(virtualKeyboard, element, context); game?.postNotification(context.getString(R.string.virtual_keyboard_menu_copy_button) + "\n" + element?.elementId, 2000); VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context) } })
            if (element?.group != -1) { actionsContainer.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_delete_group_button); setOnClickListener { virtualKeyboard.elements.filter { it.group == element?.group }.forEach { virtualKeyboard.removeElementByElement(it) }; VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context) } }) }
            actionsContainer.addView(Button(context).apply { text = context.getString(R.string.virtual_keyboard_menu_macro_edit_button); setOnClickListener { val macroEditor = MacroEditor(context, element!!.buttonData, object : OnMacroDataChangedListener { override fun onMacroDataChanged(newData: JSONObject) { element!!.buttonData = newData; element!!.invalidate(); VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context) } }); macroEditor.setElements(virtualKeyboard.elements); macroEditor.showMacroEditor() } })
        }

        val builder = AlertDialog.Builder(context)
        val dialog: AlertDialog
        if (element != null) {
            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_set_button_title))
                .setView(rootLayout)
                .setCancelable(false)
                .setPositiveButton(R.string.virtual_keyboard_menu_save_button, null)
                .setNeutralButton(R.string.virtual_keyboard_menu_remove_button) { _, _ -> try { virtualKeyboard.removeElementByElement(element); VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context) } catch (e: Exception) { Log.e("vk", "delete failed", e) } }
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .create()
        } else {
            dialog = builder.setTitle(context.getString(R.string.virtual_keyboard_menu_add_button_title))
                .setView(rootLayout)
                .setCancelable(true)
                .setPositiveButton(R.string.virtual_keyboard_menu_confirm_button, null)
                .setNegativeButton(R.string.virtual_keyboard_menu_cancel_button, null)
                .create()
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            try {
                val buttonId = buttonIdEditText.text.toString().toIntOrNull() ?: (virtualKeyboard.lastElementId + 1)
                val vkCode = vkCodeEditText.text.toString().ifEmpty { "0" }
                val buttonText = buttonTextEditText.text.toString()
                val radius = radiusSeekBar.progress.toFloat()
                val group = groupEditText.text.toString().toIntOrNull() ?: -1

                val data = (element?.buttonData ?: JSONObject()).apply {
                    put("BORDER_ENABLED", borderEnableCheck.isChecked)
                    put("BORDER_WIDTH_PX", borderWidthSeek.progress)
                    put("BORDER_COLOR", parseColorSafely(borderColorEditText.text.toString(), 0xFF888888.toInt()))
                    put("BORDER_ALPHA", borderAlphaSeek.progress)
                    put("TEXT_COLOR", parseColorSafely(textColorEdit.text.toString(), Color.WHITE))
                    put("TEXT_ALPHA", textAlphaSeek.progress)
                    put("BG_COLOR", parseColorSafely(bgColorEdit.text.toString(), 0xF0888888.toInt()))
                    put("BG_ALPHA", bgAlphaSeek.progress)
                    put("BG_COLOR_PRESSED", parseColorSafely(bgPressedColorEdit.text.toString(), 0xF00000FF.toInt()))
                    put("BG_ALPHA_PRESSED", bgPressedAlphaSeek.progress)
                    if (selectedButtonType == VirtualKeyboardElement.ButtonType.TouchPad) put("TOUCHPAD_SENSITIVITY", touchpadSensitivity.progress)
                }

                if (element != null) {
                    element!!.apply { elementId = buttonId; text = buttonText; vk_code = vkCode; this.radius = radius; this.group = group; buttonType = selectedButtonType; setButtonData(data); invalidate() }
                } else {
                    VirtualKeyboardConfigurationLoader.addButton(virtualKeyboard, context, buttonId, vkCode, buttonText, selectedButtonType, data)
                }
                VirtualKeyboardConfigurationLoader.saveProfile(virtualKeyboard, context)
                virtualKeyboard.refreshLayout()
                dialog.dismiss()
            } catch (e: Exception) {
                Log.e("vk", "save/add failed", e)
                Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createActionMap(): Map<String, () -> Unit> = createActionMap(null)

    fun createActionMap(dialog: AlertDialog?): Map<String, () -> Unit> {
        val actionMap = mutableMapOf<String, () -> Unit>()
        actionMap[context.getString(R.string.virtual_keyboard_menu_add_button_eg)] = {
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
        }
        actionMap[context.getString(R.string.virtual_keyboard_menu_load_profile)] = {
            val intent = Intent(context, LoadFileActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
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

            dialog.setOnShowListener {
                val window = dialog.window
                val dm = context.resources.displayMetrics
                window?.setLayout((dm.widthPixels * 0.98f).toInt(),
                    (dm.heightPixels * 0.9f).toInt())
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }

            val horizontalScroll = HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isFillViewport = true
            }

            val keyboardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isBaselineAligned = false
            }

            val qwertyRows = listOf(
                listOf(
                    Triple("VK_ESCAPE", 1f, null),
                    Triple("VK_F1", 1f, null), Triple("VK_F2", 1f, null), Triple("VK_F3", 1f, null), Triple("VK_F4", 1f, null),
                    Triple("VK_F5", 1f, null), Triple("VK_F6", 1f, null), Triple("VK_F7", 1f, null), Triple("VK_F8", 1f, null),
                    Triple("VK_F9", 1f, null), Triple("VK_F10", 1f, null), Triple("VK_F11", 1f, null), Triple("VK_F12", 1f, null)
                ),
                listOf(
                    Triple("VK_OEM_3", 1f, null),
                    Triple("VK_1", 1f, null), Triple("VK_2", 1f, null), Triple("VK_3", 1f, null), Triple("VK_4", 1f, null),
                    Triple("VK_5", 1f, null), Triple("VK_6", 1f, null), Triple("VK_7", 1f, null), Triple("VK_8", 1f, null),
                    Triple("VK_9", 1f, null), Triple("VK_0", 1f, null), Triple("VK_OEM_MINUS", 1f, null), Triple("VK_OEM_PLUS", 1f, null),
                    Triple("VK_BACK", 2f, null)
                ),
                listOf(
                    Triple("VK_TAB", 1.5f, null),
                    Triple("VK_Q", 1f, null), Triple("VK_W", 1f, null), Triple("VK_E", 1f, null), Triple("VK_R", 1f, null),
                    Triple("VK_T", 1f, null), Triple("VK_Y", 1f, null), Triple("VK_U", 1f, null), Triple("VK_I", 1f, null),
                    Triple("VK_O", 1f, null), Triple("VK_P", 1f, null), Triple("VK_OEM_4", 1f, null), Triple("VK_OEM_6", 1f, null),
                    Triple("VK_OEM_5", 1.5f, null)
                ),
                listOf(
                    Triple("VK_CAPITAL", 1.8f, null),
                    Triple("VK_A", 1f, null), Triple("VK_S", 1f, null), Triple("VK_D", 1f, null), Triple("VK_F", 1f, null),
                    Triple("VK_G", 1f, null), Triple("VK_H", 1f, null), Triple("VK_J", 1f, null), Triple("VK_K", 1f, null),
                    Triple("VK_L", 1f, null), Triple("VK_OEM_1", 1f, null), Triple("VK_OEM_7", 1f, null),
                    Triple("VK_RETURN", 2.2f, null)
                ),
                listOf(
                    Triple("VK_LSHIFT", 2.2f, null),
                    Triple("VK_Z", 1f, null), Triple("VK_X", 1f, null), Triple("VK_C", 1f, null), Triple("VK_V", 1f, null),
                    Triple("VK_B", 1f, null), Triple("VK_N", 1f, null), Triple("VK_M", 1f, null), Triple("VK_OEM_COMMA", 1f, null),
                    Triple("VK_OEM_PERIOD", 1f, null), Triple("VK_OEM_2", 1f, null),
                    Triple("VK_RSHIFT", 2.2f, null)
                ),
                listOf(
                    Triple("VK_LCONTROL", 1.5f, null), Triple("VK_LWIN", 1.2f, null), Triple("VK_LMENU", 1.2f, null),
                    Triple("VK_SPACE", 7f, null),
                    Triple("VK_RMENU", 1.2f, null), Triple("VK_RWIN", 1.2f, null), Triple("VK_APPS", 1.2f, null), Triple("VK_RCONTROL", 1.5f, null),
                    Triple("VK_LEFT", 1f, null), Triple("VK_UP", 1f, null), Triple("VK_DOWN", 1f, null), Triple("VK_RIGHT", 1f, null)
                )
            )

            val vkMap = VirtualKeyboardVkCode.VKCode.entries.associateBy { it.name }

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