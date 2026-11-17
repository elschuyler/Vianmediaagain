package shura.media.ui

import android.content.Intent
import android.net.Uri
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
import shura.media.LogKeeper
import shura.media.service.PlayerManager
import shura.media.ui.screens.PlayerScreen
import shura.media.ui.theme.MyApplicationTheme

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
                try {
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        return shura.media.data.MediaUriResolver.extractCleanTitleFromUri(uri, "Video")
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return shura.media.data.MediaUriResolver.queryCleanDisplayName(this, uri)
    }

    private fun resolveFastUriOnce(rawUri: Uri): Uri {
        if (rawUri.scheme == "content" && rawUri.authority == android.provider.MediaStore.AUTHORITY) {
            return rawUri
        }
        return shura.media.data.MediaUriResolver.resolveToPlayableUri(this, rawUri)
    }

    private fun encodeBase64Url(raw: String): String {
        val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        return android.util.Base64.encodeToString(raw.toByteArray(), base64Flags)
    }

    private fun preparePlaybackImmediately(encodedUri: String, preResolvedUri: Uri? = null) {
        if (encodedUri.isEmpty()) return
        val decodedStr = try {
            String(android.util.Base64.decode(encodedUri, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        } catch (e: Exception) { encodedUri }
        if (decodedStr.isEmpty()) return
        val rawUri = android.net.Uri.parse(decodedStr)
        val uri = preResolvedUri ?: resolveFastUriOnce(rawUri)
        val currentPlayer = PlayerManager.exoPlayer
        val pending = PlayerManager.consumePendingQueueWithImplicitFlag()
        val activeMediaItem = currentPlayer?.currentMediaItem
        val matchesActiveItem = activeMediaItem != null && (
            activeMediaItem.mediaId == decodedStr ||
                activeMediaItem.localConfiguration?.uri?.toString() == decodedStr
            )
        if ((pending != null && pending.first.isNotEmpty()) || !matchesActiveItem) {
            val settings = shura.media.data.SettingsManager.getInstance(applicationContext)
            val fallbackRawName = shura.media.data.MediaUriResolver.extractCleanTitleFromUri(rawUri, "Video")
            val fastName = shura.media.data.MediaUriResolver.extractCleanTitleFromUri(uri, fallbackRawName)
            val activeItemUri = pending?.first?.getOrNull(pending.second)?.localConfiguration?.uri ?: uri
            val isAudio = PlayerManager.isAudioOnlyHint(applicationContext, activeItemUri) ||
                PlayerManager.isAudioOnlyHint(applicationContext, rawUri)
            val sessionMediaId = pending?.first?.getOrNull(pending.second)?.mediaId ?: decodedStr
            PlayerManager.initSessionBackgroundPlayForNewMedia(isAudio, settings.defaultAudioBackgroundPlay, sessionMediaId)

            if (pending != null && pending.first.isNotEmpty()) {
                val (queueItems, queueIndex, isImplicit) = pending
                val activeItem = queueItems.getOrNull(queueIndex)
                val activeName = shura.media.data.MediaUriResolver.cleanDisplayName(
                    activeItem?.mediaMetadata?.title?.toString(),
                    fastName
                )
                val lastPos = settings.getPlaybackPosition(decodedStr, activeName)
                val startPos = if (lastPos > 0 && !settings.isFinished(decodedStr, activeName)) lastPos else 0L
                PlayerManager.stagePlaybackRequest(
                    mediaItems = queueItems,
                    startIndex = queueIndex,
                    startPositionMs = startPos,
                    isImplicit = isImplicit,
                    appendIfActive = false
                )
                settings.markAsOpened(decodedStr, activeName)
            } else {
                val siblingSubs = if (uri.scheme == "file" || (uri.scheme == null && uri.path?.startsWith("/") == true)) {
                    shura.media.service.SubtitleAutoDetector.detectSiblingSubtitles(applicationContext, uri)
                } else {
                    emptyList()
                }
                val itemBuilder = androidx.media3.common.MediaItem.Builder()
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
                    itemBuilder.setSubtitleConfigurations(siblingSubs)
                }
                val mediaItem = itemBuilder.build()
                val lastPos = settings.getPlaybackPosition(decodedStr, fastName)
                val startPos = if (lastPos > 0 && !settings.isFinished(decodedStr, fastName)) lastPos else 0L
                PlayerManager.stagePlaybackRequest(
                    mediaItems = listOf(mediaItem),
                    startIndex = 0,
                    startPositionMs = startPos,
                    isImplicit = true,
                    appendIfActive = false
                )
                settings.markAsOpened(decodedStr, fastName)
            }
            shura.media.service.PlaybackService.applyStagedPlaybackOrStart(applicationContext)

            // Resolve content:// display name and sibling subtitles asynchronously on IO
            if (uri.scheme == "content" || rawUri.scheme == "content") {
                lifecycleScope.launch(Dispatchers.IO) {
                    val resolvedName = queryDisplayName(uri)
                        ?: if (rawUri != uri) queryDisplayName(rawUri) else null
                    val asyncSubs = shura.media.service.SubtitleAutoDetector.detectSiblingSubtitles(applicationContext, uri)
                    val hasCandidateName = !resolvedName.isNullOrBlank() && resolvedName != fastName

                    if (hasCandidateName) {
                        withContext(Dispatchers.Main) {
                            PlayerManager.updateActiveTitleOverride(decodedStr, resolvedName!!)
                        }
                    }

                    if (hasCandidateName || asyncSubs.isNotEmpty()) {
                        var waitedMs = 0L
                        while (waitedMs < 2000L) {
                            val ready = withContext(Dispatchers.Main) {
                                val p = PlayerManager.exoPlayer
                                p != null && p.currentMediaItem?.mediaId == decodedStr &&
                                    p.playbackState == androidx.media3.common.Player.STATE_READY
                            }
                            if (ready) break
                            kotlinx.coroutines.delay(100L)
                            waitedMs += 100L
                        }
                        withContext(Dispatchers.Main) {
                            val activePlayer = PlayerManager.exoPlayer
                            val activeItem = activePlayer?.currentMediaItem
                            if (activePlayer != null && activeItem?.mediaId == decodedStr) {
                                val currentTitle = activeItem.mediaMetadata.title?.toString()
                                val shouldUpdateName = hasCandidateName && (currentTitle.isNullOrBlank() || currentTitle == fastName)
                                val existingConfigs = activeItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                                val newSubs = asyncSubs.filter { candidate ->
                                    existingConfigs.none { it.uri == candidate.uri }
                                }
                                if (shouldUpdateName || newSubs.isNotEmpty()) {
                                    val updatedMeta = activeItem.mediaMetadata.buildUpon().apply {
                                        if (shouldUpdateName) {
                                            setTitle(resolvedName).setDisplayTitle(resolvedName)
                                        }
                                    }.build()
                                    val updatedItemBuilder = activeItem.buildUpon().setMediaMetadata(updatedMeta)
                                    if (newSubs.isNotEmpty()) {
                                        updatedItemBuilder.setSubtitleConfigurations(existingConfigs + newSubs)
                                    }
                                    activePlayer.replaceMediaItem(activePlayer.currentMediaItemIndex, updatedItemBuilder.build())
                                    if (shouldUpdateName) {
                                        settings.markAsOpened(decodedStr, resolvedName!!)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        persistUriPermissions(intent)
        val newUri = extractEncodedUri(intent)
        if (newUri.isNotEmpty()) {
            val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            val decodedNew = try {
                val dec = String(android.util.Base64.decode(newUri, base64Flags))
                if (dec.contains("://") || dec.startsWith("/")) dec else newUri
            } catch (e: Exception) { newUri }
            val resolved = resolveFastUriOnce(Uri.parse(decodedNew))
            val finalNewUri = if (resolved.toString() != decodedNew) {
                encodeBase64Url(resolved.toString())
            } else newUri

            if (finalNewUri != activeUriString || PlayerManager.hasPendingQueue()) {
                activeUriString = finalNewUri
                preparePlaybackImmediately(finalNewUri, resolved)
            }
        } else {
            val activeItem = PlayerManager.exoPlayer?.currentMediaItem
            val activeMediaUri = activeItem?.mediaId?.takeIf { it.isNotBlank() }
                ?: activeItem?.localConfiguration?.uri?.toString()
            if (!activeMediaUri.isNullOrBlank()) {
                val encodedActive = encodeBase64Url(activeMediaUri)
                if (encodedActive != activeUriString) {
                    activeUriString = encodedActive
                }
            }
        }
        PlayerManager.floatingSession.isMinimized = false
        shura.media.service.PlaybackService.hideOverlay(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogKeeper.init(this)
        persistUriPermissions(intent)
        var rawEncoded = extractEncodedUri(intent)
        if (rawEncoded.isEmpty()) {
            val activeItem = PlayerManager.exoPlayer?.currentMediaItem
            val activeMediaUri = activeItem?.mediaId?.takeIf { it.isNotBlank() }
                ?: activeItem?.localConfiguration?.uri?.toString()
            if (!activeMediaUri.isNullOrBlank()) {
                rawEncoded = encodeBase64Url(activeMediaUri)
            } else if (!PlayerManager.hasStagedPlaybackRequest() && !PlayerManager.hasPendingQueue()) {
                try {
                    val mainIntent = Intent(this, shura.media.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(mainIntent)
                } catch (_: Exception) {}
                finish()
                return
            }
        }
        val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        val decodedStr = try {
            val decoded = String(android.util.Base64.decode(rawEncoded, base64Flags))
            if (decoded.contains("://") || decoded.startsWith("/")) decoded else rawEncoded
        } catch (e: Exception) { rawEncoded }
        val resolvedUri = if (decodedStr.isNotEmpty()) {
            resolveFastUriOnce(Uri.parse(decodedStr))
        } else null
        activeUriString = if (resolvedUri != null && resolvedUri.toString() != decodedStr) {
            encodeBase64Url(resolvedUri.toString())
        } else rawEncoded

        if (decodedStr.startsWith("content://")) {
            try {
                grantUriPermission(packageName, Uri.parse(decodedStr), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
        }
        lifecycleScope.launch {
            PlayerManager.playbackEndedCloseEvent.collect {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }
        PlayerManager.floatingSession.isMinimized = false
        shura.media.service.PlaybackService.hideOverlay(this)
        PlayerManager.isPlayerScreenVisible = true
        preparePlaybackImmediately(activeUriString, resolvedUri)

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
            val settings = remember { shura.media.data.SettingsManager.getInstance(applicationContext) }
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
                            val decodedStr = try {
                                val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                                val decoded = String(android.util.Base64.decode(activeUriString, base64Flags))
                                if (decoded.contains("://") || decoded.startsWith("/")) decoded else activeUriString
                            } catch (e: Exception) {
                                activeUriString
                            }
                            val targetUri = Uri.parse(decodedStr)
                            data = targetUri
                            clipData = android.content.ClipData.newRawUri("media", targetUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(editorIntent)
                    }
                )
            }
        }
    }
}
