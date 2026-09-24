package com.example.ime.clipboard

import android.content.Context
import android.util.AttributeSet
import com.example.ime.cards.EntryType
import com.example.ime.cards.VianCardModalView

/**
 * Compatibility wrapper for VianClipboardModalView backed by the unified VianCardModalView.
 */
class VianClipboardModalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VianCardModalView(context, EntryType.CLIPBOARD, attrs, defStyleAttr)
