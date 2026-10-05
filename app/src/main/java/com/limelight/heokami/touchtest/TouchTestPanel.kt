package com.limelight.heokami.touchtest

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.limelight.R
import com.limelight.utils.AppToast
import com.limelight.utils.OverlayAlertDialog
import com.limelight.utils.OverlayContainer
import com.limelight.utils.OverlayManager
import kotlin.math.max
import kotlin.math.min

/**
 * Floating log panel of the touch test. It is a regular floating overlay panel, so touches on it
 * are routed exactly like touches on the floating keyboard.
 */
class TouchTestPanel(
    private val activity: Activity,
    private val log: TouchTestLog,
    private val onExit: Runnable
) : TouchTestLog.Listener {
    private val density = activity.resources.displayMetrics.density
    private val enabled = BooleanArray(TouchTestLog.Category.values().size) { true }
    private val chips = ArrayList<TextView>()
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var pauseButton: TextView
    private lateinit var collapseButton: TextView
    private var handle: OverlayContainer.DialogHandle? = null
    private var collapsed = false
    private var leftPx = 0
    private var topPx = 0
    private var widthPx = 0
    private var heightPx = 0

    fun show() {
        val metrics = activity.resources.displayMetrics
        widthPx = (metrics.widthPixels * 0.42f).toInt().coerceAtLeast(dp(260))
        heightPx = (metrics.heightPixels * 0.55f).toInt().coerceAtLeast(dp(200))
        leftPx = metrics.widthPixels - widthPx - dp(12)
        topPx = dp(12)
        root = buildView()
        handle = OverlayManager.getInstance().showOverlay(
            activity, root, widthPx, heightPx, leftPx, topPx, false, null)
        log.addListener(this)
        render()
    }

    fun dismiss() {
        log.removeListener(this)
        handle?.remove()
        handle = null
    }

    override fun onLogChanged() {
        if (!collapsed) {
            render()
        }
    }

    private fun buildView(): LinearLayout {
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor((ContextCompat.getColor(activity, R.color.menu_surface) and 0x00FFFFFF) or 0xE6000000.toInt())
                setStroke(dp(1), ContextCompat.getColor(activity, R.color.menu_surface_pressed))
            }
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            setText(R.string.touch_test_title)
            setTextColor(ContextCompat.getColor(activity, R.color.menu_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        collapseButton = headerButton("−") { toggleCollapsed() }
        header.addView(collapseButton)
        header.addView(headerButton(activity.getString(R.string.touch_test_exit)) { onExit.run() })
        enableDrag(header)
        panel.addView(header)

        body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        pauseButton = actionButton(activity.getString(R.string.touch_test_pause)) { togglePause() }
        actions.addView(pauseButton)
        actions.addView(actionButton(activity.getString(R.string.touch_test_clear)) { log.clear() })
        actions.addView(actionButton(activity.getString(R.string.touch_test_copy)) { copy() })
        actions.addView(actionButton(activity.getString(R.string.touch_test_share)) { share(log.export(enabled)) })
        actions.addView(actionButton(activity.getString(R.string.touch_test_history)) { showHistory() })
        body.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        })

        val chipRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = intArrayOf(
            R.string.touch_test_cat_touch, R.string.touch_test_cat_route, R.string.touch_test_cat_gesture,
            R.string.touch_test_cat_host, R.string.touch_test_cat_key, R.string.touch_test_cat_info)
        TouchTestLog.Category.values().forEachIndexed { index, _ ->
            val chip = actionButton(activity.getString(labels[index])) {
                enabled[index] = !enabled[index]
                updateChips()
                render()
            }
            chips.add(chip)
            chipRow.addView(chip)
        }
        body.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        })
        updateChips()

        logText = TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(ContextCompat.getColor(activity, R.color.menu_text_secondary))
            setTextIsSelectable(false)
        }
        logScroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(logText)
        }
        body.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })
        panel.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return panel
    }

    private fun headerButton(text: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(activity, R.color.menu_text_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    private fun actionButton(text: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(activity, R.color.menu_text_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(dp(9), dp(4), dp(9), dp(4))
        background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(ContextCompat.getColor(activity, R.color.menu_surface_raised))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = dp(4)
            topMargin = dp(4)
        }
        setOnClickListener { onClick() }
    }

    private fun updateChips() {
        chips.forEachIndexed { index, chip ->
            (chip.background as GradientDrawable).setColor(ContextCompat.getColor(activity,
                if (enabled[index]) R.color.menu_accent_tint else R.color.menu_surface_raised))
            chip.setTextColor(ContextCompat.getColor(activity,
                if (enabled[index]) R.color.menu_text_primary else R.color.menu_text_muted))
        }
    }

    private fun render() {
        if (!::logText.isInitialized) return
        val atBottom = !logScroll.canScrollVertically(1)
        val start = log.sessionStart
        val lines = log.snapshot().filter { enabled[it.category.ordinal] }.takeLast(MAX_VISIBLE_LINES)
        logText.text = lines.joinToString("\n") { it.format(start) }
        if (atBottom) {
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun togglePause() {
        val paused = !log.isPaused
        log.isPaused = paused
        pauseButton.setText(if (paused) R.string.touch_test_resume else R.string.touch_test_pause)
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        body.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapseButton.text = if (collapsed) "+" else "−"
        updateLayout()
        if (!collapsed) render()
    }

    private fun updateLayout() {
        handle?.updateLayout(widthPx, if (collapsed) 0 else heightPx, leftPx, topPx)
    }

    private fun enableDrag(header: View) {
        var lastX = 0f
        var lastY = 0f
        header.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val metrics = activity.resources.displayMetrics
                    leftPx = min(max(0, leftPx + (event.rawX - lastX).toInt()), metrics.widthPixels - dp(80))
                    topPx = min(max(0, topPx + (event.rawY - lastY).toInt()), metrics.heightPixels - dp(40))
                    lastX = event.rawX
                    lastY = event.rawY
                    updateLayout()
                    true
                }
                else -> false
            }
        }
    }

    private fun copy() {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("touch test", log.export(enabled)))
        AppToast.makeText(activity, R.string.touch_test_copied, AppToast.LENGTH_SHORT).show()
    }

    private fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.touch_test_share)))
        } catch (_: Exception) {
        }
    }

    private fun showHistory() {
        val files = TouchTestLog.savedSessions(activity)
        if (files.isEmpty()) {
            AppToast.makeText(activity, R.string.touch_test_history_empty, AppToast.LENGTH_SHORT).show()
            return
        }
        val names = files.map { it.name.removePrefix("touch-test-").removeSuffix(".txt") }.toTypedArray()
        OverlayAlertDialog.Builder(activity)
            .setTitle(R.string.touch_test_history)
            .setItems(names) { _, which -> share(TouchTestLog.read(files[which])) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    companion object {
        private const val MAX_VISIBLE_LINES = 300
    }
}
