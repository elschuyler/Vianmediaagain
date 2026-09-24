package com.example.ime.settings

import android.content.Context
import android.content.SharedPreferences

data class DesktopShortcutItem(
    val id: String,
    val title: String,
    val combo: String,
    val description: String
)

class DesktopShortcutsStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vian_shortcuts_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_ACTIVE_SHORTCUTS = "active_shortcuts"
        const val KEY_RECENT_SHORTCUTS = "recent_shortcuts"
        const val MAX_ACTIVE_SHORTCUTS = 7

        // Default 7 active shortcuts (3-2-2 layout):
        // Row 1 (3): Find, Replace, Save
        // Row 2 (2): Comment, Duplicate
        // Row 3 (2): Indent, Outdent
        val DEFAULT_ACTIVE = listOf(
            "find",
            "replace",
            "save",
            "comment_line",
            "duplicate_line",
            "indent",
            "outdent"
        )

        val ALL_SHORTCUTS = listOf(
            DesktopShortcutItem("find", "Find", "Ctrl+F", "Search in text/code"),
            DesktopShortcutItem("replace", "Replace", "Ctrl+H", "Find and replace"),
            DesktopShortcutItem("copy_all", "Copy All", "Ctrl+A,C", "Select and copy entire text"),
            DesktopShortcutItem("delete_all", "Delete All", "Ctrl+A,⌫", "Select and delete all text"),
            DesktopShortcutItem("goto", "Go To", "Ctrl+G", "Jump to line number"),
            DesktopShortcutItem("save", "Save", "Ctrl+S", "Save active document/file"),
            DesktopShortcutItem("undo", "Undo", "Ctrl+Z", "Undo last edit"),
            DesktopShortcutItem("redo", "Redo", "Ctrl+Y", "Redo reverted edit"),
            DesktopShortcutItem("select_all", "Select All", "Ctrl+A", "Select entire buffer"),
            DesktopShortcutItem("select_word", "Select Word", "Ctrl+W", "Select current word"),
            DesktopShortcutItem("duplicate_line", "Duplicate", "Ctrl+D", "Duplicate current line"),
            DesktopShortcutItem("comment_line", "Comment", "Ctrl+/", "Toggle line comment"),
            DesktopShortcutItem("indent", "Indent", "Tab", "Increase line indentation"),
            DesktopShortcutItem("outdent", "Outdent", "Shift+Tab", "Decrease line indentation"),
            DesktopShortcutItem("line_up", "Move Line Up", "Alt+▲", "Swap line with line above"),
            DesktopShortcutItem("line_down", "Move Line Down", "Alt+▼", "Swap line with line below")
        )

        fun getItemById(id: String): DesktopShortcutItem {
            return ALL_SHORTCUTS.firstOrNull { it.id == id }
                ?: DesktopShortcutItem(id, id.replace("_", " ").capitalize(), "", "")
        }
    }

    fun getActiveShortcuts(): List<String> {
        val saved = prefs.getString(KEY_ACTIVE_SHORTCUTS, null)
        return if (!saved.isNullOrEmpty()) {
            saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_ACTIVE
        }
    }

    fun setActiveShortcuts(list: List<String>) {
        prefs.edit().putString(KEY_ACTIVE_SHORTCUTS, list.take(MAX_ACTIVE_SHORTCUTS).joinToString(",")).apply()
    }

    fun getRecentShortcuts(): List<String> {
        val saved = prefs.getString(KEY_RECENT_SHORTCUTS, "") ?: ""
        return saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addRecentShortcut(id: String) {
        val current = getRecentShortcuts().toMutableList()
        current.remove(id)
        current.add(0, id)
        val trimmed = current.take(6)
        prefs.edit().putString(KEY_RECENT_SHORTCUTS, trimmed.joinToString(",")).apply()
    }
}
