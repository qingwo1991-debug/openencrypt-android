package org.openlist.encrypt.android.config

import android.content.Context
import org.json.JSONObject

data class SchemaFieldDescriptor(
    val key: String,
    val level: String,
    val primaryPage: String
)

class SchemaFieldRegistry(private val context: Context) {
    fun loadFields(): List<SchemaFieldDescriptor> {
        val raw = context.assets.open("config.schema.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val properties = root.getJSONObject("properties")

        val fields = mutableListOf<SchemaFieldDescriptor>()
        collectFields("", properties, fields)
        return fields
    }

    fun validatePrimaryEditPages(fields: List<SchemaFieldDescriptor>): List<String> {
        val map = mutableMapOf<String, String>()
        val errors = mutableListOf<String>()
        fields.forEach { f ->
            val existing = map.putIfAbsent(f.key, f.primaryPage)
            if (existing != null && existing != f.primaryPage) {
                errors += "duplicate editable entry: ${f.key}"
            }
        }
        return errors
    }

    private fun collectFields(
        prefix: String,
        properties: JSONObject,
        out: MutableList<SchemaFieldDescriptor>
    ) {
        val keys = properties.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val node = properties.optJSONObject(k) ?: continue
            val fullKey = if (prefix.isEmpty()) k else "$prefix.$k"
            val nestedProps = node.optJSONObject("properties")
            if (nestedProps != null) {
                collectFields(fullKey, nestedProps, out)
                continue
            }

            if (node.optString("type") == "array") {
                val items = node.optJSONObject("items")
                val itemProps = items?.optJSONObject("properties")
                if (itemProps != null) {
                    collectFields("$fullKey[]", itemProps, out)
                }
                continue
            }

            val level = node.optString("x-level", "expert")
            out += SchemaFieldDescriptor(
                key = fullKey,
                level = level,
                primaryPage = pickPrimaryPage(fullKey)
            )
        }
    }

    private fun pickPrimaryPage(key: String): String {
        return when {
            key.startsWith("openlist.") -> "Cloud"
            key.startsWith("gateway.") -> "Encrypt"
            key.startsWith("encrypt_rules") -> "Encrypt"
            key.startsWith("webdav.") -> "Tasks"
            key.startsWith("runtime.") -> "Settings"
            key.startsWith("update.") -> "Settings"
            else -> "Settings"
        }
    }
}
