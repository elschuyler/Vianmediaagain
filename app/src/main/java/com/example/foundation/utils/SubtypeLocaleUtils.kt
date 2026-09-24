package com.example.foundation.utils

import android.view.inputmethod.InputMethodSubtype
import com.example.foundation.common.Constants
import com.example.foundation.common.LocaleUtils
import java.util.Locale

/**
 * Subtype locale lookup, layout name resolution, and script detection utilities.
 */
object SubtypeLocaleUtils {

    const val SUBTYPE_MODE_KEYBOARD = Constants.Subtype.KEYBOARD_MODE
    private const val EXTRA_VALUE_KEYBOARD_LOCALE = Constants.Subtype.EXTRA_VALUE_KEYBOARD_LOCALE
    private const val EXTRA_VALUE_ASCII_CAPABLE = Constants.Subtype.EXTRA_VALUE_ASCII_CAPABLE

    /**
     * Resolves the [Locale] represented by an Android [InputMethodSubtype].
     */
    fun getSubtypeLocale(subtype: InputMethodSubtype?): Locale {
        if (subtype == null) return Locale.getDefault()

        // Check extraValue for explicit KeyboardLocale tag
        val extraValue = subtype.extraValue
        if (!extraValue.isNullOrEmpty()) {
            val pairs = extraValue.split(',')
            for (pair in pairs) {
                val kv = pair.split('=')
                if (kv.size == 2 && kv[0].trim() == EXTRA_VALUE_KEYBOARD_LOCALE) {
                    return LocaleUtils.constructLocale(kv[1].trim())
                }
            }
        }

        // Fallback to subtype locale string
        val localeString = subtype.locale
        return if (localeString.isNotEmpty()) {
            LocaleUtils.constructLocale(localeString)
        } else {
            Locale.getDefault()
        }
    }

    /**
     * Checks whether the subtype declares ASCII typing capability.
     */
    fun isAsciiCapable(subtype: InputMethodSubtype?): Boolean {
        if (subtype == null) return false
        val extraValue = subtype.extraValue ?: return false
        return extraValue.contains(EXTRA_VALUE_ASCII_CAPABLE)
    }

    /**
     * Resolves a clean display name for the subtype (e.g., "English (US)", "Français").
     */
    fun getSubtypeDisplayName(subtype: InputMethodSubtype?): String {
        if (subtype == null) return "Default"
        val locale = getSubtypeLocale(subtype)
        return LocaleUtils.getLocaleDisplayName(locale)
    }

    /**
     * Returns the 2-letter uppercase indicator (e.g. "EN", "FR") for keyboard spacebars.
     */
    fun getSubtypeIndicator(subtype: InputMethodSubtype?): String {
        val locale = getSubtypeLocale(subtype)
        return LocaleUtils.getDisplayIndicator(locale)
    }

    /**
     * Resolves the keyboard layout name (e.g. "qwerty", "azerty", "qwertz", "dvorak")
     * from subtype extra values or defaults.
     */
    fun getKeyboardLayoutSetName(subtype: InputMethodSubtype?): String {
        val extra = subtype?.extraValue ?: return "qwerty"
        val pairs = extra.split(',')
        for (pair in pairs) {
            val kv = pair.split('=')
            if (kv.size == 2 && kv[0].trim() == "KeyboardLayoutSet") {
                return kv[1].trim()
            }
        }
        val locale = getSubtypeLocale(subtype)
        return when (locale.language.lowercase(Locale.ROOT)) {
            "fr" -> "azerty"
            "de" -> "qwertz"
            else -> "qwerty"
        }
    }
}
