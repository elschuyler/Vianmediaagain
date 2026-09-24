package com.example.ime.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import com.example.R
import com.example.logger.LogViewerActivity

class AdvancedSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_settings)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        // 1. Log Keeper
        findViewById<LinearLayout>(R.id.cardLogKeeper).setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        // 2. Backup & Restore
        findViewById<LinearLayout>(R.id.cardBackupRestore).setOnClickListener {
            startActivity(Intent(this, BackupRestoreSettingsActivity::class.java))
        }
    }
}
