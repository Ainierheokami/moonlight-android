package com.limelight.heokami.activity

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.FrameLayout
import com.limelight.R
import com.limelight.heokami.EdgeSwipeDetector
import com.limelight.heokami.EdgeSwipeDetector.MoveResult
import com.limelight.preferences.PreferenceConfiguration

/**
 * Full-screen test mode for the edge-swipe menu gesture.
 *
 * It runs the same [EdgeSwipeDetector] as the stream, fed from an Activity-level
 * dispatchTouchEvent just like Game, with the same immersive window, the same system
 * back-gesture exclusion strips and the same "second finger abandons the gesture" rule. It also
 * tells you why a swipe did not open the menu. The menu itself is drawn as an outline for now.
 */
class EdgeMenuTestActivity : Activity() {
    private val detector = EdgeSwipeDetector()
    private lateinit var testView: EdgeMenuTestView
    private var hotZoneDp = PreferenceConfiguration.DEFAULT_EDGE_MENU_HOT_ZONE_DP
    private var thresholdDp = PreferenceConfiguration.DEFAULT_EDGE_MENU_SWIPE_THRESHOLD_DP
    private var backCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceConfiguration.readPreferences(this)
        hotZoneDp = prefs.edgeMenuHotZoneDp
        thresholdDp = prefs.edgeMenuSwipeThresholdDp

        testView = EdgeMenuTestView(this, hotZoneDp, thresholdDp, prefs.enableGameMenuGestureWake)
        val exit = Button(this).apply {
            setText(R.string.edge_test_exit)
            setAllCaps(false)
            setOnClickListener { finish() }
        }
        val root = FrameLayout(this)
        root.addView(testView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(exit, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(24) })
        setContentView(root)
        hideSystemUi()

        // The stream ignores the system back gesture while edge wake is enabled, so a back event
        // here means the system stole the swipe. Report it instead of closing the test.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { testView.onSystemBack() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        testView.onSystemBack()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
            applySystemGestureExclusion()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        // Same flags Game uses while streaming.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun exclusionWidthPx(viewWidth: Int): Int =
        EdgeSwipeDetector.systemGestureExclusionWidthPx(dp(hotZoneDp), dp(EdgeSwipeDetector.SYSTEM_GESTURE_EXCLUSION_MIN_DP), viewWidth)

    private fun applySystemGestureExclusion() {
        val decor = window.decorView
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            testView.setExclusionWidthPx(-1)
            return
        }
        decor.post {
            val width = decor.width
            val height = decor.height
            if (width <= 0 || height <= 0) return@post
            val edge = exclusionWidthPx(width)
            decor.systemGestureExclusionRects = listOf(
                Rect(0, 0, minOf(edge, width), height),
                Rect(maxOf(0, width - edge), 0, width, height)
            )
            testView.setExclusionWidthPx(edge)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return if (handleGesture(event)) true else super.dispatchTouchEvent(event)
    }

    private fun handleGesture(event: MotionEvent): Boolean {
        val width = window.decorView.width
        if (width <= 0) return false

        val hotZonePx = dp(hotZoneDp)
        val thresholdPx = dp(thresholdDp)
        detector.configure(hotZonePx, thresholdPx,
            EdgeSwipeDetector.intentThresholdFor(thresholdPx, dp(EdgeSwipeDetector.INTENT_THRESHOLD_CAP_DP)))

        if ((detector.isCandidate || detector.isConsuming) && event.pointerCount != 1) {
            val wasConsuming = detector.cancelForMultiTouch()
            testView.onGestureEnd(null, detector.lastMiss, 0f)
            return wasConsuming
        }
        if (event.pointerCount != 1) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val candidate = detector.down(event.x, event.y, width)
                testView.onGestureStart(event.x, event.y, candidate)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val result = detector.move(event.x, event.y)
                testView.onGestureMove(event.x, event.y, detector, result)
                return when (result) {
                    MoveResult.NOT_TRACKING, MoveResult.PENDING -> false
                    else -> true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val side = if (detector.isTriggered) detector.startSide else null
                val remainingDp = detector.remainingPx() / resources.displayMetrics.density
                val wasConsuming = detector.up()
                testView.onGestureEnd(side, detector.lastMiss, remainingDp)
                return wasConsuming
            }
        }
        return detector.isConsuming
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
