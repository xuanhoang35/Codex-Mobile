package vn.intsoft.codexmobile.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SnippetStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("codex_mobile_snippets", Context.MODE_PRIVATE)

    fun load(): List<SnippetEntry> {
        return try {
            val dataStr = preferences.getString("snippets_data", null) ?: return emptyList()
            val jsonArray = JSONArray(dataStr)

            val result = mutableListOf<SnippetEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val parentId = obj.optString("parentId").takeIf { it.isNotBlank() }
                val kindStr = obj.optString("kind", "SNIPPET")
                val kind = if (kindStr == "GROUP") SnippetKind.GROUP else SnippetKind.SNIPPET
                val title = obj.optString("title")
                val content = obj.optString("content")
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

                if (id.isNotBlank() && title.isNotBlank()) {
                    result.add(SnippetEntry(id, parentId, kind, title, content, updatedAt))
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(entries: List<SnippetEntry>) {
        try {
            val array = JSONArray()
            for (item in entries) {
                val obj = JSONObject()
                    .put("id", item.id)
                    .put("parentId", item.parentId ?: "")
                    .put("kind", item.kind.name)
                    .put("title", item.title)
                    .put("content", item.content)
                    .put("updatedAt", item.updatedAt)
                array.put(obj)
            }
            preferences.edit().putString("snippets_data", array.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    fun getAll(): List<SnippetEntry> = load()

    fun createGroup(parentId: String?, title: String): SnippetEntry {
        val entry = SnippetEntry(
            id = java.util.UUID.randomUUID().toString(),
            parentId = parentId,
            kind = SnippetKind.GROUP,
            title = title
        )
        val list = load().toMutableList()
        list.add(entry)
        save(list)
        return entry
    }

    fun createSnippet(groupId: String?, title: String, content: String): SnippetEntry {
        val entry = SnippetEntry(
            id = java.util.UUID.randomUUID().toString(),
            parentId = groupId,
            kind = SnippetKind.SNIPPET,
            title = title,
            content = content
        )
        val list = load().toMutableList()
        list.add(entry)
        save(list)
        return entry
    }

    fun update(id: String, title: String, content: String) {
        val list = load().map {
            if (it.id == id) it.copy(title = title, content = content, updatedAt = System.currentTimeMillis())
            else it
        }
        save(list)
    }

    fun delete(id: String) {
        val all = load()
        val idsToDelete = mutableSetOf(id)
        var addedMore = true
        while (addedMore) {
            val children = all.filter { it.parentId in idsToDelete && it.id !in idsToDelete }.map { it.id }
            if (children.isEmpty()) {
                addedMore = false
            } else {
                idsToDelete.addAll(children)
            }
        }
        save(all.filterNot { it.id in idsToDelete })
    }
}
