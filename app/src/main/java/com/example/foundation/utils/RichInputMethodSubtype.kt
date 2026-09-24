package com.example.foundation.utils

import android.view.inputmethod.InputMethodSubtype
import com.example.foundation.common.LocaleUtils
import java.util.Locale

/**
 * Rich wrapper around Android's [InputMethodSubtype], offering null-safe
 * access to keyboard locales, layout sets, display names, and script metadata.
 */
class RichInputMethodSubtype(val rawSubtype: InputMethodSubtype?) {

    val locale: Locale by lazy {
        SubtypeLocaleUtils.getSubtypeLocale(rawSubtype)
    }

    val localeString: String
        get() = rawSubtype?.locale ?: "en_US"

    val mode: String
        get() = rawSubtype?.mode ?: SubtypeLocaleUtils.SUBTYPE_MODE_KEYBOARD

    val keyboardLayoutSetName: String by lazy {
        SubtypeLocaleUtils.getKeyboardLayoutSetName(rawSubtype)
    }

    val isAsciiCapable: Boolean by lazy {
        SubtypeLocaleUtils.isAsciiCapable(rawSubtype)
    }

    val displayName: String by lazy {
        SubtypeLocaleUtils.getSubtypeDisplayName(rawSubtype)
    }

    val displayIndicator: String by lazy {
        SubtypeLocaleUtils.getSubtypeIndicator(rawSubtype)
    }

    val isRtl: Boolean by lazy {
        LocaleUtils.isRtl(locale)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RichInputMethodSubtype) return false
        return rawSubtype == other.rawSubtype
    }

    override fun hashCode(): Int {
        return rawSubtype?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "RichInputMethodSubtype(locale=$locale, layout=$keyboardLayoutSetName, mode=$mode)"
    }

    companion object {
        fun get(subtype: InputMethodSubtype?): RichInputMethodSubtype {
            return RichInputMethodSubtype(subtype)
        }

        fun createDummy(localeString: String, layoutSet: String): RichInputMethodSubtype {
            val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale(localeString)
                .setSubtypeMode(SubtypeLocaleUtils.SUBTYPE_MODE_KEYBOARD)
                .setSubtypeExtraValue("KeyboardLocale=$localeString,KeyboardLayoutSet=$layoutSet,AsciiCapable")
                .build()
            return RichInputMethodSubtype(subtype)
        }
    }
}
