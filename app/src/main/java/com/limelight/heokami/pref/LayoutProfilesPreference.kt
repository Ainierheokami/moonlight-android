package com.limelight.heokami.pref

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.preference.Preference
import android.util.AttributeSet
import com.limelight.heokami.layout.LayoutProfileDialogs

/** Settings row that opens the keyboard layout manager (outside a stream). */
class LayoutProfilesPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        var ctx: Context? = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        (ctx as? Activity)?.let { LayoutProfileDialogs.show(it, null) }
    }
}
