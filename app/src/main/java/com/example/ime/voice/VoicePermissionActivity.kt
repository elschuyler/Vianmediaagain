package com.example.ime.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object VoicePermissionBridge {
    var onPermissionResult: ((Boolean) -> Unit)? = null

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRecordAudioPermission(context: Context, callback: (Boolean) -> Unit) {
        if (hasRecordAudioPermission(context)) {
            callback(true)
            return
        }
        onPermissionResult = callback
        val intent = Intent(context, VoicePermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }
}

class VoicePermissionActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        VoicePermissionBridge.onPermissionResult?.invoke(isGranted)
        VoicePermissionBridge.onPermissionResult = null
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (VoicePermissionBridge.hasRecordAudioPermission(this)) {
            VoicePermissionBridge.onPermissionResult?.invoke(true)
            VoicePermissionBridge.onPermissionResult = null
            finish()
            overridePendingTransition(0, 0)
            return
        }
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
