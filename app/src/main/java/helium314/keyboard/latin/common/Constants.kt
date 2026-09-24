package helium314.keyboard.latin.common

import com.example.foundation.common.Constants as FoundationConstants

object Constants {
    const val CODE_ENTER = FoundationConstants.CODE_ENTER
    const val CODE_TAB = FoundationConstants.CODE_TAB
    const val CODE_SPACE = FoundationConstants.CODE_SPACE
    const val CODE_PERIOD = FoundationConstants.CODE_PERIOD
    const val CODE_COMMA = FoundationConstants.CODE_COMMA
    const val CODE_DASH = FoundationConstants.CODE_DASH
    const val CODE_SINGLE_QUOTE = FoundationConstants.CODE_SINGLE_QUOTE
    const val CODE_DOUBLE_QUOTE = FoundationConstants.CODE_DOUBLE_QUOTE
    const val CODE_DELETE = FoundationConstants.CODE_DELETE
    const val CODE_BACKSPACE = FoundationConstants.CODE_BACKSPACE
    const val CODE_SHIFT = FoundationConstants.CODE_SHIFT
    const val CODE_SWITCH_ALPHA_SYMBOL = FoundationConstants.CODE_SWITCH_ALPHA_SYMBOL
    const val CODE_OUTPUT_TEXT = FoundationConstants.CODE_OUTPUT_TEXT
    const val CODE_ACTION_NEXT = FoundationConstants.CODE_ACTION_NEXT
    const val CODE_ACTION_PREVIOUS = FoundationConstants.CODE_ACTION_PREVIOUS
    const val CODE_LANGUAGE_SWITCH = FoundationConstants.CODE_LANGUAGE_SWITCH
    const val CODE_EMOJI = FoundationConstants.CODE_EMOJI
    const val CODE_ALPHA_FROM_SYMBOLS = FoundationConstants.CODE_ALPHA_FROM_SYMBOLS
    const val CODE_SETTINGS = FoundationConstants.CODE_SETTINGS
    const val CODE_SHORTCUT = FoundationConstants.CODE_SHORTCUT
    const val CODE_NOT_A_KEY = FoundationConstants.CODE_NOT_A_KEY

    const val NOT_A_COORDINATE = FoundationConstants.NOT_A_COORDINATE
    const val NOT_A_CODE = FoundationConstants.NOT_A_CODE

    object Subtype {
        const val KEYBOARD_MODE = "keyboard"
        const val VOICE_MODE = "voice"
    }

    object Correction {
        const val AUTO_CORRECTION = 1
        const val SUGGESTION = 2
    }
}
