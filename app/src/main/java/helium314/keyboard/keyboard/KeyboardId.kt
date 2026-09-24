package helium314.keyboard.keyboard

import com.example.RichInputMethodSubtype

class KeyboardId(
    val element: Int,
    val subtype: RichInputMethodSubtype,
    val width: Int,
    val height: Int,
    val mode: Int,
    val inputType: Int = 0,
    val imeOptions: Int = 0,
    val imeAction: Int = 0,
    val deviceLocked: Boolean = false,
    val numberRowEnabled: Boolean = false,
    val numberRowInSymbols: Boolean = false,
    val languageSwitchKeyEnabled: Boolean = false,
    val emojiKeyEnabled: Boolean = false,
    val customActionLabel: String? = null,
    val hasShortcutKey: Boolean = false,
    val isSplitLayout: Boolean = false,
    val oneHandedModeEnabled: Boolean = false,
    val internalAction: String? = null,
    val emojiSearchAvailable: Boolean = false
)
