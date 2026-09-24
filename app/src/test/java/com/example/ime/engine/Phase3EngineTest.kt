package com.example.ime.engine

import com.example.ime.engine.TextEngineBridge.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3EngineTest {

    @Test
    fun testLanguageModeIndicators() {
        assertEquals("EN", LanguageMode.ENGLISH.indicator)
        assertEquals("FR", LanguageMode.FRENCH.indicator)
        assertEquals("EN • FR", LanguageMode.DUAL.indicator)
    }

    @Test
    fun testLanguageModeDisplayNames() {
        assertEquals("English", LanguageMode.ENGLISH.displayName)
        assertEquals("Français", LanguageMode.FRENCH.displayName)
        assertEquals("Dual (EN + FR)", LanguageMode.DUAL.displayName)
    }

    @Test
    fun testLanguageModeEnumValues() {
        val modes = LanguageMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(LanguageMode.ENGLISH))
        assertTrue(modes.contains(LanguageMode.FRENCH))
        assertTrue(modes.contains(LanguageMode.DUAL))
    }
}
