package com.example.ime.settings

import android.content.Context
import android.content.SharedPreferences

class CommaPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vian_comma_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SLOTS = "comma_slots"

        val AVAILABLE_ITEMS = listOf(
            "Voice" to "Voice Input",
            "Desktop" to "Desktop Shortcuts",
            "Emoji" to "Emoji Picker",
            "Log Keeper" to "Log Keeper",
            "Clipboard" to "Clipboard Manager",
            "Prompt List" to "Prompt List (Notes)",
            "One Hand" to "One-Handed Mode",
            "Floating" to "Floating Keyboard",
            "Security Vault" to "Security Vault",
            "Personal Vault" to "Personal Vault"
        )

        // Default 4 customizable items besides hardcoded "Settings"
        val DEFAULT_SLOTS = listOf("Voice", "Desktop", "Emoji", "Log Keeper")
    }

    fun getSelectedSlots(): List<String> {
        val saved = prefs.getString(KEY_SLOTS, null)
        return if (!saved.isNullOrEmpty()) {
            saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            DEFAULT_SLOTS
        }
    }

    fun setSelectedSlots(slots: List<String>) {
        prefs.edit().putString(KEY_SLOTS, slots.take(4).joinToString(",")).apply()
    }
}
