package com.example.ime.quicknotes

import android.content.Context
import com.example.ime.cards.EntryType
import com.example.ime.cards.TextCardStorage

/**
 * Lightweight, zero-idle persistence store for user Quick Notes.
 * Backed by the unified TextCardStorage engine under EntryType.PROMPT.
 */
class QuickNotesStorage(context: Context) {

    private val cardStorage = TextCardStorage.getInstance(context)

    companion object {
        const val PREFS_SETTINGS = "vian_notes_settings"
        const val KEY_MOVE_MODE = "pref_clipboard_move_mode" // "copy" or "cut"
        const val DEFAULT_MOVE_MODE = "copy"
    }

    fun getPinnedNotes(): List<String> =
        cardStorage.getPinnedItems(EntryType.PROMPT).map { it.text }

    fun getSavedNotes(): List<String> =
        cardStorage.getUnpinnedItems(EntryType.PROMPT).map { it.text }

    fun addNote(text: String) {
        cardStorage.addPrompt(text)
    }

    fun updateNote(oldText: String, newText: String) {
        cardStorage.updatePrompt(oldText, newText)
    }

    fun togglePin(text: String) {
        cardStorage.togglePinByText(EntryType.PROMPT, text)
    }

    fun deleteNote(text: String) {
        cardStorage.deleteItemByText(EntryType.PROMPT, text)
    }

    fun isPinned(text: String): Boolean {
        return cardStorage.isPinnedByText(EntryType.PROMPT, text)
    }

    fun isMoveModeCut(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return sp.getString(KEY_MOVE_MODE, DEFAULT_MOVE_MODE) == "cut"
    }

    fun setMoveMode(context: Context, mode: String) {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_MOVE_MODE, mode).apply()
    }
}
