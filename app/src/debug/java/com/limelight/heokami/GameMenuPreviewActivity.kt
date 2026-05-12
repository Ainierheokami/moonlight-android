package com.limelight.heokami

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.limelight.R

class GameMenuPreviewActivity : Activity() {
    private val surface = Color.parseColor("#FF121212")
    private val panel = Color.parseColor("#FF181818")
    private val stroke = Color.parseColor("#FF323232")
    private val textColor = Color.parseColor("#FFFFFFFF")
    private val muted = Color.parseColor("#FFB8B8B8")
    private val accent = Color.parseColor("#FFE6E6E6")

    private lateinit var touchSheet: LinearLayout
    private var touchMode = 2

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, strokeColor: Int? = stroke, radius: Int = 16) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun label(value: String, sp: Float = 13f, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(if (bold) textColor else muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun actionButton(value: String, danger: Boolean = false) = Button(this).apply {
        text = value
        setAllCaps(false)
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(10), 0, dp(8), 0)
        maxLines = 2
        setBackgroundResource(if (danger) R.drawable.button_background_red_dark else R.drawable.button_background_dark)
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(42)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
        render()
    }

    private fun statusChip(label: String, value: String) = TextView(this).apply {
        text = "$label\n$value"
        setTextColor(Color.parseColor("#FFE6E6E6"))
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        background = bg(panel)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
    }

    private fun addSection(parent: LinearLayout, title: String, actions: List<String>) {
        parent.addView(label(title, 13f, true).apply { setPadding(dp(4), dp(8), dp(4), dp(4)) })
        parent.addView(GridLayout(this).apply {
            columnCount = 2
            actions.forEach { addView(actionButton(it)) }
        })
    }

    private fun touchModeName(): String {
        return when (touchMode) {
            0 -> "多点触摸"
            1 -> "触控板"
            else -> "鼠标"
        }
    }

    private fun renderTouchSheet() {
        touchSheet.removeAllViews()
        touchSheet.addView(label("当前：${touchModeName()}", 14f, true).apply {
            setPadding(0, 0, 0, dp(8))
        })
        touchSheet.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("多点触摸", "触控板", "鼠标").forEachIndexed { index, name ->
                addView(Button(this@GameMenuPreviewActivity).apply {
                    text = name
                    setAllCaps(false)
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    background = bg(if (touchMode == index) Color.parseColor("#FF303030") else Color.parseColor("#FF202020"), if (touchMode == index) Color.parseColor("#FF707070") else stroke, 8)
                    setOnClickListener {
                        touchMode = index
                        renderTouchSheet()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }
                })
            }
        })
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(surface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(Color.parseColor("#FF151515"))
            setPadding(dp(14), dp(6), dp(10), dp(6))
            addView(label("菜单", 18f, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(label("×", 24f, true).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)) })
        })

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(8))
            addView(statusChip("触控", touchModeName()))
            addView(statusChip("虚拟键盘", "就绪"))
            addView(statusChip("叠加", "快捷"))
            addView(statusChip("传送门", "开"))
        })

        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(LinearLayout(this@GameMenuPreviewActivity).apply {
                orientation = LinearLayout.VERTICAL
                addSection(this, "输入控制", listOf("开启输入法", "悬浮键盘", "全屏键盘", "发送剪贴板"))
                addSection(this, "常用热键", listOf("复制", "粘贴", "虚拟键盘", "切换窗口", "返回桌面"))
                addSection(this, "屏幕叠加", listOf("虚拟手柄", "屏幕虚拟键盘", "编辑虚拟键盘", "性能叠加"))
                addSection(this, "传送门管理", listOf("关闭传送门", "添加传送门", "切换编辑模式", "管理传送门"))
            })
        })

        touchSheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = bg(panel)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        renderTouchSheet()
        root.addView(touchSheet)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(Button(this@GameMenuPreviewActivity).apply {
                text = "触摸模式"
                setAllCaps(false)
                setTextColor(textColor)
                setBackgroundResource(R.drawable.button_background_dark)
                setOnClickListener { touchSheet.visibility = if (touchSheet.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) }
            })
            addView(Button(this@GameMenuPreviewActivity).apply {
                text = "断开连接"
                setAllCaps(false)
                setTextColor(textColor)
                setBackgroundResource(R.drawable.button_background_red_dark)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            })
        })

        setContentView(FrameLayout(this).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.parseColor("#CC000000"))
            addView(root, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.46f).toInt().coerceAtLeast(dp(440)),
                (resources.displayMetrics.heightPixels * 0.82f).toInt(),
                Gravity.CENTER or Gravity.RIGHT
            ))
        })
    }
}
