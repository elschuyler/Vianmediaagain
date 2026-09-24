package com.example.foundation.utils

import com.example.foundation.common.Constants
import com.example.foundation.common.LocaleUtils
import java.util.Locale

/**
 * Immutable representation of a keyboard subtype, decoupling layout definitions
 * from Android system framework classes for pure JVM testability and fast switching.
 */
data class Subtype(
    val locale: String,
    val keyboardLayoutSet: String = "qwerty",
    val mode: String = Constants.Subtype.KEYBOARD_MODE,
    val displayName: String = "",
    val isAsciiCapable: Boolean = true,
    val extraValue: String = ""
) {
    val javaLocale: Locale by lazy {
        LocaleUtils.constructLocale(locale)
    }

    val displayIndicator: String by lazy {
        LocaleUtils.getDisplayIndicator(javaLocale)
    }

    val isRtl: Boolean by lazy {
        LocaleUtils.isRtl(javaLocale)
    }

    companion object {
        val DEFAULT = Subtype(
            locale = "en_US",
            keyboardLayoutSet = "qwerty",
            mode = Constants.Subtype.KEYBOARD_MODE,
            displayName = "English (US)",
            isAsciiCapable = true
        )

        val FRENCH = Subtype(
            locale = "fr",
            keyboardLayoutSet = "azerty",
            mode = Constants.Subtype.KEYBOARD_MODE,
            displayName = "Français",
            isAsciiCapable = true
        )
    }
}
