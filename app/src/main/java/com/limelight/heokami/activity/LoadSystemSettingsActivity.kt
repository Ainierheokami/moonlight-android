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
import com.limelight.heokami.WrongPasswordException
import com.limelight.utils.AppExecutors

/**
 * 全量配对与设置还原的 Activity。
 * SAF 调起；备份含加密配对凭据时要求输入密码（验证通过前不改动任何现有数据），也可选择只还原设置。
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
                    val info = try {
                        SystemSettingsBackupHelper.inspectBackup(content)
                    } catch (e: Exception) {
                        Log.e("LoadSettingsActivity", "设置导入发生异常", e)
                        showInvalidFile(textView)
                        return
                    }
                    if (info.needsPassword) {
                        promptPassword(content, textView, wrongPassword = false)
                    } else {
                        restore(content, null, textView)
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

    private fun promptPassword(content: String, textView: TextView, wrongPassword: Boolean) {
        BackupPasswordDialog.askExistingPassword(
            this,
            wrongPassword,
            onPassword = { password -> restore(content, password, textView) },
            onSkip = { restore(content, null, textView) },
            onCancel = { finish() }
        )
    }

    /** [password] == null restores settings and the PC list only. Runs off the main thread (PBKDF2). */
    private fun restore(content: String, password: CharArray?, textView: TextView) {
        textView.setText(R.string.restore_in_progress)
        AppExecutors.execute {
            var result: SystemSettingsBackupHelper.RestoreResult? = null
            var wrongPassword = false
            try {
                result = SystemSettingsBackupHelper.importSystemBackup(this, content, password)
            } catch (e: WrongPasswordException) {
                wrongPassword = true
            } catch (e: Exception) {
                Log.e("LoadSettingsActivity", "设置导入发生异常", e)
            } finally {
                password?.fill('\u0000')
            }
            val restored = result
            val badPassword = wrongPassword
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    badPassword -> promptPassword(content, textView, wrongPassword = true)
                    restored == null -> showInvalidFile(textView)
                    else -> showRestored(restored, textView)
                }
            }
        }
    }

    private fun showRestored(result: SystemSettingsBackupHelper.RestoreResult, textView: TextView) {
        val full = result == SystemSettingsBackupHelper.RestoreResult.CREDENTIALS_RESTORED
        textView.setText(if (full) R.string.restore_credentials_success else R.string.restore_settings_only_success)
        AppToast.makeText(
            this,
            getString(if (full) R.string.restore_credentials_toast else R.string.restore_settings_only_toast),
            AppToast.LENGTH_LONG
        ).show()
        // 给应用内提示留出可读和复制调试信息的时间
        restartApp()
    }

    private fun showInvalidFile(textView: TextView) {
        textView.setText(R.string.restore_failed)
        AppToast.makeText(this, getString(R.string.restore_invalid_file), AppToast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
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
