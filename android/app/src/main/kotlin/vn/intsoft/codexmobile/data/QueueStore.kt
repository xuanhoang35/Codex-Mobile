package vn.intsoft.codexmobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class QueueStore(context: Context) {
    private val preferences = context.getSharedPreferences("codex_mobile_queue", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): List<QueuedTurnRecord> {
        return try {
            val ivStr = preferences.getString("iv", null) ?: return emptyList()
            val dataStr = preferences.getString("data", null) ?: return emptyList()
            val key = keyStore.getKey("codex_mobile_queue_key", null) as? SecretKey ?: return emptyList()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(ivStr, Base64.NO_WRAP)))
            val decrypted = cipher.doFinal(Base64.decode(dataStr, Base64.NO_WRAP))
            val jsonArray = JSONArray(String(decrypted, Charsets.UTF_8))

            val result = mutableListOf<QueuedTurnRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val text = obj.optString("text").trim()
                if (id.isNotBlank() && text.isNotBlank()) {
                    result.add(QueuedTurnRecord(id, text))
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(records: List<QueuedTurnRecord>) {
        val key = getOrCreateKey()
        val array = JSONArray()
        for (item in records) {
            array.put(JSONObject().put("id", item.id).put("text", item.text))
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun getAll(): List<QueuedTurnRecord> = load()

    fun add(text: String): QueuedTurnRecord {
        val record = QueuedTurnRecord(java.util.UUID.randomUUID().toString(), text)
        val current = load().toMutableList()
        current.add(record)
        save(current)
        return record
    }

    fun update(id: String, text: String) {
        val current = load().map { if (it.id == id) it.copy(text = text) else it }
        save(current)
    }

    fun delete(id: String) {
        save(load().filterNot { it.id == id })
    }

    fun clear() {
        save(emptyList())
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey("codex_mobile_queue_key", null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                "codex_mobile_queue_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
