package com.example.ime.settings

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.example.R
import com.example.ime.quicknotes.QuickNotesStorage

/**
 * Settings Activity for Quick Notes.
 * Allows configuring the transfer mode when moving items from Clipboard (Copy vs. Cut).
 */
class QuickNotesSettingsActivity : Activity() {

    private lateinit var storage: QuickNotesStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_notes_settings)

        storage = QuickNotesStorage(this)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val rgMoveMode = findViewById<RadioGroup>(R.id.rgMoveMode)
        val rbCopy = findViewById<RadioButton>(R.id.rbCopy)
        val rbCut = findViewById<RadioButton>(R.id.rbCut)

        val isCut = storage.isMoveModeCut(this)
        if (isCut) {
            rbCut.isChecked = true
        } else {
            rbCopy.isChecked = true
        }

        rgMoveMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCut) {
                storage.setMoveMode(this, "cut")
                Toast.makeText(this, "Set to Move (delete from clipboard)", Toast.LENGTH_SHORT).show()
            } else {
                storage.setMoveMode(this, "copy")
                Toast.makeText(this, "Set to Copy (keep in clipboard)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
