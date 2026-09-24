package com.example.ime.clipboard

import android.content.Context
import com.example.ime.cards.EntryType
import com.example.ime.cards.TextCardStorage

/**
 * Lightweight, zero-idle persistence store for clipboard history and pinned clips.
 * Backed by the unified TextCardStorage engine under EntryType.CLIPBOARD.
 */
class ClipboardStorage(context: Context) {

    private val cardStorage = TextCardStorage.getInstance(context)

    companion object {
        const val MAX_RECENT = TextCardStorage.MAX_RECENT_CLIPS
    }

    fun getPinnedClips(): List<String> =
        cardStorage.getPinnedItems(EntryType.CLIPBOARD).map { it.text }

    fun getRecentClips(): List<String> =
        cardStorage.getUnpinnedItems(EntryType.CLIPBOARD).map { it.text }

    fun addClip(text: String) {
        cardStorage.addClip(text)
    }

    fun togglePin(text: String) {
        cardStorage.togglePinByText(EntryType.CLIPBOARD, text)
    }

    fun deleteClip(text: String) {
        cardStorage.deleteItemByText(EntryType.CLIPBOARD, text)
    }

    fun clearUnpinned() {
        cardStorage.clearUnpinned(EntryType.CLIPBOARD)
    }

    fun isPinned(text: String): Boolean {
        return cardStorage.isPinnedByText(EntryType.CLIPBOARD, text)
    }
}
