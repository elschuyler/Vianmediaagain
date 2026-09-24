package com.example.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEnginePreferencesTest {

    @Test
    fun testDefaultValuesAreEnabled() {
        // HeliBoard legacy bug fix toggles must default to ON (true)
        assertTrue(TextEnginePreferences.DEFAULT_ATOMIC_WORD_REPLACEMENT)
        assertTrue(TextEnginePreferences.DEFAULT_CURSOR_SYNC_GUARD)
        assertTrue(TextEnginePreferences.DEFAULT_WEB_EDITOR_PERFORMANCE_MODE)
        // Lite Mode defaults to OFF (false)
        org.junit.Assert.assertFalse(TextEnginePreferences.DEFAULT_LITE_MODE)
    }

    @Test
    fun testPreferenceKeyNames() {
        assertEquals("vian_text_engine_prefs", TextEnginePreferences.PREF_FILE_NAME)
        assertEquals("pref_engine_atomic_word_replacement", TextEnginePreferences.KEY_ATOMIC_WORD_REPLACEMENT)
        assertEquals("pref_engine_cursor_sync_guard", TextEnginePreferences.KEY_CURSOR_SYNC_GUARD)
        assertEquals("pref_engine_web_editor_performance_mode", TextEnginePreferences.KEY_WEB_EDITOR_PERFORMANCE_MODE)
        assertEquals("pref_engine_lite_mode", TextEnginePreferences.KEY_LITE_MODE)
    }
}
