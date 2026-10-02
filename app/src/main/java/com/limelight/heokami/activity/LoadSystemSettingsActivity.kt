package com.limelight.heokami.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.limelight.utils.AppToast
import androidx.appcompat.app.AppCompatActivity
import com.limelight.PcView
import com.limelight.R
import com.limelight.heokami.FilePickerUtils
import com.limelight.heokami.SystemSettingsBackupHelper

/**
 * 全量配对与设置还原的 Activity。
 * SAF 调起，并在回调中对数据流进行解密与恢复，支持异机自适应安全降级提示。
 * 导入成功后自动重启应用以确保所有 Preference 变更即时生效。
 */
class LoadSystemSettingsActivity : AppCompatActivity() {
    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load_file_activity)
        
        val textView = findViewById<TextView>(R.id.message_text)
        textView.setText(R.string.restore_in_progress)
        
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener { finish() }

        filePicker = FilePickerUtils(this)
        filePicker.pickFile(
            mimeType = "*/*",
            callback = object : FilePickerUtils.FilePickerCallback {
                override fun onCallBack(fileName: String, content: String, uri: Uri) {
                    try {
                        val result = SystemSettingsBackupHelper.importSystemBackup(this@LoadSystemSettingsActivity, content)
                        if (result == 1) {
                            textView.setText(R.string.restore_same_device_success)
                            AppToast.makeText(
                                this@LoadSystemSettingsActivity,
                                this@LoadSystemSettingsActivity.getString(R.string.restore_same_device_toast),
                                AppToast.LENGTH_LONG
                            ).show()
                        } else {
                            textView.setText(R.string.restore_cross_device_success)
                            AppToast.makeText(
                                this@LoadSystemSettingsActivity,
                                this@LoadSystemSettingsActivity.getString(R.string.restore_cross_device_toast),
                                AppToast.LENGTH_LONG
                            ).show()
                        }
                        // 给应用内提示留出可读和复制调试信息的时间
                        restartApp()
                    } catch (e: Exception) {
                        Log.e("LoadSettingsActivity", "设置导入发生异常", e)
                        textView.setText(R.string.restore_failed)
                        AppToast.makeText(
                            this@LoadSystemSettingsActivity,
                            this@LoadSystemSettingsActivity.getString(R.string.restore_invalid_file),
                            AppToast.LENGTH_LONG
                        ).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            this@LoadSystemSettingsActivity.finish()
                        }, 3000)
                    }
                }

                override fun onError(error: String) {
                    Log.e("LoadSettingsActivity", "SAF 导入出错: $error")
                    this@LoadSystemSettingsActivity.finish()
                }
            },
            saveMode = false,
            intentLaunch = (savedInstanceState == null)
        )
    }

    /**
     * 自动重启应用：通过 CLEAR_TASK + NEW_TASK 标志位彻底销毁当前 Activity 栈，
     * 然后重新启动主界面 PcView，使所有导入的 Preference 变更即时生效，
     * 用户无需手动杀进程重启。
     */
    private fun restartApp() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@LoadSystemSettingsActivity, PcView::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }, 3000)
    }
}
