package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.ime.settings.SettingsActivity
import com.example.ime.setup.SetupUtils
import com.example.ime.setup.SetupWizardActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEnabled = SetupUtils.isKeyboardEnabled(this)
        val isSelected = SetupUtils.isKeyboardSelected(this)

        if (isEnabled && isSelected) {
            // Already fully setup: go directly to Settings
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            // Needs onboarding: open Setup Wizard
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
        finish()
    }
}
