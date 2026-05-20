package com.limelight.heokami

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

import android.os.Build
import android.provider.DocumentsContract

class FilePickerUtils(private val activity: AppCompatActivity) {

    // 回调接口
    interface FilePickerCallback {
        fun onCallBack(fileName: String, content: String, uri: Uri)
        fun onError(error: String)
    }

    // 文件选择启动器
    private var callback: FilePickerCallback? = null
    private val filePicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleUri(uri)
            } ?: run {
                callback?.onError("未能获取文件URI")
            }
        } else {
            callback?.onError("文件选择取消")
        }
    }

    // 打开文件选择器
    fun pickFile(
        mimeType: String = "*/*",
        callback: FilePickerCallback,
        saveMode: Boolean = false,
        intentLaunch: Boolean = true,
        defaultFileName: String = "File.txt"
    ) {
        this.callback = callback
        if (intentLaunch) {
            if (saveMode){
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, defaultFileName)
                    
                    // 🌟 首席架构师特调：自动指引初始选择器定位到系统的 Download (下载) 目录，方便查找和归类
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                        } catch (e: Exception) {
                            Log.w("FilePickerUtils", "设置初始SAF路径失败: ${e.message}")
                        }
                    }
                }
                filePicker.launch(intent)
            } else{
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    // 🌟 首席架构师特调高兼容导入方案：
                    // 使用 */* 兜底并通过 EXTRA_MIME_TYPES 同时申明对 .txt 和 .json 的支持，
                    // 彻底解决某些 Android 定制 ROM 将 .json 文件错误识别为 binary 并予以置灰无法选择的问题。
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/json", "text/*"))
                    
                    // 🌟 导入时同样自动指引定位到系统的 Download 目录
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                        } catch (e: Exception) {
                            Log.w("FilePickerUtils", "设置初始SAF路径失败: ${e.message}")
                        }
                    }
                }
                filePicker.launch(intent)
            }
            Log.i("pickFile", "intent created")
        }
    }

    // 处理选中的文件
    private fun handleUri(uri: Uri) {
        try {
            // 获取文件名
            val fileName = getFileName(uri)
            // 读取文件内容
            val content = readFileContent(uri)
            callback?.onCallBack(fileName, content, uri)
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
