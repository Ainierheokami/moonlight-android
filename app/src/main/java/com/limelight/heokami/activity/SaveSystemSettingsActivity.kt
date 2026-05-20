package com.limelight.heokami.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R
import com.limelight.heokami.FilePickerUtils
import com.limelight.heokami.SystemSettingsBackupHelper

/**
 * 全量配对与设置备份的 Activity。
 * SAF 调起，并将指纹加密后的备份内容保存到指定 URI。
 */
class SaveSystemSettingsActivity : AppCompatActivity() {
    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load_file_activity)
        
        val textView = findViewById<TextView>(R.id.message_text)
        textView.text = "备份系统设置中..."
        
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener { finish() }

        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val defaultName = "Moonlight_System_Backup_$timeStamp.json"

        filePicker = FilePickerUtils(this)
        filePicker.pickFile(
            mimeType = "application/json",
            callback = object : FilePickerUtils.FilePickerCallback {
                override fun onCallBack(fileName: String, content: String, uri: Uri) {
                    try {
                        val backupData = SystemSettingsBackupHelper.exportSystemBackup(this@SaveSystemSettingsActivity)
                        if (backupData != null) {
                            filePicker.saveToUri(uri, backupData)
                            textView.text = "配对与设置备份成功"
                            android.widget.Toast.makeText(this@SaveSystemSettingsActivity, "配对与设置备份成功", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            textView.text = "备份失败：导出生成为空"
                            android.widget.Toast.makeText(this@SaveSystemSettingsActivity, "备份失败，配置生成为空", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("SaveSettingsActivity", "备份发生异常", e)
                        textView.text = "备份失败"
                        android.widget.Toast.makeText(this@SaveSystemSettingsActivity, "备份保存异常，请重试", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    this@SaveSystemSettingsActivity.finish()
                }

                override fun onError(error: String) {
                    Log.e("SaveSettingsActivity", "SAF 备份出错: $error")
                    this@SaveSystemSettingsActivity.finish()
                }
            },
            saveMode = true,
            intentLaunch = (savedInstanceState == null),
            defaultFileName = defaultName
        )
    }
}
