package vn.intsoft.codexmobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "codex_secure_tokens",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        context.getSharedPreferences("codex_fallback_tokens", Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun clearToken() {
        prefs.edit().remove("auth_token").apply()
    }

    fun saveActiveEngine(engine: AiEngine) {
        prefs.edit().putString("active_engine", engine.id).apply()
    }

    fun getActiveEngine(): AiEngine {
        val id = prefs.getString("active_engine", AiEngine.CODEX.id)
        return AiEngine.values().firstOrNull { it.id == id } ?: AiEngine.CODEX
    }

    fun saveGoogleUser(profile: GoogleUserProfile?) {
        if (profile == null) {
            prefs.edit().remove("google_user_json").apply()
        } else {
            val json = JSONObject().apply {
                put("email", profile.email)
                put("displayName", profile.displayName)
                put("idToken", profile.idToken ?: "")
                put("accessToken", profile.accessToken ?: "")
                put("avatarUrl", profile.avatarUrl ?: "")
            }
            prefs.edit().putString("google_user_json", json.toString()).apply()
        }
    }

    fun getGoogleUser(): GoogleUserProfile? {
        val raw = prefs.getString("google_user_json", null) ?: return null
        return try {
            val json = JSONObject(raw)
            GoogleUserProfile(
                email = json.optString("email"),
                displayName = json.optString("displayName"),
                idToken = json.optString("idToken").takeIf { it.isNotBlank() },
                accessToken = json.optString("accessToken").takeIf { it.isNotBlank() },
                avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAntigravityApiKey(key: String) {
        prefs.edit().putString("antigravity_api_key", key).apply()
    }

    fun getAntigravityApiKey(): String {
        return prefs.getString("antigravity_api_key", "") ?: ""
    }

    fun saveClineApiKey(key: String) {
        prefs.edit().putString("cline_api_key", key).apply()
    }

    fun getClineApiKey(): String {
        return prefs.getString("cline_api_key", "") ?: ""
    }

    fun saveClineEndpoint(endpoint: String) {
        prefs.edit().putString("cline_endpoint", endpoint).apply()
    }

    fun getClineEndpoint(): String {
        return prefs.getString("cline_endpoint", "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1"
    }
}
