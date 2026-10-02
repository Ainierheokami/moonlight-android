package com.limelight.heokami.activity

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R
import com.limelight.heokami.FilePickerUtils
import com.limelight.heokami.SystemSettingsBackupHelper
import com.limelight.utils.AppExecutors
import com.limelight.utils.AppToast

/**
 * 全量配对与设置备份的 Activity。
 * 先询问备份密码（留空则只备份设置），再导出并保存到 Download 或用户指定的 URI。
 */
class SaveSystemSettingsActivity : AppCompatActivity() {
    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load_file_activity)
        
        val textView = findViewById<TextView>(R.id.message_text)
        textView.setText(R.string.backup_in_progress)
        
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener { finish() }

        filePicker = FilePickerUtils(this)
        if (savedInstanceState != null) {
            // Activity was recreated while the password prompt / picker was up; start over.
            finish()
            return
        }

        BackupPasswordDialog.askNewPassword(
            this,
            onResult = { password -> exportInBackground(password, textView) },
            onCancel = { finish() }
        )
    }

    private fun exportInBackground(password: CharArray, textView: TextView) {
        textView.setText(R.string.backup_in_progress)
        AppExecutors.execute {
            val result = try {
                SystemSettingsBackupHelper.exportSystemBackup(this, password)
            } catch (e: Exception) {
                Log.e("SaveSettingsActivity", "备份发生异常", e)
                null
            } finally {
                password.fill('\u0000')
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                saveBackup(result, textView)
            }
        }
    }

    private fun saveBackup(backupData: String?, textView: TextView) {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val defaultName = "Moonlight_System_Backup_$timeStamp.json"

        if (backupData == null) {
            textView.setText(R.string.backup_failed_empty_export)
            AppToast.makeText(this, this@SaveSystemSettingsActivity.getString(R.string.backup_failed_empty_config), AppToast.LENGTH_SHORT).show()
            finishAfterToast()
            return
        }

        val savedUri = filePicker.saveTextToDownloadsFolder(defaultName, backupData)
        if (savedUri != null) {
            Log.d("SaveSettingsActivity", "自动保存系统设置到: $savedUri")
            textView.setText(R.string.backup_success)
            AppToast.makeText(this, this@SaveSystemSettingsActivity.getString(R.string.backup_saved_to_download), AppToast.LENGTH_SHORT).show()
            finishAfterToast()
            return
        }

        filePicker.pickFile(
            mimeType = "application/json",
            callback = object : FilePickerUtils.FilePickerCallback {
                override fun onCallBack(fileName: String, content: String, uri: Uri) {
                    try {
                        filePicker.saveToUri(uri, backupData)
                        textView.setText(R.string.backup_success)
                        AppToast.makeText(this@SaveSystemSettingsActivity, this@SaveSystemSettingsActivity.getString(R.string.backup_success), AppToast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("SaveSettingsActivity", "备份发生异常", e)
                        textView.setText(R.string.backup_failed)
                        AppToast.makeText(this@SaveSystemSettingsActivity, this@SaveSystemSettingsActivity.getString(R.string.backup_save_error_retry), AppToast.LENGTH_SHORT).show()
                    }
                    finishAfterToast()
                }

                override fun onError(error: String) {
                    Log.e("SaveSettingsActivity", "SAF 备份出错: $error")
                    this@SaveSystemSettingsActivity.finish()
                }
            },
            saveMode = true,
            intentLaunch = true,
            defaultFileName = defaultName
        )
    }

    private fun finishAfterToast() {
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1800)
    }
}
