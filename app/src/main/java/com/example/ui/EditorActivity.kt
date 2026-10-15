package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.LogKeeper
import com.example.service.PlayerManager
import com.example.service.PlaybackService
import com.example.ui.screens.AudioTrimmerScreen
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.theme.MyApplicationTheme

class EditorActivity : ComponentActivity() {

    private var activeUris by mutableStateOf<List<String>>(emptyList())
    private var detectedType by mutableStateOf("image")

    private fun persistUriPermissions(intent: Intent?) {
        if (intent == null) return
        val uris = mutableListOf<Uri>()
        try {
            intent.data?.let { uris.add(it) }
        } catch (e: Exception) {}
        try {
            (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                uris.add(it)
            }
        } catch (e: Exception) {}
        try {
            val clipData = intent.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            }
        } catch (e: Exception) {}
        try {
            val arrayList = intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
            if (arrayList != null) {
                for (parcel in arrayList) {
                    (parcel as? Uri)?.let { uris.add(it) }
                }
            }
        } catch (e: Exception) {}

        for (uri in uris) {
            if (uri.scheme == "content") {
                try {
                    val flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (flags != 0) {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun extractUris(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        val uriSet = LinkedHashSet<String>()

        intent.getStringExtra("uri")?.let { raw ->
            val decoded = try {
                val bytes = android.util.Base64.decode(raw, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                val str = String(bytes)
                if (str.contains("://") || str.startsWith("/")) str else raw
            } catch (e: Exception) { raw }
            if (decoded.isNotBlank()) uriSet.add(decoded)
        }

        try {
            intent.data?.let { uriSet.add(it.toString()) }
        } catch (e: Exception) {}

        try {
            (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                uriSet.add(it.toString())
            }
        } catch (e: Exception) {}

        try {
            val clip = intent.clipData
            if (clip != null && clip.itemCount > 0) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { uriSet.add(it.toString()) }
                }
            }
        } catch (e: Exception) {}

        try {
            val list = intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
            if (list != null) {
                for (parcel in list) {
                    (parcel as? Uri)?.let { uriSet.add(it.toString()) }
                }
            }
        } catch (e: Exception) {}

        return uriSet.filter { it.isNotBlank() }
    }

    private fun classifyMediaType(uris: List<String>, explicitMime: String?): String {
        if (uris.isEmpty()) return "image"
        val firstUriStr = uris.first()
        val uri = Uri.parse(firstUriStr)

        val resolvedMime = try {
            contentResolver.getType(uri)?.lowercase()
        } catch (e: Exception) { null }

        val mime = (resolvedMime ?: explicitMime ?: "").lowercase()
        val ext = firstUriStr.substringAfterLast('.', "").substringBefore('?').lowercase()

        val isAudio = mime.startsWith("audio/") || ext in com.example.data.COMMON_AUDIO_EXTENSIONS
        val isVideo = mime.startsWith("video/") || ext in com.example.data.COMMON_VIDEO_EXTENSIONS

        return when {
            isAudio -> "audio"
            isVideo -> "video"
            else -> "image"
        }
    }

    private fun handleIntentData(intent: Intent?) {
        persistUriPermissions(intent)
        val uris = extractUris(intent)
        activeUris = uris
        detectedType = classifyMediaType(uris, intent?.type)
        LogKeeper.log("EditorActivity launched with ${uris.size} uris, detectedType=$detectedType", "EditorActivity")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentData(intent)
        PlaybackService.hideOverlay(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogKeeper.init(this)
        handleIntentData(intent)

        try {
            PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {}
        PlaybackService.hideOverlay(this)

        if (activeUris.isEmpty()) {
            Toast.makeText(this, "No media file provided to edit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            val settings = remember { com.example.data.SettingsManager.getInstance(applicationContext) }
            val themePref by settings.themePreference.collectAsState()
            val fontPref by settings.fontPreference.collectAsState()

            MyApplicationTheme(themePreference = themePref, fontPreference = fontPref) {
                when (detectedType) {
                    "video" -> {
                        val mainUri = activeUris.first()
                        val joinUris = if (activeUris.size > 1) activeUris.drop(1) else emptyList()
                        VideoEditorScreen(
                            uriString = mainUri,
                            initialJoinUris = joinUris,
                            onNavigateBack = { finish() }
                        )
                    }
                    "audio" -> {
                        AudioTrimmerScreen(
                            uriString = activeUris.first(),
                            onNavigateBack = { finish() }
                        )
                    }
                    else -> {
                        PhotoEditorScreen(
                            uriString = activeUris.first(),
                            onNavigateBack = { finish() }
                        )
                    }
                }
            }
        }
    }
}
