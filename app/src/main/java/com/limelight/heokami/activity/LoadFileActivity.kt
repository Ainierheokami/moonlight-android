package com.limelight.heokami.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.limelight.PcView
import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader
import com.limelight.heokami.FilePickerUtils

class LoadFileActivity : AppCompatActivity() {

    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load_file_activity)
        val textView = findViewById<TextView>(R.id.message_text)
        textView.text = "加载配置成功"
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener {
            finish()
        }
        filePicker = FilePickerUtils(this)
        filePicker.pickFile(mimeType = "*/*", callback = object :
            FilePickerUtils.FilePickerCallback {
            override fun onCallBack(fileName: String, content: String, uri: Uri) {
                try {
                    Log.d("pickFile", "文件名: $fileName")
                    Log.d("pickFile", "文件内容: $content")

                    VirtualKeyboardConfigurationLoader.loadForFile(this@LoadFileActivity, content)
                    Toast.makeText(this@LoadFileActivity, "加载配置成功，应用即将自动重启...", Toast.LENGTH_SHORT).show()
                    // 延迟 800ms 后自动重启应用，确保 SP 写入完成
                    restartApp()
                } catch (e: Exception) {
                    Log.e("pickFile", "导入按键配置异常", e)
                    Toast.makeText(this@LoadFileActivity, "配置格式破损，无法导入虚拟键盘布局", Toast.LENGTH_LONG).show()
                    this@LoadFileActivity.finish()
                }
            }

            override fun onError(error: String) {
                Log.e("pickFile","错误: $error")
                this@LoadFileActivity.finish()
            }
        },saveMode = false, intentLaunch = (savedInstanceState == null))
    }

    /**
     * 自动重启应用：通过 CLEAR_TASK + NEW_TASK 标志位彻底销毁当前 Activity 栈，
     * 重新启动主界面 PcView，使所有导入的虚拟键盘配置即时生效。
     */
    private fun restartApp() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@LoadFileActivity, PcView::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }, 800)
    }
}
