package com.limelight.heokami

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

class FilePickerUtils(private val activity: AppCompatActivity) {

    // 回调接口
    interface FilePickerCallback {
        fun onFileSelected(fileName: String, content: String)
        fun onError(error: String)
    }

    private var mode = "r"
    private var fileName = ""
    private var content = ""

    // 文件选择启动器
    private var callback: FilePickerCallback? = null
    private val filePicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                if (mode == "r") {
                    handleSelectedFile(uri)
                }else{
                    saveToUri(uri, content)
                }
            } ?: run {
                callback?.onError("未能获取文件URI")
            }
        }
    }

    // 打开文件选择器
    fun pickFile(mimeType: String = "*/*", callback: FilePickerCallback, intentLaunch: Boolean = true) {
        this.callback = callback
        if (intentLaunch) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
            }
            Log.i("pickFile", "intent created")
            filePicker.launch(intent)
        }
    }

    // 打开文件保存选择器
    fun saveFileUsingPicker(fileName: String, content: String, mimeType: String = "text/plain",intentLaunch: Boolean = true) {
        if (intentLaunch){
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)  // 设置默认文件名
            }
            // 启动保存文件的选择器
            filePicker.launch(intent)
        }
        this.fileName = fileName
        this.content = content
        this.mode = "w"
    }

    // 处理选中的文件
    private fun handleSelectedFile(uri: Uri) {
        try {
            // 获取文件名
            val fileName = getFileName(uri)

            // 读取文件内容
            val content = readFileContent(uri)

            callback?.onFileSelected(fileName, content)
        } catch (e: Exception) {
            callback?.onError("读取文件失败: ${e.message}")
        }
    }

    // 获取文件名
    private fun getFileName(uri: Uri): String {
        var fileName = ""
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    // 读取文件内容
    private fun readFileContent(uri: Uri): String {
        val stringBuilder = StringBuilder()
        activity.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                    stringBuilder.append("\n")
                }
            }
        }
        return stringBuilder.toString()
    }

    // 保存文件内容到用户选择的位置
    fun saveToUri(uri: Uri, content: String): Boolean {
        var outputStream: OutputStream? = null
        try {
            // 获取文件输出流
            outputStream = activity.contentResolver.openOutputStream(uri) ?: return false

            // 写入内容
            outputStream.write(content.toByteArray())
            outputStream.flush()
            return true
        } catch (e: Exception) {
            Log.e("FilePickerUtils", "保存文件失败: ${e.message}")
            return false
        } finally {
            outputStream?.close()
        }
    }
}
