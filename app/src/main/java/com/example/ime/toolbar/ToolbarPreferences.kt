package com.example.ime.toolbar

import android.content.Context
import android.content.SharedPreferences

class ToolbarPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vian_toolbar_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PINNED_TOOLS = "pinned_tools"
        private const val KEY_EXPANDED_TOOLS = "expanded_tools"
        private const val KEY_HIDE_PINNED_ON_EXPAND = "hide_pinned_on_expand"
        private const val KEY_TEMP_INCOGNITO_MINUTES = "temp_incognito_minutes"
    }

    fun getPinnedTools(): List<ToolbarTool> {
        val saved = prefs.getString(KEY_PINNED_TOOLS, null)
        if (saved.isNullOrEmpty()) {
            return listOf(ToolbarTool.SELECT_ALL, ToolbarTool.COPY, ToolbarTool.CLIPBOARD)
        }
        return saved.split(",").mapNotNull { ToolbarTool.fromId(it.trim()) }
    }

    fun setPinnedTools(tools: List<ToolbarTool>) {
        val str = tools.joinToString(",") { it.id }
        prefs.edit().putString(KEY_PINNED_TOOLS, str).apply()
    }

    fun getExpandedTools(): List<ToolbarTool> {
        val saved = prefs.getString(KEY_EXPANDED_TOOLS, null)
        if (saved.isNullOrEmpty()) {
            return ToolbarTool.values().filter { it.isDefaultExpanded }
        }
        return saved.split(",").mapNotNull { ToolbarTool.fromId(it.trim()) }
    }

    fun setExpandedTools(tools: List<ToolbarTool>) {
        val str = tools.joinToString(",") { it.id }
        prefs.edit().putString(KEY_EXPANDED_TOOLS, str).apply()
    }

    var hidePinnedWhenExpanded: Boolean
        get() = prefs.getBoolean(KEY_HIDE_PINNED_ON_EXPAND, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_PINNED_ON_EXPAND, value).apply()

    var tempIncognitoDurationMinutes: Int
        get() = prefs.getInt(KEY_TEMP_INCOGNITO_MINUTES, 3)
        set(value) = prefs.edit().putInt(KEY_TEMP_INCOGNITO_MINUTES, value).apply()
}
