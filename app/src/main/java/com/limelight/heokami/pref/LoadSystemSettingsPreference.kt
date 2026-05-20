package com.limelight.heokami.pref

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.preference.Preference
import com.limelight.heokami.activity.LoadSystemSettingsActivity

/**
 * 还原系统设置 Preference 选项触发器。
 * 用于调起导入系统设置 Activity，通过系统 SAF 执行导入。
 */
class LoadSystemSettingsPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(context, LoadSystemSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("LoadPreference", "调起 LoadSystemSettingsActivity 异常", e)
        }
    }
}
