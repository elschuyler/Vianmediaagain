package com.example.ime.quicknotes

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.R

/**
 * Translucent dialog Activity for adding or editing a Quick Note.
 * Opens as an Android Dialog window, allowing the native VianBoard IME
 * to appear below it and type naturally into the EditText.
 */
class QuickNoteEditActivity : Activity() {

    companion object {
        const val EXTRA_OLD_TEXT = "extra_old_text"
        const val EXTRA_IS_NEW = "extra_is_new"
    }

    private lateinit var storage: QuickNotesStorage
    private var oldText: String? = null
    private var isNew: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_quick_note_edit)

        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        storage = QuickNotesStorage(this)
        oldText = intent.getStringExtra(EXTRA_OLD_TEXT)
        isNew = intent.getBooleanExtra(EXTRA_IS_NEW, false)

        val tvTitle = findViewById<TextView>(R.id.tvDialogTitle)
        val etNoteContent = findViewById<EditText>(R.id.etNoteContent)
        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnSave = findViewById<Button>(R.id.btnSave)

        if (isNew) {
            tvTitle.text = "New Quick Note"
            etNoteContent.setText(oldText ?: "")
        } else {
            tvTitle.text = "Edit Quick Note"
            etNoteContent.setText(oldText ?: "")
        }

        etNoteContent.setSelection(etNoteContent.text.length)
        etNoteContent.requestFocus()
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        btnCancel.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            val updated = etNoteContent.text.toString().trim()
            if (updated.isEmpty()) {
                Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isNew) {
                storage.addNote(updated)
                Toast.makeText(this, "Quick Note saved", Toast.LENGTH_SHORT).show()
            } else {
                val original = oldText ?: ""
                storage.updateNote(original, updated)
                Toast.makeText(this, "Quick Note updated", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
