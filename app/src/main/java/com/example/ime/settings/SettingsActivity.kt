package com.example.ime.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.example.R
import com.example.ime.setup.SetupUtils
import com.example.ime.setup.SetupWizardActivity

class SettingsActivity : Activity() {

    private lateinit var layoutImeInactiveBanner: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        layoutImeInactiveBanner = findViewById(R.id.layoutImeInactiveBanner)
        findViewById<Button>(R.id.btnSwitchKeyboardBanner).setOnClickListener {
            SetupUtils.showInputMethodPicker(this)
        }

        // 0. Keyboard Setup & Switcher Card
        findViewById<LinearLayout>(R.id.cardSetupWizard).setOnClickListener {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }

        // 1. Appearance (Sliders & Desktop Shortcuts subpage)
        findViewById<LinearLayout>(R.id.cardAppearance).setOnClickListener {
            startActivity(Intent(this, AppearanceSettingsActivity::class.java))
        }

        // 2. Layout Customization (Comma popup & Toolbar tools)
        findViewById<LinearLayout>(R.id.cardLayoutCustomization).setOnClickListener {
            startActivity(Intent(this, LayoutCustomizationActivity::class.java))
        }

        // 3. Voice Input
        findViewById<LinearLayout>(R.id.cardVoiceInput).setOnClickListener {
            startActivity(Intent(this, VoiceInputSettingsActivity::class.java))
        }

        // 4. Security Vault
        findViewById<LinearLayout>(R.id.cardSecurityVault).setOnClickListener {
            startActivity(Intent(this, SecurityVaultSettingsActivity::class.java))
        }

        // 5. Text Engine (Safeguards & Performance & Personal Dictionary)
        findViewById<LinearLayout>(R.id.cardTextEngine).setOnClickListener {
            startActivity(Intent(this, TextEngineSettingsActivity::class.java))
        }

        // 6. Advanced (Log Keeper & Backup/Restore)
        findViewById<LinearLayout>(R.id.cardAdvanced).setOnClickListener {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateActiveKeyboardBanner()
    }

    private fun updateActiveKeyboardBanner() {
        val isSelected = SetupUtils.isKeyboardSelected(this)
        layoutImeInactiveBanner.visibility = if (isSelected) View.GONE else View.VISIBLE
    }
}
