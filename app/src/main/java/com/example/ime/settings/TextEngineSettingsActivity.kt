package com.example.ime.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.example.R
import com.example.ime.engine.TextEnginePreferences

class TextEngineSettingsActivity : Activity() {

    private lateinit var prefs: TextEnginePreferences
    private lateinit var switchAtomicReplacement: Switch
    private lateinit var switchCursorSyncGuard: Switch
    private lateinit var switchWebPerformance: Switch
    private lateinit var switchLiteMode: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_engine_settings)

        prefs = TextEnginePreferences(this)

        switchAtomicReplacement = findViewById(R.id.switchAtomicReplacement)
        switchCursorSyncGuard = findViewById(R.id.switchCursorSyncGuard)
        switchWebPerformance = findViewById(R.id.switchWebPerformance)
        switchLiteMode = findViewById(R.id.switchLiteMode)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetToDefaults() }

        findViewById<LinearLayout>(R.id.cardPersonalDictionary).setOnClickListener {
            startActivity(Intent(this, PersonalDictionarySettingsActivity::class.java))
        }

        loadPreferences()
        setupListeners()
    }

    private fun loadPreferences() {
        switchAtomicReplacement.isChecked = prefs.atomicWordReplacement
        switchCursorSyncGuard.isChecked = prefs.cursorSyncGuard
        switchWebPerformance.isChecked = prefs.webEditorPerformanceMode
        switchLiteMode.isChecked = prefs.liteMode
    }

    private fun setupListeners() {
        switchAtomicReplacement.setOnCheckedChangeListener { _, isChecked ->
            prefs.atomicWordReplacement = isChecked
        }
        switchCursorSyncGuard.setOnCheckedChangeListener { _, isChecked ->
            prefs.cursorSyncGuard = isChecked
        }
        switchWebPerformance.setOnCheckedChangeListener { _, isChecked ->
            prefs.webEditorPerformanceMode = isChecked
        }
        switchLiteMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.liteMode = isChecked
        }
    }

    private fun resetToDefaults() {
        prefs.resetToDefaults()
        loadPreferences()
        Toast.makeText(this, "Text Engine settings reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
