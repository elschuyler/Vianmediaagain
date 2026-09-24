package com.example.ime.toolbar

import com.example.R

enum class ToolbarTool(
    val id: String,
    val titleResId: Int,
    val iconResId: Int,
    val isDefaultExpanded: Boolean,
    val isDefaultPinnedRight: Boolean
) {
    SETTINGS("settings", R.string.tool_settings, R.drawable.ic_settings, true, false),
    CLIPBOARD("clipboard", R.string.tool_clipboard, R.drawable.ic_clipboard, true, true),
    TEXT_EDIT("text_edit", R.string.tool_text_edit, R.drawable.ic_dpad_rounded, true, false),
    THEME("theme", R.string.tool_theme, R.drawable.ic_palette, true, false),
    EMOJI("emoji", R.string.tool_emoji, R.drawable.ic_emoji, true, false),
    NUMBER_ROW("number_row", R.string.tool_number_row, R.drawable.ic_number_row, true, false),
    CLEAR_CLIPBOARD("clear_clipboard", R.string.tool_clear_clipboard, R.drawable.ic_delete_sweep, true, false),
    INCOGNITO("incognito", R.string.tool_incognito, R.drawable.sym_keyboard_incognito_lxx, true, false),
    ONE_HANDED("one_handed", R.string.tool_one_handed, R.drawable.ic_one_hand, true, false),
    FLOATING("floating", R.string.tool_floating, R.drawable.ic_floating_keyboard, true, false),
    UNDO("undo", R.string.tool_undo, R.drawable.ic_undo_rounded, true, false),
    REDO("redo", R.string.tool_redo, R.drawable.ic_redo_rounded, true, false),
    SELECT_WORD("select_word", R.string.tool_select_word, R.drawable.ic_select_word, true, false),
    SELECT_ALL("select_all", R.string.tool_select_all, R.drawable.ic_select_all, false, true),
    COPY("copy", R.string.tool_copy, R.drawable.sym_keyboard_copy_rounded, true, true),
    PASTE("paste", R.string.tool_paste, R.drawable.ic_paste, true, false),
    UP("up", R.string.tool_up, R.drawable.ic_arrow_up, false, false),
    DOWN("down", R.string.tool_down, R.drawable.ic_arrow_down, false, false),
    VOICE("voice", R.string.tool_voice, R.drawable.sym_keyboard_voice_rounded, true, false),
    LOG_KEEPER("log_keeper", R.string.tool_log_keeper, R.drawable.ic_log_keeper, true, false),
    PERSONAL_VAULT("personal_vault", R.string.tool_personal_vault, R.drawable.ic_personal_vault, false, false),
    SECURITY_VAULT("security_vault", R.string.tool_security_vault, R.drawable.ic_security_vault, false, false),
    PROMPT_LIST("prompt_list", R.string.tool_prompt_list, R.drawable.ic_prompt_list, false, false),
    DESKTOP_SHORTCUTS("desktop_shortcuts", R.string.tool_desktop_shortcuts, R.drawable.ic_desktop_shortcuts, false, false);

    companion object {
        fun fromId(id: String): ToolbarTool? = values().firstOrNull { it.id == id }
    }
}
