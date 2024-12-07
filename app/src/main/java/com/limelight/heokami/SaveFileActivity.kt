package com.limelight.heokami

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.limelight.R
import com.limelight.binding.input.virtual_keyboard.VirtualKeyboardConfigurationLoader

class SaveFileActivity : AppCompatActivity() {

    private lateinit var filePicker: FilePickerUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建一个 AlertDialog
        setContentView(R.layout.load_file_activity)
        val textView = findViewById<TextView>(R.id.message_text)
        textView.text = "保存配置成功"
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener {
            finish()
        }
        filePicker = FilePickerUtils(this)
        filePicker.saveFileUsingPicker(
            fileName = "test.txt",
            content = VirtualKeyboardConfigurationLoader.saveToFile(this),
            mimeType = "text/plain",
            intentLaunch = savedInstanceState == null
        )
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Toast.makeText(this, "Hello, world!", Toast.LENGTH_SHORT).show()
////        setContentView (R.layout.activity_main)
//    }
}