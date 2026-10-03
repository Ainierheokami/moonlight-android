package com.limelight.heokami.pref

import android.content.Context
import android.content.Intent
import android.preference.Preference
import android.util.AttributeSet
import com.limelight.heokami.activity.EdgeMenuTestActivity

/** Settings row that opens the edge-gesture test mode (a simulated stream screen). */
class EdgeMenuPreviewPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        context.startActivity(Intent(context, EdgeMenuTestActivity::class.java))
    }
}
