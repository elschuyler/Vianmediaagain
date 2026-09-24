package com.example.ime.quicknotes

import android.content.Context
import android.util.AttributeSet
import com.example.ime.cards.EntryType
import com.example.ime.cards.VianCardModalView

/**
 * Compatibility wrapper for VianQuickNotesModalView backed by the unified VianCardModalView.
 */
class VianQuickNotesModalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VianCardModalView(context, EntryType.PROMPT, attrs, defStyleAttr)
