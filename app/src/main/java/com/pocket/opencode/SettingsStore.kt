package com.pocket.opencode

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Пароль сервера шифруется ключом Android Keystore (AES-256/GCM).
 * В SharedPreferences попадает только зашифрованный блоб.
 */
private class PasswordCipher {
    private val alias = "opencode_pocket_password"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(encrypted, 0, packed, iv.size, encrypted.size)
            Base64.encodeToString(packed, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val packed = Base64.decode(value, Base64.NO_WRAP)
            if (packed.size <= 12) return ""
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pocket_state", Context.MODE_PRIVATE)
    private val cipher = PasswordCipher()

    var baseUrl: String
        get() = prefs.getString("base_url", "http://127.0.0.1:4096").orEmpty()
        set(value) = prefs.edit().putString("base_url", value).apply()

    var password: String
        get() = cipher.decrypt(prefs.getString("password_enc", "").orEmpty())
        set(value) = prefs.edit().putString("password_enc", cipher.encrypt(value)).apply()

    var sessionId: String?
        get() = prefs.getString("session_id", null)
        set(value) = prefs.edit().putString("session_id", value).apply()

    var sessionTitle: String
        get() = prefs.getString("session_title", "").orEmpty()
        set(value) = prefs.edit().putString("session_title", value).apply()

    var tab: Int
        get() {
            val schema = prefs.getInt("tab_schema", 1)
            val value = TabMigration.map(prefs.getInt("tab", 0), schema)
            if (schema < TabMigration.SCHEMA) {
                prefs.edit()
                    .putInt("tab", value)
                    .putInt("tab_schema", TabMigration.SCHEMA)
                    .apply()
            }
            return value
        }
        set(value) {
            prefs.edit()
                .putInt("tab", value)
                .putInt("tab_schema", TabMigration.SCHEMA)
                .apply()
        }

    val modeWasSet: Boolean get() = prefs.contains("mode")

    var mode: ServerMode
        get() = ConnectionModeResolver.resolve(
            prefs.contains("mode"),
            prefs.getString("mode", null),
            prefs.contains("base_url"),
        )
        set(value) = prefs.edit()
            .putString("mode", if (value == ServerMode.EMBEDDED) "embedded" else "external")
            .apply()

    var workspacePath: String
        get() = prefs.getString("workspace_path", "/sdcard/OpenCode").orEmpty()
        set(value) = prefs.edit().putString("workspace_path", value).apply()

    var serverPort: Int
        get() = prefs.getInt("server_port", 4096)
        set(value) = prefs.edit().putInt("server_port", value).apply()

    var draft: String
        get() = prefs.getString("draft", "").orEmpty()
        set(value) = prefs.edit().putString("draft", value).apply()

    var agent: String?
        get() = prefs.getString("agent", null)
        set(value) = prefs.edit().putString("agent", value).apply()

    var modelProvider: String?
        get() = prefs.getString("model_provider", null)
        set(value) = prefs.edit().putString("model_provider", value).apply()

    var modelId: String?
        get() = prefs.getString("model_id", null)
        set(value) = prefs.edit().putString("model_id", value).apply()

    var modelVariant: String?
        get() = prefs.getString("model_variant", null)
        set(value) = prefs.edit().putString("model_variant", value).apply()

    fun saveSession(id: String?, title: String) {
        prefs.edit().putString("session_id", id).putString("session_title", title).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

class SessionCache(context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    private fun file(id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.json")
    }

    fun load(id: String): List<UiMessage> = runCatching {
        val f = file(id)
        if (!f.exists()) emptyList() else cacheFromJson(f.readText())
    }.getOrDefault(emptyList())

    fun save(id: String, messages: List<UiMessage>) {
        runCatching {
            val recent = if (messages.size > 300) messages.takeLast(300) else messages
            val arr = JSONArray()
            for (message in recent) arr.put(message.toCacheJson())
            val root = JSONObject().put("version", 1).put("messages", arr)
            val tmp = File(dir, "${file(id).name}.tmp")
            tmp.writeText(root.toString())
            if (file(id).exists()) file(id).delete()
            tmp.renameTo(file(id))
        }
    }

    fun clearAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
