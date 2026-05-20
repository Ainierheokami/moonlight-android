package com.limelight.heokami.pref

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.preference.Preference
import com.limelight.heokami.activity.SaveSystemSettingsActivity

/**
 * 备份系统设置 Preference 选项触发器。
 * 用于调起保存系统设置 Activity，通过系统 SAF 执行导出。
 */
class SaveSystemSettingsPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick()
        try {
            val intent = Intent(context, SaveSystemSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SavePreference", "调起 SaveSystemSettingsActivity 异常", e)
        }
    }
}
