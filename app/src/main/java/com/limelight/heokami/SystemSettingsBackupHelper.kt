package com.limelight.heokami

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.heokami.layout.LayoutProfileManager
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
 * 系统设置与配对关系备份还原助手。
 *
 * 备份分两层：
 *  - 偏好设置与主机列表：明文 JSON，不含任何密钥材料。
 *  - 配对凭据（client.key / client.crt / uniqueid）：仅当用户设置了备份密码时导出，
 *    使用 [BackupCrypto]（PBKDF2-HMAC-SHA256 + AES-256-GCM）加密，可在任意设备上用密码还原。
 *    不设密码则只备份偏好设置，换设备后需重新配对。
 *
 * 旧版（设备指纹加密）备份只保留读取能力：旧版把密钥指纹明文写进了文件，不再生成。
 */
object SystemSettingsBackupHelper {
    private const val TAG = "SettingsBackupHelper"
    private val EXTRA_PREFS_NAMES = arrayOf(
        "OSK",
        "floating_keyboard_prefs",
        "game_menu_prefs"
    )
    
    // 仅用于读取旧版备份（AES-256-CBC + 静态 IV + 设备指纹密钥）
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
     * 包括：SharedPreferences 设置参数、Computers 数据库；若提供了 [password]，另含用该密码加密的证书、私钥和 UniqueID。
     * 注意：调用含 PBKDF2，较耗时，不要在主线程执行。
     */
    fun exportSystemBackup(context: Context, password: CharArray? = null): String? {
        try {
            val root = JSONObject()
            
            // 1. 导出元数据
            val metadata = JSONObject()
            metadata.put("app_id", "com.limelight.heokami")
            metadata.put("backup_time", System.currentTimeMillis())
            metadata.put("type", "pairing_and_settings")
            metadata.put("format_version", 2)
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

            // 键盘布局配置（多套布局及其绑定）。不含密钥，明文即可。
            try {
                root.put("layout_profiles", LayoutProfileManager.exportForBackup(context))
            } catch (e: Exception) {
                Log.w(TAG, "键盘布局配置导出失败，已跳过", e)
            }

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

            // 4. 仅在用户设置了密码时导出配对凭据，且只以密文形式写入
            if (password != null && password.isNotEmpty()) {
                val dataPath = context.filesDir.absolutePath
                val uniqueIdBytes = readFileBytesSafe(File("$dataPath/uniqueid"))
                val certBytes = readFileBytesSafe(File("$dataPath/client.crt"))
                val keyBytes = readFileBytesSafe(File("$dataPath/client.key"))

                if (uniqueIdBytes != null && certBytes != null && keyBytes != null) {
                    val secrets = JSONObject()
                    secrets.put("uniqueid", String(uniqueIdBytes, StandardCharsets.UTF_8).trim())
                    secrets.put("client_crt", String(certBytes, StandardCharsets.UTF_8))
                    secrets.put("client_key_b64", Base64.encodeToString(keyBytes, Base64.NO_WRAP))
                    val blob = BackupCrypto.encrypt(password, secrets.toString().toByteArray(StandardCharsets.UTF_8))
                    root.put("credentials_enc", Base64.encodeToString(blob, Base64.NO_WRAP))
                }
            }

            // 输出 4 格美化 JSON
            return root.toString(4)
        } catch (e: Exception) {
            Log.e(TAG, "系统设置全量备份导出失败", e)
        }
        return null
    }

    enum class RestoreResult { CREDENTIALS_RESTORED, PREFERENCES_ONLY }

    class BackupInfo(val needsPassword: Boolean, val hasLegacyCredentials: Boolean)

    private class Credentials(val uniqueId: String, val certPem: String, val keyBytes: ByteArray)

    /** 解析备份文件，判断是否包含需要密码才能还原的配对凭据；文件无效时抛出 [InvalidBackupException]。 */
    fun inspectBackup(data: String): BackupInfo {
        try {
            val root = JSONObject(data)
            if (!root.has("preferences") && !root.has("computers")) {
                throw InvalidBackupException("Not a Moonlight backup file")
            }
            return BackupInfo(
                needsPassword = root.has("credentials_enc"),
                hasLegacyCredentials = root.has("credentials")
            )
        } catch (e: org.json.JSONException) {
            throw InvalidBackupException("Not a valid backup file", e)
        }
    }

    /**
     * 系统配置一键恢复。凭据先解密校验，全部通过后才开始写入，密码错误时不会改动任何现有数据。
     *
     * @param password 备份密码；为 null 表示跳过配对凭据，只还原偏好设置与主机列表。
     * @throws WrongPasswordException 文件含加密凭据、提供了密码但验证失败
     * @throws InvalidBackupException 文件损坏或格式不支持
     */
    fun importSystemBackup(context: Context, data: String, password: CharArray?): RestoreResult {
        val root = try {
            JSONObject(data)
        } catch (e: org.json.JSONException) {
            throw InvalidBackupException("Not a valid backup file", e)
        }

        val credentials = resolveCredentials(context, root, password)

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

        if (root.has("layout_profiles")) {
            try {
                LayoutProfileManager.importFromBackup(context, root.getJSONObject("layout_profiles"))
                Log.i(TAG, "已成功恢复键盘布局配置")
            } catch (e: Exception) {
                Log.w(TAG, "键盘布局配置恢复失败，保留当前布局", e)
            }
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
                
                // 服务器证书只有在客户端凭据一并还原时才有意义；否则保留它会让主机显示"已配对"但实际被拒绝
                if (credentials != null && pcObj.has("server_cert_b64")) {
                    try {
                        val certBytes = Base64.decode(pcObj.getString("server_cert_b64"), Base64.NO_WRAP)
                        pc.serverCert = certFactory.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as java.security.cert.X509Certificate
                    } catch (ignored: Exception) {}
                }
                
                dbManager.updateComputer(pc)
            }
            dbManager.close()
            Log.i(TAG, "已成功批量恢复主机表 SQLite 数据库")
        }

        // 3. 写入配对凭据
        if (credentials != null) {
            val dataPath = context.filesDir.absolutePath
            FileOutputStream(File("$dataPath/uniqueid")).use { it.write(credentials.uniqueId.toByteArray(StandardCharsets.UTF_8)) }
            FileOutputStream(File("$dataPath/client.crt")).use { it.write(credentials.certPem.toByteArray(StandardCharsets.UTF_8)) }
            FileOutputStream(File("$dataPath/client.key")).use { it.write(credentials.keyBytes) }
            Log.i(TAG, "配对凭据已还原，无需重新配对")
            return RestoreResult.CREDENTIALS_RESTORED
        }
        return RestoreResult.PREFERENCES_ONLY
    }

    /** 在不修改任何现有数据的前提下取得并校验凭据；返回 null 表示这次不还原凭据。 */
    private fun resolveCredentials(context: Context, root: JSONObject, password: CharArray?): Credentials? {
        if (root.has("credentials_enc")) {
            if (password == null) return null
            val blob = try {
                Base64.decode(root.getString("credentials_enc"), Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                throw InvalidBackupException("Corrupted credentials block", e)
            }
            val secrets = try {
                JSONObject(String(BackupCrypto.decrypt(password, blob), StandardCharsets.UTF_8))
            } catch (e: org.json.JSONException) {
                throw InvalidBackupException("Corrupted credentials block", e)
            }
            return validated(
                secrets.getString("uniqueid"),
                secrets.getString("client_crt"),
                Base64.decode(secrets.getString("client_key_b64"), Base64.NO_WRAP)
            )
        }

        // 旧版备份：只有在同一台设备（指纹一致）上才能解开，跨设备一律跳过
        if (root.has("credentials")) {
            val legacy = root.getJSONObject("credentials")
            return try {
                val fingerprint = getDeviceFingerprint(context)
                validated(
                    decrypt(legacy.getString("enc_uniqueid"), fingerprint),
                    legacy.getString("client_crt"),
                    Base64.decode(decrypt(legacy.getString("enc_client_key"), fingerprint), Base64.NO_WRAP)
                )
            } catch (e: Exception) {
                Log.w(TAG, "旧版备份凭据无法在本设备解密，已跳过", e)
                null
            }
        }
        return null
    }

    private fun validated(uniqueId: String, certPem: String, keyBytes: ByteArray): Credentials {
        try {
            java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(java.io.ByteArrayInputStream(certPem.toByteArray(StandardCharsets.UTF_8)))
            java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyBytes))
        } catch (e: Exception) {
            throw InvalidBackupException("Credentials in backup are not a valid certificate/key pair", e)
        }
        return Credentials(uniqueId, certPem, keyBytes)
    }
}
