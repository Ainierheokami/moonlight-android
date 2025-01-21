package com.limelight.heokami.pref

import android.content.Context
import android.content.Intent
import android.preference.Preference
import android.util.AttributeSet
import com.limelight.heokami.LoadFileActivity

class LoadFilePreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs) {

    init {
        // 不需要设置 dialogLayoutResource，因为我们不需要自定义对话框布局
    }

    @Deprecated("Deprecated in Java")
    override fun onClick() {
        super.onClick() // 这句很重要，调用父类的点击事件，以正确关闭对话框
        val intent = Intent(context, LoadFileActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }
}