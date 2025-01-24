package com.limelight.heokami.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader
import com.limelight.heokami.FilePickerUtils

class LoadFileActivityAdd : AppCompatActivity() {

    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建一个 AlertDialog
        setContentView(R.layout.load_file_activity)
        val textView = findViewById<TextView>(R.id.message_text)
        textView.text = "加载配置成功"
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener {
            finish()
        }
        filePicker = FilePickerUtils(this)
        filePicker.pickFile(mimeType = "text/*", callback = object :
            FilePickerUtils.FilePickerCallback {
            override fun onCallBack(fileName: String, content: String, uri: Uri) {
                // 处理选中的文件
                Log.d("pickFile", "文件名: $fileName")
                Log.d("pickFile", "文件内容: $content")

                VirtualKeyboardConfigurationLoader.loadForFileAdd(this@LoadFileActivityAdd, content)
                this@LoadFileActivityAdd.finish()
            }

            override fun onError(error: String) {
                // 处理错误
                Log.e("pickFile","错误: $error")
                this@LoadFileActivityAdd.finish()
            }
        },saveMode = false, intentLaunch = (savedInstanceState == null))
    }
}