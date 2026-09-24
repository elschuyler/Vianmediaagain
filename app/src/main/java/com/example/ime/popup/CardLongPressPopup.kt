package com.example.ime.popup

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.example.R

/**
 * Lightweight, compact on-demand popup anchored to cards in Clipboard and Quick Notes.
 * Uses authentic HeliBoard styling and vector icons.
 */
class CardLongPressPopup(
    private val context: Context,
    private val isPinned: Boolean,
    private val middleActionText: String, // "To Notes" or "Edit"
    private val middleActionIconRes: Int, // R.drawable.ic_prompt_list or R.drawable.ic_edit_pencil
    private val onPinToggle: () -> Unit,
    private val onMiddleAction: () -> Unit,
    private val onDelete: () -> Unit
) {

    private val popupWindow: PopupWindow
    private val contentView: View

    init {
        contentView = LayoutInflater.from(context).inflate(R.layout.view_card_longpress_popup, null)

        val btnPin = contentView.findViewById<LinearLayout>(R.id.btnPopupPin)
        val ivPinIcon = contentView.findViewById<ImageView>(R.id.ivPopupPinIcon)
        val tvPinText = contentView.findViewById<TextView>(R.id.tvPopupPinText)

        if (isPinned) {
            tvPinText.text = "Unpin"
            ivPinIcon.setColorFilter(0xFF3B82F6.toInt())
        } else {
            tvPinText.text = "Pin"
            ivPinIcon.setColorFilter(0xFF475569.toInt())
        }

        btnPin.setOnClickListener {
            popupWindow.dismiss()
            onPinToggle()
        }

        val btnAction = contentView.findViewById<LinearLayout>(R.id.btnPopupAction)
        val ivActionIcon = contentView.findViewById<ImageView>(R.id.ivPopupActionIcon)
        val tvActionText = contentView.findViewById<TextView>(R.id.tvPopupActionText)

        tvActionText.text = middleActionText
        ivActionIcon.setImageResource(middleActionIconRes)
        ivActionIcon.setColorFilter(0xFF475569.toInt())

        btnAction.setOnClickListener {
            popupWindow.dismiss()
            onMiddleAction()
        }

        val btnDelete = contentView.findViewById<LinearLayout>(R.id.btnPopupDelete)
        btnDelete.setOnClickListener {
            popupWindow.dismiss()
            onDelete()
        }

        popupWindow = PopupWindow(
            contentView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 16f
        }
    }

    fun show(anchorView: View) {
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = contentView.measuredHeight
        val popupWidth = contentView.measuredWidth

        val xOffset = (anchorView.width - popupWidth) / 2
        val yOffset = -anchorView.height - popupHeight - 8

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }
}
