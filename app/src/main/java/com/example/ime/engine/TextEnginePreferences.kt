package com.example.ime.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferences manager for HeliBoard legacy bug fix toggles and performance modes.
 * Defaults are all set to ON (true) as specified in Phase 2.
 */
class TextEnginePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_FILE_NAME = "vian_text_engine_prefs"

        const val KEY_ATOMIC_WORD_REPLACEMENT = "pref_engine_atomic_word_replacement"
        const val KEY_CURSOR_SYNC_GUARD = "pref_engine_cursor_sync_guard"
        const val KEY_WEB_EDITOR_PERFORMANCE_MODE = "pref_engine_web_editor_performance_mode"
        const val KEY_LITE_MODE = "pref_engine_lite_mode"

        const val DEFAULT_ATOMIC_WORD_REPLACEMENT = true
        const val DEFAULT_CURSOR_SYNC_GUARD = true
        const val DEFAULT_WEB_EDITOR_PERFORMANCE_MODE = true
        const val DEFAULT_LITE_MODE = false // Normal Mode by default
    }

    var atomicWordReplacement: Boolean
        get() = prefs.getBoolean(KEY_ATOMIC_WORD_REPLACEMENT, DEFAULT_ATOMIC_WORD_REPLACEMENT)
        set(value) = prefs.edit().putBoolean(KEY_ATOMIC_WORD_REPLACEMENT, value).apply()

    var cursorSyncGuard: Boolean
        get() = prefs.getBoolean(KEY_CURSOR_SYNC_GUARD, DEFAULT_CURSOR_SYNC_GUARD)
        set(value) = prefs.edit().putBoolean(KEY_CURSOR_SYNC_GUARD, value).apply()

    var webEditorPerformanceMode: Boolean
        get() = prefs.getBoolean(KEY_WEB_EDITOR_PERFORMANCE_MODE, DEFAULT_WEB_EDITOR_PERFORMANCE_MODE)
        set(value) = prefs.edit().putBoolean(KEY_WEB_EDITOR_PERFORMANCE_MODE, value).apply()

    var liteMode: Boolean
        get() = prefs.getBoolean(KEY_LITE_MODE, DEFAULT_LITE_MODE)
        set(value) = prefs.edit().putBoolean(KEY_LITE_MODE, value).apply()

    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_ATOMIC_WORD_REPLACEMENT, DEFAULT_ATOMIC_WORD_REPLACEMENT)
            .putBoolean(KEY_CURSOR_SYNC_GUARD, DEFAULT_CURSOR_SYNC_GUARD)
            .putBoolean(KEY_WEB_EDITOR_PERFORMANCE_MODE, DEFAULT_WEB_EDITOR_PERFORMANCE_MODE)
            .putBoolean(KEY_LITE_MODE, DEFAULT_LITE_MODE)
            .apply()
    }
}
