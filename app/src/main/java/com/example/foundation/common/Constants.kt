package com.example.foundation.common

/**
 * Fundamental constants for the VianBoard IME engine, including key codes,
 * layout identifiers, coordinate sentinels, and system limits.
 */
object Constants {
    // Standard ASCII and Control Key Codes
    const val CODE_ENTER = 10
    const val CODE_TAB = 9
    const val CODE_SPACE = 32
    const val CODE_PERIOD = 46
    const val CODE_COMMA = 44
    const val CODE_DASH = 45
    const val CODE_SINGLE_QUOTE = 39
    const val CODE_DOUBLE_QUOTE = 34
    const val CODE_BACKSLASH = 92
    const val CODE_SLASH = 47
    const val CODE_QUESTION_MARK = 63
    const val CODE_EXCLAMATION = 33
    const val CODE_PERCENT = 37
    const val CODE_PLUS = 43
    const val CODE_COLON = 58
    const val CODE_SEMICOLON = 59
    const val CODE_EQUAL = 61
    const val CODE_UNDERSCORE = 95
    const val CODE_AT = 64
    const val CODE_HASH = 35

    // Special Functional Key Codes (Negative internal sentinels)
    const val CODE_SHIFT = -1
    const val CODE_SWITCH_ALPHA_SYMBOL = -2
    const val CODE_OUTPUT_TEXT = -3
    const val CODE_DELETE = -4
    const val CODE_BACKSPACE = -4
    const val CODE_SETTINGS = -5
    const val CODE_SHORTCUT = -6
    const val CODE_ACTION_NEXT = -7
    const val CODE_ACTION_PREVIOUS = -8
    const val CODE_LANGUAGE_SWITCH = -9
    const val CODE_EMOJI = -10
    const val CODE_ALPHA_FROM_SYMBOLS = -11
    const val CODE_SHIFT_ENTER = -12
    const val CODE_SYMBOL_FROM_MORE_SYMBOLS = -13
    const val CODE_CLIPBOARD = -14
    const val CODE_VOICE = -15
    const val CODE_DESKTOP_SHORTCUTS = -16
    const val CODE_SECURITY_VAULT = -17
    const val CODE_QUICK_NOTES = -18
    const val CODE_PROMPT_LIST = -19
    const val CODE_ACTION_ENTER = -20
    const val CODE_NOT_A_KEY = -1000

    // Coordinate & Proximity Sentinels
    const val NOT_A_COORDINATE = -1
    const val NOT_A_CODE = -1
    const val NOT_A_TOUCH_ID = -1

    // Engine Sizing and Limits
    const val MAX_WORD_LENGTH = 48
    const val MAX_SUGGESTIONS = 16
    const val MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3
    const val SUGGESTIONS_STRIP_DEFAULT_COUNT = 3

    // Color sentinels
    const val COLOR_TRANSPARENT = 0

    // Layout Modes
    const val MODE_TEXT = 0
    const val MODE_URL = 1
    const val MODE_EMAIL = 2
    const val MODE_IM = 3
    const val MODE_PHONE = 4
    const val MODE_NUMBER = 5
    const val MODE_DATE = 6
    const val MODE_TIME = 7
    const val MODE_DATETIME = 8

    /**
     * Subtype and layout specification constants.
     */
    object Subtype {
        const val KEYBOARD_MODE = "keyboard"
        const val EXTRA_VALUE_KEYBOARD_LOCALE = "KeyboardLocale"
        const val EXTRA_VALUE_ASCII_CAPABLE = "AsciiCapable"
        const val EXTRA_VALUE_SUPPORT_TOUCH_POSITION_CORRECTION = "SupportTouchPositionCorrection"
        const val EXTRA_VALUE_EMOJI_CAPABLE = "EmojiCapable"
        const val DEFAULT_SUBTYPE_MODE = "keyboard"
        const val DEFAULT_LOCALE = "en_US"
    }

    /**
     * Text correction and prediction thresholds.
     */
    object Correction {
        const val AUTOCORRECT_THRESHOLD_OFF = 0
        const val AUTOCORRECT_THRESHOLD_MODEST = 1
        const val AUTOCORRECT_THRESHOLD_AGGRESSIVE = 2
        const val AUTOCORRECT_THRESHOLD_VERY_AGGRESSIVE = 3

        const val SCORE_EXACT_MATCH = 2000000
        const val SCORE_HIGH_FREQUENCY = 1000000
        const val SCORE_PENALTY_DEMOTED = -500000
    }
}
