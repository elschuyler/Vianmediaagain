package com.example.ime.cards

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID

@Serializable
data class TextCardItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var type: EntryType,
    var isPinned: Boolean = false,
    var timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("text", text)
            put("type", type.name)
            put("isPinned", isPinned)
            put("timestamp", timestamp)
        }
    }

    companion object {
        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun encodeList(items: List<TextCardItem>): String {
            return jsonFormat.encodeToString(items)
        }

        fun decodeList(jsonStr: String): List<TextCardItem> {
            return try {
                jsonFormat.decodeFromString(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun fromJson(obj: JSONObject): TextCardItem? {
            val text = obj.optString("text", "")
            if (text.isEmpty()) return null
            val id = obj.optString("id", UUID.randomUUID().toString())
            val typeStr = obj.optString("type", EntryType.CLIPBOARD.name)
            val type = try {
                EntryType.valueOf(typeStr)
            } catch (e: Exception) {
                EntryType.CLIPBOARD
            }
            val isPinned = obj.optBoolean("isPinned", false)
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            return TextCardItem(
                id = id,
                text = text,
                type = type,
                isPinned = isPinned,
                timestamp = timestamp
            )
        }
    }
}
