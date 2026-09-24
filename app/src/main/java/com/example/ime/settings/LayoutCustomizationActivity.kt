package com.example.ime.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.R

class LayoutCustomizationActivity : Activity() {

    private lateinit var commaPrefs: CommaPreferences
    private lateinit var tvCommaSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_customization)

        commaPrefs = CommaPreferences(this)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        tvCommaSummary = findViewById(R.id.tvCommaSummary)
        updateCommaSummary()

        findViewById<LinearLayout>(R.id.cardCommaCustomization).setOnClickListener {
            showCommaCustomizationDialog()
        }

        findViewById<LinearLayout>(R.id.cardToolbar).setOnClickListener {
            startActivity(Intent(this, ToolbarSettingsActivity::class.java))
        }
    }

    private fun updateCommaSummary() {
        val selected = commaPrefs.getSelectedSlots()
        tvCommaSummary.text = "Settings (fixed), " + selected.joinToString(", ")
    }

    private fun showCommaCustomizationDialog() {
        val allItems = CommaPreferences.AVAILABLE_ITEMS
        val itemKeys = allItems.map { it.first }.toTypedArray()
        val itemLabels = allItems.map { it.second }.toTypedArray()

        val currentlySelected = commaPrefs.getSelectedSlots().toMutableSet()
        val checkedItems = BooleanArray(itemKeys.size) { i ->
            currentlySelected.contains(itemKeys[i])
        }

        AlertDialog.Builder(this)
            .setTitle("Comma Key: Choose 4 Items")
            .setMultiChoiceItems(itemLabels, checkedItems) { _, which, isChecked ->
                val key = itemKeys[which]
                if (isChecked) {
                    if (currentlySelected.size >= 4) {
                        checkedItems[which] = false
                        Toast.makeText(this, "Maximum 4 items allowed. Deselect one first.", Toast.LENGTH_SHORT).show()
                    } else {
                        currentlySelected.add(key)
                    }
                } else {
                    currentlySelected.remove(key)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                commaPrefs.setSelectedSlots(currentlySelected.toList())
                updateCommaSummary()
                Toast.makeText(this, "Comma key layout updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset Default") { _, _ ->
                commaPrefs.setSelectedSlots(CommaPreferences.DEFAULT_SLOTS)
                updateCommaSummary()
                Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
