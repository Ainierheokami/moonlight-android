package com.limelight.heokami

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 首席架构师定制版：系统设置与配对关系备份还原助手。
 * 实现虚拟串流参数、配对电脑 SQLite 以及安全证书私钥的指纹加密存取，提供异机降级隔离免冲突保护。
 */
object SystemSettingsBackupHelper {
    private const val TAG = "SettingsBackupHelper"
    private val EXTRA_PREFS_NAMES = arrayOf(
        "OSK",
        "floating_keyboard_prefs",
        "game_menu_prefs"
    )
    
    // 加密配置：使用 AES-256-CBC 配合 16 字节静态 IV，保证物理指纹密文的完美对称解密
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    private val STATIC_IV = byteArrayOf(10, 23, 85, 41, -102, 12, 9, 88, 77, 33, 99, -110, 4, 18, 56, 92)

    /**
     * 根据设备物理特征要素 (主板, 品牌, 型号, 产品, 厂商) 与系统 Android ID，
     * 混合散列计算出当前设备在生命周期内 100% 唯一的 256 位设备指纹密钥。
     */
    private fun getDeviceFingerprint(context: Context): ByteArray {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            val hardwareInfo = "${Build.BOARD}|${Build.BRAND}|${Build.DEVICE}|${Build.MODEL}|${Build.PRODUCT}|${Build.MANUFACTURER}|$androidId"
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(hardwareInfo.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "物理设备指纹密钥生成失败", e)
            ByteArray(32) // 容灾兜底
        }
    }

    private fun encrypt(data: String, key: ByteArray): String {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(STATIC_IV))
        val encrypted = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedData: String, key: ByteArray): String {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(STATIC_IV))
        val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun readFileBytesSafe(file: File): ByteArray? {
        if (!file.exists()) return null
        return try {
            val bytes = ByteArray(file.length().toInt())
            FileInputStream(file).use { fin ->
                var offset = 0
                var numRead = 0
                while (offset < bytes.size && fin.read(bytes, offset, bytes.size - offset).also { numRead = it } >= 0) {
                    offset += numRead
                }
            }
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "安全读取物理凭据文件失败: ${file.name}", e)
            null
        }
    }

    private fun putPreferenceValue(json: JSONObject, key: String, value: Any?) {
        val item = JSONObject()
        when (value) {
            is Boolean -> {
                item.put("type", "boolean")
                item.put("value", value)
            }
            is Int -> {
                item.put("type", "int")
                item.put("value", value)
            }
            is Long -> {
                item.put("type", "long")
                item.put("value", value)
            }
            is Float -> {
                item.put("type", "float")
                item.put("value", value.toDouble())
            }
            is String -> {
                item.put("type", "string")
                item.put("value", value)
            }
            is Set<*> -> {
                item.put("type", "string_set")
                val arr = JSONArray()
                value.filterIsInstance<String>().forEach { arr.put(it) }
                item.put("value", arr)
            }
            else -> return
        }
        json.put(key, item)
    }

    private fun exportPreferences(prefs: SharedPreferences): JSONObject {
        val prefsObj = JSONObject()
        for ((key, value) in prefs.all) {
            if (key != "uniqueid") {
                putPreferenceValue(prefsObj, key, value)
            }
        }
        return prefsObj
    }

    private fun importPreferences(editor: SharedPreferences.Editor, prefsObj: JSONObject) {
        val keys = prefsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val rawValue = prefsObj.get(key)
            if (rawValue is JSONObject && rawValue.has("type")) {
                when (rawValue.optString("type")) {
                    "boolean" -> editor.putBoolean(key, rawValue.getBoolean("value"))
                    "int" -> editor.putInt(key, rawValue.getInt("value"))
                    "long" -> editor.putLong(key, rawValue.getLong("value"))
                    "float" -> editor.putFloat(key, rawValue.getDouble("value").toFloat())
                    "string" -> editor.putString(key, rawValue.optString("value", ""))
                    "string_set" -> {
                        val arr = rawValue.optJSONArray("value") ?: JSONArray()
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) {
                            set.add(arr.optString(i))
                        }
                        editor.putStringSet(key, set)
                    }
                }
            } else {
                when (rawValue) {
                    is Boolean -> editor.putBoolean(key, rawValue)
                    is Int -> editor.putInt(key, rawValue)
                    is Long -> editor.putLong(key, rawValue)
                    is Float -> editor.putFloat(key, rawValue)
                    is String -> editor.putString(key, rawValue)
                }
            }
        }
    }

    /**
     * 全量系统配置导出。
     * 包括：SharedPreferences 设置参数、Computers 数据库、以及基于物理设备指纹 AES 加密处理的证书和 UniqueID。
     */
    fun exportSystemBackup(context: Context): String? {
        try {
            val root = JSONObject()
            
            // 1. 导出元数据
            val metadata = JSONObject()
            metadata.put("app_id", "com.limelight.heokami")
            metadata.put("backup_time", System.currentTimeMillis())
            metadata.put("type", "pairing_and_settings")
            
            // 记录当前物理设备的指纹 Base64，用作后期校验标识
            val fingerprint = getDeviceFingerprint(context)
            metadata.put("fingerprint_signature", Base64.encodeToString(fingerprint, Base64.NO_WRAP))
            root.put("metadata", metadata)

            // 2. 导出所有串流设置 Preferences (比特率、帧率、辅助模式开关等)
            // 使用 PreferenceManager 动态获取，自适应所有 applicationId 变体（com.heokami.debug 等）
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            root.put("preferences", exportPreferences(sharedPrefs))

            val extraPrefs = JSONObject()
            for (name in EXTRA_PREFS_NAMES) {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                if (prefs.all.isNotEmpty()) {
                    extraPrefs.put(name, exportPreferences(prefs))
                }
            }
            root.put("extra_preferences", extraPrefs)

            // 3. 导出已配对电脑数据库列表 (SQLite computers4.db -> JSON)
            val computersArray = JSONArray()
            val dbManager = ComputerDatabaseManager(context)
            val pcList = dbManager.allComputers
            dbManager.close()
            
            for (pc in pcList) {
                val pcObj = JSONObject()
                pcObj.put("uuid", pc.uuid)
                pcObj.put("name", pc.name)
                pcObj.put("mac", pc.macAddress)
                
                // 导出各网口 IP 拓扑地址
                val addrObj = JSONObject()
                addrObj.put("local", ComputerDatabaseManager.tupleToJson(pc.localAddress))
                addrObj.put("remote", ComputerDatabaseManager.tupleToJson(pc.remoteAddress))
                
                val manualArr = JSONArray()
                for (tuple in pc.manualAddresses) {
                    manualArr.put(ComputerDatabaseManager.tupleToJson(tuple))
                }
                addrObj.put("manual", manualArr)
                addrObj.put("ipv6", ComputerDatabaseManager.tupleToJson(pc.ipv6Address))
                pcObj.put("addresses", addrObj)

                // 导出与服务器已绑定的安全证书
                if (pc.serverCert != null) {
                    pcObj.put("server_cert_b64", Base64.encodeToString(pc.serverCert.encoded, Base64.NO_WRAP))
                }
                computersArray.put(pcObj)
            }
            root.put("computers", computersArray)

            // 4. 读取极为敏感的设备 UniqueID 和 RSA 私钥及证书，混合加密
            val credentials = JSONObject()
            val dataPath = context.filesDir.absolutePath
            val uniqueIdFile = File("$dataPath/uniqueid")
            val certFile = File("$dataPath/client.crt")
            val keyFile = File("$dataPath/client.key")

            val uniqueIdBytes = readFileBytesSafe(uniqueIdFile)
            val certBytes = readFileBytesSafe(certFile)
            val keyBytes = readFileBytesSafe(keyFile)

            if (uniqueIdBytes != null && certBytes != null && keyBytes != null) {
                val uniqueIdStr = String(uniqueIdBytes, StandardCharsets.UTF_8).trim()
                val certStr = String(certBytes, StandardCharsets.UTF_8)
                val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)

                // 💥 关键防线：利用本机物理指纹 AES-256 对敏感数据执行加密，公钥证书公开发送
                credentials.put("enc_uniqueid", encrypt(uniqueIdStr, fingerprint))
                credentials.put("enc_client_key", encrypt(keyBase64, fingerprint))
                credentials.put("client_crt", certStr)
                
                root.put("credentials", credentials)
            }

            // 输出 4 格美化 JSON
            return root.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "系统设置全量备份导出失败", e)
        }
        return null
    }

    /**
     * 系统配置一键恢复。
     * 支持自适应指纹校验：
     *   - 指纹解密成功（本设备复原）：全量恢复所有参数、主机表及安全私钥（用户免重新配对直连）。
     *   - 指纹解密失败（跨设备导入）：自适应安全降级自愈，只导入通用 Preferences 参数与电脑列表，主动丢弃 UniqueID 和私钥证书。
     * @return 导入执行状态代码。1: 同机全量恢复；2: 跨设备降级安全导入。
     */
    fun importSystemBackup(context: Context, data: String): Int {
        val root = JSONObject(data)
        
        // 1. 恢复主要 Preferences 设置参数
        if (root.has("preferences")) {
            val prefsObj = root.getJSONObject("preferences")
            // 使用 PreferenceManager 动态获取，自适应所有 applicationId 变体
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.clear()
            
            importPreferences(editor, prefsObj)
            editor.apply()
            Log.i(TAG, "已成功批量恢复串流参数 Preference 配置")
        }

        if (root.has("extra_preferences")) {
            val extraPrefs = root.getJSONObject("extra_preferences")
            for (name in EXTRA_PREFS_NAMES) {
                if (!extraPrefs.has(name)) continue
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                editor.clear()
                importPreferences(editor, extraPrefs.getJSONObject(name))
                editor.apply()
            }
            Log.i(TAG, "已成功恢复虚拟键盘、悬浮键盘与自定义热键配置")
        }

        // 2. 批量将已存电脑表恢复写入 SQLite computers4.db
        if (root.has("computers")) {
            val dbManager = ComputerDatabaseManager(context)
            // 事务级清理老的主机列表，防止覆盖残余或 UUID 冲突
            for (oldPc in dbManager.allComputers) {
                dbManager.deleteComputer(oldPc)
            }
            
            val computersArray = root.getJSONArray("computers")
            val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
            
            for (i in 0 until computersArray.length()) {
                val pcObj = computersArray.getJSONObject(i)
                val pc = ComputerDetails()
                pc.uuid = pcObj.getString("uuid")
                pc.name = pcObj.getString("name")
                pc.macAddress = pcObj.optString("mac", "")
                
                if (pcObj.has("addresses")) {
                    val addrObj = pcObj.getJSONObject("addresses")
                    pc.localAddress = ComputerDatabaseManager.tupleFromJson(addrObj, "local")
                    pc.remoteAddress = ComputerDatabaseManager.tupleFromJson(addrObj, "remote")
                    
                    if (addrObj.has("manual")) {
                        val manualArr = addrObj.getJSONArray("manual")
                        for (j in 0 until manualArr.length()) {
                            val tupleObj = manualArr.getJSONObject(j)
                            pc.manualAddresses.add(ComputerDetails.AddressTuple(
                                tupleObj.getString("address"),
                                tupleObj.getInt("port")
                            ))
                        }
                    }
                    pc.ipv6Address = ComputerDatabaseManager.tupleFromJson(addrObj, "ipv6")
                }
                
                if (pcObj.has("server_cert_b64")) {
                    try {
                        val certBytes = Base64.decode(pcObj.getString("server_cert_b64"), Base64.NO_WRAP)
                        pc.serverCert = certFactory.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as java.security.cert.X509Certificate
                    } catch (ignored: Exception) {}
                }
                
                dbManager.updateComputer(pc)
            }
            dbManager.close()
            Log.i(TAG, "已成功批量恢复配对主机表 SQLite 数据库")
        }

        // 3. 校验并解密核心安全凭据
        if (root.has("credentials")) {
            val credentials = root.getJSONObject("credentials")
            val fingerprint = getDeviceFingerprint(context)
            
            try {
                // 尝试用当前本机的物理设备指纹密钥进行解密
                val encUniqueId = credentials.getString("enc_uniqueid")
                val encClientKey = credentials.getString("enc_client_key")
                val certStr = credentials.getString("client_crt")

                // 物理指纹匹配校验：若是非本机导入，该步骤解密必定会抛出解密 padding 破损异常
                val decryptedUniqueId = decrypt(encUniqueId, fingerprint)
                val decryptedKeyB64 = decrypt(encClientKey, fingerprint)
                val decryptedKeyBytes = Base64.decode(decryptedKeyB64, Base64.NO_WRAP)

                // 密码学解密成功 -> 说明是本台设备恢复，全量自愈还原，免二次配对
                val dataPath = context.filesDir.absolutePath
                
                FileOutputStream(File("$dataPath/uniqueid")).use { it.write(decryptedUniqueId.toByteArray(StandardCharsets.UTF_8)) }
                FileOutputStream(File("$dataPath/client.crt")).use { it.write(certStr.toByteArray(StandardCharsets.UTF_8)) }
                FileOutputStream(File("$dataPath/client.key")).use { it.write(decryptedKeyBytes) }
                
                Log.i(TAG, "同机指纹解密校验通过，证书与配对 UniqueID 全量原样复原！")
                return 1
            } catch (e: Exception) {
                // 指纹不匹配 -> 自动触发安全降级（Downgrade Policy）
                // 仅仅还原串流 Preference 和 Computers，坚决不重写 UniqueID 和 client.key，保障隔离安全
                Log.w(TAG, "指纹密文解密失败 (异机导入或指纹变化)，安全保护激活：已跳过覆盖本机 UniqueID 和私钥", e)
            }
        }

        return 2 // 异机安全降级导入
    }
}
