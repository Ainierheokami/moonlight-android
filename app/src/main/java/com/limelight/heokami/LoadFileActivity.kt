package com.limelight.heokami

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader

class LoadFileActivity : AppCompatActivity() {

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
        filePicker.pickFile(mimeType = "text/*", callback = object : FilePickerUtils.FilePickerCallback {
            override fun onFileSelected(fileName: String, content: String) {
                // 处理选中的文件
                Log.d("pickFile", "文件名: $fileName")
                Log.d("pickFile", "文件内容: $content")

                VirtualKeyboardConfigurationLoader.loadForFile(this@LoadFileActivity, content)
            }

            override fun onError(error: String) {
                // 处理错误
                Log.e("pickFile","错误: $error")
            }
        }, intentLaunch = (savedInstanceState == null))
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Toast.makeText(this, "Hello, world!", Toast.LENGTH_SHORT).show()
////        setContentView (R.layout.activity_main)
//    }
}