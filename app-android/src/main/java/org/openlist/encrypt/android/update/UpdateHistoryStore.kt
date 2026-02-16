package org.openlist.encrypt.android.update

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UpdateHistoryEntry(
    val timestamp: String,
    val stage: String,
    val result: String,
    val detail: String
)

class UpdateHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("update_history", Context.MODE_PRIVATE)
    private val key = "entries"
    private val maxEntries = 50

    fun append(entry: UpdateHistoryEntry) {
        val arr = loadJsonArray()
        arr.put(
            JSONObject()
                .put("timestamp", entry.timestamp)
                .put("stage", entry.stage)
                .put("result", entry.result)
                .put("detail", entry.detail)
        )

        val trimmed = JSONArray()
        val start = (arr.length() - maxEntries).coerceAtLeast(0)
        for (i in start until arr.length()) {
            trimmed.put(arr.getJSONObject(i))
        }

        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    fun latest(limit: Int = 5): List<UpdateHistoryEntry> {
        val arr = loadJsonArray()
        if (arr.length() == 0) return emptyList()

        val out = mutableListOf<UpdateHistoryEntry>()
        val start = (arr.length() - limit).coerceAtLeast(0)
        for (i in arr.length() - 1 downTo start) {
            val obj = arr.optJSONObject(i) ?: continue
            out += UpdateHistoryEntry(
                timestamp = obj.optString("timestamp"),
                stage = obj.optString("stage"),
                result = obj.optString("result"),
                detail = obj.optString("detail")
            )
        }
        return out
    }

    private fun loadJsonArray(): JSONArray {
        val raw = prefs.getString(key, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
}
