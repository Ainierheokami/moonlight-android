package com.limelight.heokami.pref

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import com.limelight.Game

/**
 * Settings row that opens the touch test: the real stream screen (menu gesture, floating and
 * virtual keyboards, touch modes) with no host, logging every input instead of sending it.
 */
class EdgeMenuPreviewPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        context.startActivity(Game.createTouchTestIntent(context))
    }
}
