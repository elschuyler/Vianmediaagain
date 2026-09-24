package com.example.ime.emoji

import android.content.Context
import com.example.R
import org.json.JSONArray

data class EmojiCategory(
    val id: String,
    val name: String,
    val iconResId: Int,
    val emojis: List<String>
)

object EmojiCategoryData {

    private const val PREFS_NAME = "vian_emoji_prefs"
    private const val KEY_RECENTS = "recent_emojis"
    private const val MAX_RECENTS = 48

    val DEFAULT_RECENTS = listOf(
        "😀", "😂", "🥰", "😍", "👍", "❤️", "🔥", "✨",
        "😊", "🙏", "🎉", "🤣", "😭", "🥺", "😎", "🙌"
    )

    private val cachedCategories = mutableMapOf<String, List<String>>()

    private fun loadAssetEmojis(context: Context, filename: String): List<String> {
        cachedCategories[filename]?.let { return it }
        return try {
            val list = context.assets.open("emoji/$filename").bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) null
                    else if (filename == "EMOTICONS.txt") trimmed
                    else trimmed.split("\\s+".toRegex()).firstOrNull()
                }.toList()
            }
            cachedCategories[filename] = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCategories(context: Context): List<EmojiCategory> {
        val recents = getRecentEmojis(context)
        return listOf(
            EmojiCategory("recent", "Recent", R.drawable.ic_emoji_recents_rounded, recents),
            EmojiCategory("smileys", "Smileys", R.drawable.ic_emoji_smileys_emotion_rounded, loadAssetEmojis(context, "SMILEYS_AND_EMOTION.txt")),
            EmojiCategory("people", "People", R.drawable.ic_emoji_people_body_rounded, loadAssetEmojis(context, "PEOPLE_AND_BODY.txt")),
            EmojiCategory("animals", "Animals", R.drawable.ic_emoji_animals_nature, loadAssetEmojis(context, "ANIMALS_AND_NATURE.txt")),
            EmojiCategory("food", "Food", R.drawable.ic_emoji_food_drink_rounded, loadAssetEmojis(context, "FOOD_AND_DRINK.txt")),
            EmojiCategory("travel", "Travel", R.drawable.ic_emoji_travel_places_rounded, loadAssetEmojis(context, "TRAVEL_AND_PLACES.txt")),
            EmojiCategory("activities", "Activities", R.drawable.ic_emoji_activities_rounded, loadAssetEmojis(context, "ACTIVITIES.txt")),
            EmojiCategory("objects", "Objects", R.drawable.ic_emoji_objects_rounded, loadAssetEmojis(context, "OBJECTS.txt")),
            EmojiCategory("symbols", "Symbols", R.drawable.ic_emoji_symbols_rounded, loadAssetEmojis(context, "SYMBOLS.txt")),
            EmojiCategory("flags", "Flags", R.drawable.ic_emoji_flags_rounded, loadAssetEmojis(context, "FLAGS.txt"))
        )
    }

    fun getRecentEmojis(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RECENTS, null) ?: return DEFAULT_RECENTS
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            if (list.isEmpty()) DEFAULT_RECENTS else list
        } catch (e: Exception) {
            DEFAULT_RECENTS
        }
    }

    fun recordEmojiUse(context: Context, emoji: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getRecentEmojis(context).toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        if (current.size > MAX_RECENTS) {
            current.removeAt(current.lastIndex)
        }
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }
}
