package com.limelight.heokami

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream

import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

class FilePickerUtils(private val activity: AppCompatActivity) {
    companion object {
        const val DEFAULT_DOWNLOADS_FOLDER = "Moonlight"
    }

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
        defaultFileName: String = "File.txt",
        extraMimeTypes: Array<String>? = null
    ) {
        this.callback = callback
        if (intentLaunch) {
            if (saveMode){
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, defaultFileName)
                    
                    // Point SAF to the app's backup folder when the provider supports it.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val initialUri = getDownloadsFolderInitialUri()
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
                    type = mimeType
                    extraMimeTypes?.takeIf { it.isNotEmpty() }?.let {
                        putExtra(Intent.EXTRA_MIME_TYPES, it)
                    }
                    
                    // Point SAF to the app's backup folder when the provider supports it.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val initialUri = getDownloadsFolderInitialUri()
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

    private fun getDownloadsFolderInitialUri(): Uri {
        val encodedPath = "primary%3ADownload%2F$DEFAULT_DOWNLOADS_FOLDER"
        return Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")
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

    fun saveTextToDownloadsFolder(
        fileName: String,
        content: String,
        mimeType: String = "application/json",
        folderName: String = DEFAULT_DOWNLOADS_FOLDER
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveTextToMediaStoreDownloads(fileName, content, mimeType, folderName)
            } else {
                saveTextToLegacyDownloads(fileName, content, mimeType, folderName)
            }
        } catch (e: Exception) {
            Log.e("FilePickerUtils", "自动保存到下载目录失败: ${e.message}", e)
            null
        }
    }

    private fun saveTextToMediaStoreDownloads(
        fileName: String,
        content: String,
        mimeType: String,
        folderName: String
    ): Uri? {
        val resolver = activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            } ?: return null

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveTextToLegacyDownloads(
        fileName: String,
        content: String,
        mimeType: String,
        folderName: String
    ): Uri? {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            folderName
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return null
        }

        val file = File(directory, fileName)
        FileOutputStream(file).use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        }
        MediaScannerConnection.scanFile(activity, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(file)
    }
}
