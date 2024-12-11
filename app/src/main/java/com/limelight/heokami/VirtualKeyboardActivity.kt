package com.limelight.heokami

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VirtualKeyboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(layout)

        VirtualKeyboardVkCode.VKCode.entries.forEach { vkCode ->
            val button = Button(this).apply {
                text = vkCode.name
                setOnClickListener {
                    Toast.makeText(this@VirtualKeyboardActivity, "Code: ${vkCode.code}", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(button)
        }
    }
}
