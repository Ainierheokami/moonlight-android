package com.limelight.heokami

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.limelight.R

class VirtualKeyboardEditorPreviewActivity : Activity() {
    private val surface = Color.parseColor("#FF121212")
    private val panel = Color.parseColor("#FF181818")
    private val stroke = Color.parseColor("#FF323232")
    private val textColor = Color.parseColor("#FFFFFFFF")
    private val muted = Color.parseColor("#FFB8B8B8")
    private val accent = Color.parseColor("#FF707070")

    private lateinit var editorColumn: LinearLayout
    private lateinit var previewPanel: LinearLayout
    private lateinit var tabs: LinearLayout

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, strokeColor: Int? = null, radius: Int = 8) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000")))
        showEditorPreview()
    }

    private fun label(value: String, sp: Float = 13f, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(if (bold) textColor else muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(panel, stroke)
            setPadding(dp(12), dp(9), dp(12), dp(11))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(label(title, 14f, true).apply { setPadding(0, 0, 0, dp(8)) })
        }
    }

    private fun editorButton(value: String, danger: Boolean = false) = Button(this).apply {
        text = value
        setAllCaps(false)
        setTextColor(if (danger) Color.parseColor("#FFFFC9C9") else textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        minHeight = dp(40)
        setBackgroundResource(if (danger) R.drawable.button_background_red_dark else R.drawable.button_background_dark)
    }

    private fun field(value: String) = EditText(this).apply {
        setText(value)
        setTextColor(textColor)
        setHintTextColor(muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    private fun previewButton(value: String, pressed: Boolean): FrameLayout {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                bottomMargin = dp(8)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = bg(panel, stroke)
        }
        frame.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (pressed) Color.parseColor("#FF1A33FF") else Color.parseColor("#FF8A8D88"))
                setStroke(dp(2), Color.parseColor("#FFB8C0CF"))
            }
        })
        frame.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            text = value
            setTextColor(Color.parseColor("#FFE6E6E6"))
            textSize = 18f
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        return frame
    }

    private fun tab(title: String, index: Int) = label(title, 14f, true).apply {
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
            if (index < 2) rightMargin = dp(4)
        }
        setOnClickListener { selectTab(index) }
    }

    private fun selectTab(index: Int) {
        for (i in 0 until tabs.childCount) {
            tabs.getChildAt(i).background = bg(if (i == index) Color.parseColor("#FF303030") else Color.TRANSPARENT, if (i == index) accent else null, 6)
        }
        editorColumn.removeAllViews()
        editorColumn.addView(tabs)
        editorColumn.addView(contentFor(index))
        previewPanel.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun contentFor(index: Int): ScrollView {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        when (index) {
            0 -> {
                content.addView(section("按键身份").apply {
                    addView(LinearLayout(this@VirtualKeyboardEditorPreviewActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(LinearLayout(this@VirtualKeyboardEditorPreviewActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
                            addView(label("按钮唯一ID（数字）"))
                            addView(field("890"))
                        })
                        addView(LinearLayout(this@VirtualKeyboardEditorPreviewActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            addView(label("编组"))
                            addView(field("-1"))
                        })
                    })
                    addView(label("按钮显示的文本").apply { setPadding(0, dp(8), 0, 0) })
                    addView(field("R-Click"))
                })
                content.addView(section("更多操作").apply {
                    addView(editorButton("宏编辑"))
                    addView(editorButton("复制按钮"))
                    addView(editorButton("删除", true))
                })
            }
            1 -> {
                content.addView(section("行为配置").apply {
                    addView(label("按键类型"))
                    addView(field("下次触控右键"))
                    addView(label("右键修饰键默认按住生效，也可勾选为下一次触控点击切换。").apply {
                        setPadding(0, dp(8), 0, 0)
                    })
                })
            }
            else -> {
                content.addView(section("外观配置").apply {
                    addView(label("常态 / 按下"))
                    addView(field("#F0888888"))
                    addView(label("圆角、描边、文字颜色").apply { setPadding(0, dp(8), 0, 0) })
                })
            }
        }
        scroll.addView(content)
        return scroll
    }

    private fun showEditorPreview() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(surface, stroke, 10)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(surface, stroke, 10)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            addView(label("编辑按钮", 18f, true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        val workspace = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }

        editorColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg(Color.parseColor("#FF151515"), stroke, 8)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(tab("基础", 0))
            addView(tab("行为", 1))
            addView(tab("外观", 2))
        }
        workspace.addView(editorColumn)

        previewPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = bg(panel, stroke)
            setPadding(dp(12), dp(10), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                leftMargin = dp(8)
            }
            addView(label("外观页预览", 14f, true).apply { setPadding(0, 0, 0, dp(10)) })
            addView(previewButton("R-Click", false))
            addView(previewButton("R-Click", true))
            addView(label("只在外观调整流程中占用空间", 12f).apply { setPadding(0, dp(4), 0, 0) })
        }
        workspace.addView(previewPanel)
        root.addView(workspace)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg(surface, stroke, 10)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(editorButton("取消").apply { layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(8) } })
            addView(editorButton("保存").apply { layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.2f) })
        })

        setContentView(FrameLayout(this).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.parseColor("#CC000000"))
            addView(root, FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                (resources.displayMetrics.heightPixels * 0.84f).toInt(),
                Gravity.CENTER
            ))
        })
        selectTab(0)
    }
}
