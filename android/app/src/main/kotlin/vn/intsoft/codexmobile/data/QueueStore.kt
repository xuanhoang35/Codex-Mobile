package vn.intsoft.codexmobile.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class QueueStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("codex_mobile_queue", Context.MODE_PRIVATE)

    fun load(): List<QueuedTurnRecord> {
        return try {
            val dataStr = preferences.getString("data", null) ?: return emptyList()
            val jsonArray = JSONArray(dataStr)
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
        try {
            val array = JSONArray()
            for (item in records) {
                array.put(JSONObject().put("id", item.id).put("text", item.text))
            }
            preferences.edit().putString("data", array.toString()).apply()
        } catch (_: Throwable) {
        }
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
}
