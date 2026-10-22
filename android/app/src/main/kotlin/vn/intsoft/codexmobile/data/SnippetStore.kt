package vn.intsoft.codexmobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class SnippetStore(context: Context) {
    private val preferences = context.getSharedPreferences("codex_mobile_snippets", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): List<SnippetEntry> {
        return try {
            val ivStr = preferences.getString("snippets_iv", null) ?: return emptyList()
            val dataStr = preferences.getString("snippets_ciphertext", null) ?: return emptyList()
            val key = keyStore.getKey("codex_mobile_snippet_key", null) as? SecretKey ?: return emptyList()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(ivStr, Base64.NO_WRAP)))
            val decrypted = cipher.doFinal(Base64.decode(dataStr, Base64.NO_WRAP))
            val jsonArray = JSONArray(String(decrypted, Charsets.UTF_8))

            val result = mutableListOf<SnippetEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val title = obj.optString("title").trim()
                val kindStr = obj.optString("kind")
                val kind = try { SnippetKind.valueOf(kindStr) } catch (_: Throwable) { SnippetKind.SNIPPET }
                val content = obj.optString("content")
                val parentId = if (obj.has("parentId") && !obj.isNull("parentId")) obj.getString("parentId") else null

                if (id.isNotBlank() && title.isNotBlank()) {
                    result.add(SnippetEntry(id, parentId, kind, title, content))
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(entries: List<SnippetEntry>) {
        val key = getOrCreateKey()
        val array = JSONArray()
        for (item in entries) {
            val obj = JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("kind", item.kind.name)
                .put("content", item.content)
            if (item.parentId != null) {
                obj.put("parentId", item.parentId)
            }
            array.put(obj)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString("snippets_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("snippets_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey("codex_mobile_snippet_key", null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                "codex_mobile_snippet_key",
                KeyGenParameterSpec.PURPOSE_ENCRYPT or KeyGenParameterSpec.PURPOSE_DECRYPT
            )
                .setBlockModes("GCM")
                .setEncryptionPaddings("NoPadding")
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}

