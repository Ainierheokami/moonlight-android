package com.limelight.heokami.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R
import com.limelight.heokami.FilePickerUtils
import com.limelight.heokami.SystemSettingsBackupHelper

/**
 * 全量配对与设置还原的 Activity。
 * SAF 调起，并在回调中对数据流进行解密与恢复，支持异机自适应安全降级提示。
 */
class LoadSystemSettingsActivity : AppCompatActivity() {
    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load_file_activity)
        
        val textView = findViewById<TextView>(R.id.message_text)
        textView.text = "正在导入系统设置..."
        
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener { finish() }

        filePicker = FilePickerUtils(this)
        filePicker.pickFile(
            mimeType = "text/*",
            callback = object : FilePickerUtils.FilePickerCallback {
                override fun onCallBack(fileName: String, content: String, uri: Uri) {
                    try {
                        val result = SystemSettingsBackupHelper.importSystemBackup(this@LoadSystemSettingsActivity, content)
                        if (result == 1) {
                            textView.text = "同设备全量恢复成功"
                            Toast.makeText(
                                this@LoadSystemSettingsActivity,
                                "配对凭据与系统设置已完美全量恢复！",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            textView.text = "跨机安全降级恢复成功"
                            Toast.makeText(
                                this@LoadSystemSettingsActivity,
                                "跨设备导入成功！设置与电脑列表已恢复。为防止局域网设备冲突，安全凭证已进行降级隔离，您只需重新点击配对电脑即可使用！",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("LoadSettingsActivity", "设置导入发生异常", e)
                        textView.text = "设置恢复失败"
                        Toast.makeText(
                            this@LoadSystemSettingsActivity,
                            "配置文件损坏或非系统备份文件，无法恢复设置",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    this@LoadSystemSettingsActivity.finish()
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
}
