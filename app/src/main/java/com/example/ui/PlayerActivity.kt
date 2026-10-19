package com.example.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.LogKeeper
import com.example.service.PlayerManager
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme

class PlayerActivity : ComponentActivity() {

    private var activeUriString by mutableStateOf("")

    private fun persistUriPermissions(intent: Intent?) {
        if (intent == null) return
        val uris = mutableListOf<android.net.Uri>()
        try {
            intent.data?.let { uris.add(it) }
        } catch (e: Exception) {}
        try {
            (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? android.net.Uri)?.let {
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

    private fun extractEncodedUri(intent: Intent?): String {
        if (intent == null) return ""
        val rawUri = intent.getStringExtra("uri")
            ?: intent.data?.toString()
            ?: (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? android.net.Uri)?.toString()
            ?: (intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.toString())
            ?: ""

        if (rawUri.isEmpty()) return ""

        val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        return try {
            val decoded = String(android.util.Base64.decode(rawUri, base64Flags))
            if (decoded.contains("://") || decoded.startsWith("/")) {
                rawUri
            } else {
                android.util.Base64.encodeToString(rawUri.toByteArray(), base64Flags)
            }
        } catch (e: Exception) {
            android.util.Base64.encodeToString(rawUri.toByteArray(), base64Flags)
        }
    }

    private fun getFastImmediateName(uri: android.net.Uri): String {
        val decodedSegment = try {
            val last = uri.lastPathSegment
            if (last != null) android.net.Uri.decode(last) else null
        } catch (e: Exception) { uri.lastPathSegment }
        val segment = decodedSegment?.substringBeforeLast('.')?.ifBlank { null }
            ?: uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null }
        return segment ?: "Video"
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
            try {
                contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }

    private fun preparePlaybackImmediately(encodedUri: String) {
        if (encodedUri.isEmpty()) return
        val decodedStr = try {
            String(android.util.Base64.decode(encodedUri, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        } catch (e: Exception) { encodedUri }
        if (decodedStr.isEmpty()) return
        val uri = android.net.Uri.parse(decodedStr)
        val player = PlayerManager.exoPlayer
        if (player != null && player.currentMediaItem?.mediaId != decodedStr) {
            val fastName = getFastImmediateName(uri)
            val siblingSubs = com.example.service.SubtitleAutoDetector.detectSiblingSubtitles(applicationContext, uri)
            val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
                .setUri(uri)
                .setMediaId(decodedStr)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(fastName)
                        .setDisplayTitle(fastName)
                        .setArtworkUri(uri)
                        .build()
                )
            if (siblingSubs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(siblingSubs)
            }
            val mediaItem = mediaItemBuilder.build()
            val settings = com.example.data.SettingsManager.getInstance(applicationContext)
            val lastPos = settings.getPlaybackPosition(decodedStr, fastName)
            val startPos = if (lastPos > 0 && !settings.isFinished(decodedStr, fastName)) lastPos else 0L
            player.setMediaItem(mediaItem, startPos)
            player.prepare()
            player.play()
            PlayerManager.isImplicitFolderQueue = true
            settings.markAsOpened(decodedStr, fastName)

            // Resolve full display name, video thumbnail, and deferred subtitles asynchronously on IO dispatcher
            lifecycleScope.launch(Dispatchers.IO) {
                val resolvedName = if (uri.scheme == "content") queryDisplayName(uri) else null
                val asyncSubs = if (siblingSubs.isEmpty()) {
                    com.example.service.SubtitleAutoDetector.detectSiblingSubtitles(applicationContext, uri)
                } else emptyList()
                val thumbBytes = extractThumbnailBytes(uri)

                if ((!resolvedName.isNullOrBlank() && resolvedName != fastName) || asyncSubs.isNotEmpty() || thumbBytes != null) {
                    withContext(Dispatchers.Main) {
                        val currentPlayer = PlayerManager.exoPlayer
                        if (currentPlayer != null && currentPlayer.currentMediaItem?.mediaId == decodedStr) {
                            val activeItem = currentPlayer.currentMediaItem
                            val updatedMeta = currentPlayer.mediaMetadata.buildUpon()
                            if (!resolvedName.isNullOrBlank()) {
                                updatedMeta.setTitle(resolvedName).setDisplayTitle(resolvedName)
                            }
                            updatedMeta.setArtworkUri(uri)
                            if (thumbBytes != null) {
                                updatedMeta.setArtworkData(thumbBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                            val updatedItemBuilder = activeItem?.buildUpon()?.setMediaMetadata(updatedMeta.build())
                            if (asyncSubs.isNotEmpty()) {
                                val existingConfigs = activeItem?.localConfiguration?.subtitleConfigurations ?: emptyList()
                                updatedItemBuilder?.setSubtitleConfigurations(existingConfigs + asyncSubs)
                            }
                            val updatedItem = updatedItemBuilder?.build()
                            if (updatedItem != null) {
                                currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, updatedItem)
                                if (!resolvedName.isNullOrBlank()) {
                                    settings.markAsOpened(decodedStr, resolvedName)
                                }
                                try {
                                    applicationContext.sendBroadcast(
                                        Intent("com.example.ACTION_UPDATE_NOTIFICATION").setPackage(packageName)
                                    )
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractThumbnailBytes(uri: Uri): ByteArray? {
        try {
            var bmp: android.graphics.Bitmap? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    bmp = contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null)
                } catch (e: Exception) {}
            }
            if (bmp == null) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(applicationContext, uri)
                    bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320)
                            ?: retriever.frameAtTime
                    } else {
                        retriever.frameAtTime
                    }
                } catch (e: Exception) {}
                finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
            }
            if (bmp != null) {
                val baos = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                bmp.recycle()
                return bytes
            }
        } catch (e: Exception) {
            LogKeeper.logError("PlayerActivity", "Error extracting video thumbnail for notification: ${e.message}", e)
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        persistUriPermissions(intent)
        val newUri = extractEncodedUri(intent)
        if (newUri.isNotEmpty() && newUri != activeUriString) {
            activeUriString = newUri
            preparePlaybackImmediately(newUri)
        }
        com.example.service.PlaybackService.hideOverlay(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogKeeper.init(this)
        persistUriPermissions(intent)
        activeUriString = extractEncodedUri(intent)
        PlayerManager.initialize(applicationContext, false)
        com.example.service.PlaybackService.hideOverlay(this)
        preparePlaybackImmediately(activeUriString)

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
                PlayerScreen(
                    uriString = activeUriString,
                    onNavigateBack = {
                        finish()
                    },
                    onNavigateToEdit = { editRoute ->
                        val editorIntent = Intent(this@PlayerActivity, EditorActivity::class.java).apply {
                            action = Intent.ACTION_EDIT
                            putExtra("uri", activeUriString)
                        }
                        startActivity(editorIntent)
                    }
                )
            }
        }
    }
}
