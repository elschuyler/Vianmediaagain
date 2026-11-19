package shura.media.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import coil.Coil
import coil.request.ImageRequest
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class PlaybackService : MediaSessionService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

private val lifecycleRegistry = LifecycleRegistry(this)
private val store = ViewModelStore()
private val savedStateRegistryController = SavedStateRegistryController.create(this)
private lateinit var windowManager: WindowManager
private var composeView: ComposeView? = null
private var layoutParams: WindowManager.LayoutParams? = null

override val lifecycle: Lifecycle get() = lifecycleRegistry
override val viewModelStore: ViewModelStore get() = store
override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

private var mediaSession: MediaSession? = null
private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
private var decoderServiceRetryCount = 0
@Volatile
private var isRecoveringCodecError = false
private var codecRecoveryJob: Job? = null
private var isOverlayVideoMode by androidx.compose.runtime.mutableStateOf(false)
private var isClosingOnEnd = false
@Volatile
private var isServiceStopping = false
@Volatile
private var isServiceDestroying = false
private var pauseGraceJob: Job? = null
@Volatile
private var isWithinPauseGraceWindow: Boolean = false

private fun requestStopSelf() {
    isServiceStopping = true
    stopSelf()
}

private fun cancelPauseGraceTimer() {
    pauseGraceJob?.cancel()
    pauseGraceJob = null
    isWithinPauseGraceWindow = false
}

private fun startPauseGraceTimerIfNeeded() {
    if (isServiceDestroying || isServiceStopping || isClosingOnEnd) return
    val player = PlayerManager.exoPlayer ?: return
    if (player.mediaItemCount == 0 ||
        player.playbackState == Player.STATE_ENDED ||
        player.playbackState == Player.STATE_IDLE ||
        player.isPlaying ||
        player.playWhenReady
    ) {
        return
    }
    if (pauseGraceJob?.isActive == true) return
    isWithinPauseGraceWindow = true
    shura.media.LogKeeper.log("PlaybackService: Starting 5-minute pause grace timer", "PlaybackService")
    pauseGraceJob = serviceScope.launch(Dispatchers.Main) {
        delay(PAUSE_GRACE_DURATION_MS)
        pauseGraceJob = null
        val currentPlayer = PlayerManager.exoPlayer
        if (isServiceDestroying || isServiceStopping || isClosingOnEnd ||
            currentPlayer == null || currentPlayer.mediaItemCount == 0 ||
            currentPlayer.isPlaying || currentPlayer.playWhenReady
        ) {
            isWithinPauseGraceWindow = false
            return@launch
        }
        if (PlayerManager.isPlayerScreenVisible || composeView != null) {
            shura.media.LogKeeper.log("PlaybackService: 5-minute pause grace expired with UI active -> clearing foreground lock only", "PlaybackService")
            isWithinPauseGraceWindow = false
            promoteSessionNotificationNow(forceForeground = false)
        } else {
            shura.media.LogKeeper.log("PlaybackService: 5-minute pause grace expired in background -> releasing playback & notification", "PlaybackService")
            isWithinPauseGraceWindow = false
            performFullPlaybackTeardown(flushProgress = true)
        }
    }
}

private fun performFullPlaybackTeardown(flushProgress: Boolean) {
    cancelPauseGraceTimer()
    codecRecoveryJob?.cancel()
    isRecoveringCodecError = false
    if (flushProgress) {
        PlayerManager.flushProgressToStorage()
    }
    PlayerManager.resetSessionBackgroundPlay()
    hideOverlay()
    resetSessionFloatingState()
    val player = PlayerManager.exoPlayer
    try { player?.playWhenReady = false } catch (_: Exception) {}
    try { player?.stop() } catch (_: Exception) {}
    try { player?.clearVideoSurface() } catch (_: Exception) {}
    try { player?.clearMediaItems() } catch (_: Exception) {}
    try {
        androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
        cancelMediaNotifications()
    } catch (e: Exception) {
        shura.media.LogKeeper.logError("PlaybackService", "Error stopping foreground during teardown", e)
    }
    updateWidgetUI()
    requestStopSelf()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
    val player = PlayerManager.exoPlayer
    val keepPausedForeground = isWithinPauseGraceWindow &&
        !isServiceDestroying &&
        !isServiceStopping &&
        !isClosingOnEnd &&
        player != null &&
        player.mediaItemCount > 0 &&
        player.playbackState != Player.STATE_IDLE &&
        player.playbackState != Player.STATE_ENDED
    super.onUpdateNotification(session, startInForegroundRequired || keepPausedForeground)
}

private fun isSelfController(controller: MediaSession.ControllerInfo): Boolean {
    return controller.packageName == packageName || controller.uid == android.os.Process.myUid()
}

private fun isTrustedController(controller: MediaSession.ControllerInfo): Boolean {
    return isSelfController(controller) || controller.isTrusted || controller.packageName == "com.android.systemui"
}

private fun isAllowedMediaUri(uri: android.net.Uri?): Boolean {
    return shura.media.data.MediaUriResolver.isSafeExternalUri(this@PlaybackService, uri?.toString())
}

private fun isAllowedSubtitleUri(uri: android.net.Uri?): Boolean {
    val scheme = uri?.scheme?.lowercase() ?: return false
    return scheme == "content" || scheme == "file"
}

private fun safeUpdateViewLayout(cv: ComposeView, lp: WindowManager.LayoutParams) {
    if (!cv.isAttachedToWindow) return
    try {
        windowManager.updateViewLayout(cv, lp)
    } catch (e: Exception) {
        shura.media.LogKeeper.log("PlaybackService: Ignored WindowManager updateViewLayout error: ${e.message}", "PlaybackService")
    }
}

private fun cancelLegacyFallbackNotification() {
    try {
        val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(1002)
    } catch (_: Exception) {}
}

private fun cancelMediaNotifications() {
    try {
        val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        nm?.cancel(1002)
    } catch (_: Exception) {}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun promoteSessionNotificationNow(forceForeground: Boolean = false) {
    cancelLegacyFallbackNotification()
    val session = mediaSession ?: return
    val player = PlayerManager.exoPlayer
    if (player == null || player.mediaItemCount == 0) return
    try {
        onUpdateNotification(session, forceForeground)
    } catch (e: Exception) {
        shura.media.LogKeeper.log("PlaybackService: Notification update handled (${e.message})", "PlaybackService")
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun ensureForegroundNotificationSlotForOverlayStart(abortAfterSatisfyingContract: Boolean = false) {
    cancelLegacyFallbackNotification()
    val session = mediaSession
    val player = PlayerManager.exoPlayer
    if (!abortAfterSatisfyingContract && session != null && player != null && player.mediaItemCount > 0) {
        promoteSessionNotificationNow(forceForeground = true)
        return
    }
    try {
        val channelId = androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (nm != null && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        channelId,
                        "Media Playback",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val contentText = if (player != null && player.mediaItemCount > 0) {
            PlayerManager.getResolvedTitleForItem(player.currentMediaItem) ?: "Playback ready"
        } else {
            "Player ready"
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Vian Media")
            .setContentText(contentText)
            .setSmallIcon(shura.media.R.drawable.ic_widget_play)
            .setOngoing(true)
            .build()
        val notifId = androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(notifId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notifId, notification)
        }
        if (abortAfterSatisfyingContract) {
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            cancelMediaNotifications()
        }
    } catch (e: Exception) {
        shura.media.LogKeeper.logError("PlaybackService", "Failed ensuring foreground notification slot", e)
    }
}

internal fun applyStagedPlaybackRequest() {
    val req = PlayerManager.consumeStagedPlaybackRequest() ?: return
    val player = PlayerManager.exoPlayer ?: return
    if (req.mediaItems.isEmpty()) return
    cancelPauseGraceTimer()
    isServiceStopping = false
    isClosingOnEnd = false
    decoderServiceRetryCount = 0
    isRecoveringCodecError = false
    codecRecoveryJob?.cancel()
    if (req.appendIfActive && player.mediaItemCount > 0) {
        PlayerManager.appendOrTransitionQueue(applicationContext, req.mediaItems)
    } else {
        player.setMediaItems(req.mediaItems, req.startIndex, req.startPositionMs)
        player.pauseAtEndOfMediaItems = (player.repeatMode == Player.REPEAT_MODE_OFF)
        player.prepare()
        player.play()
        PlayerManager.isImplicitFolderQueue = req.isImplicit
        PlayerManager.updateSnapshot(rebuildPlaylist = true)
    }
    promoteSessionNotificationNow(forceForeground = true)
    updateWidgetUI()
}

// In-session memory for floating window dimensions and positions (remembered while open, reset on close)
private var sessionWindowX: Int?
    get() = PlayerManager.floatingSession.windowX
    set(value) { PlayerManager.floatingSession.windowX = value }

private var sessionWindowY: Int?
    get() = PlayerManager.floatingSession.windowY
    set(value) { PlayerManager.floatingSession.windowY = value }

private var sessionWindowWidth: Int?
    get() = PlayerManager.floatingSession.windowWidth
    set(value) { PlayerManager.floatingSession.windowWidth = value }

private var sessionWindowHeight: Int?
    get() = PlayerManager.floatingSession.windowHeight
    set(value) { PlayerManager.floatingSession.windowHeight = value }

private var sessionAudioWindowWidth: Int?
    get() = PlayerManager.floatingSession.audioWindowWidth
    set(value) { PlayerManager.floatingSession.audioWindowWidth = value }

private var sessionAudioWindowHeight: Int?
    get() = PlayerManager.floatingSession.audioWindowHeight
    set(value) { PlayerManager.floatingSession.audioWindowHeight = value }

private var sessionBubbleX: Int?
    get() = PlayerManager.floatingSession.bubbleX
    set(value) { PlayerManager.floatingSession.bubbleX = value }

private var sessionBubbleY: Int?
    get() = PlayerManager.floatingSession.bubbleY
    set(value) { PlayerManager.floatingSession.bubbleY = value }

private var sessionIsMinimized: Boolean
    get() = PlayerManager.floatingSession.isMinimized
    set(value) { PlayerManager.floatingSession.isMinimized = value }

private var sessionIsAspectRatioBroken: Boolean
    get() = PlayerManager.floatingSession.isAspectRatioBroken
    set(value) { PlayerManager.floatingSession.isAspectRatioBroken = value }

fun resetSessionFloatingState() {
    PlayerManager.floatingSession.reset()
}

// Removed inactivity timeout

override fun onCreate() {
super.onCreate()
isServiceStopping = false
isServiceDestroying = false
instance = this
savedStateRegistryController.performRestore(null)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager

try {
val defaultProvider = object : androidx.media3.session.DefaultMediaNotificationProvider(this) {
    override fun getMediaButtons(
        session: androidx.media3.session.MediaSession,
        playerCommands: androidx.media3.common.Player.Commands,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        showWhenCompact: Boolean
    ): com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton> {
        val defaultButtons = super.getMediaButtons(session, playerCommands, customLayout, showWhenCompact)
        
        val prevButton = defaultButtons.firstOrNull { 
            it.playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS ||
            it.playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM 
        } ?: androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Previous")
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setIconResId(shura.media.R.drawable.ic_widget_prev)
            .build()

        val playPauseButton = defaultButtons.firstOrNull {
            it.playerCommand == androidx.media3.common.Player.COMMAND_PLAY_PAUSE
        } ?: run {
            val isPlaying = session.player.isPlaying
            val icon = if (isPlaying) shura.media.R.drawable.ic_widget_pause else shura.media.R.drawable.ic_widget_play
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (isPlaying) "Pause" else "Play")
                .setPlayerCommand(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                .setIconResId(icon)
                .build()
        }

        val nextButton = defaultButtons.firstOrNull {
            it.playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT ||
            it.playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        } ?: androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Next")
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setIconResId(shura.media.R.drawable.ic_widget_next)
            .build()

        val miniPlayerButton = customLayout.firstOrNull { it.sessionCommand?.customAction == "ACTION_OVERLAY" }
            ?: androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Mini Player")
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
                .setIconResId(shura.media.R.drawable.ic_widget_miniplayer)
                .build()

        val closeButton = customLayout.firstOrNull { it.sessionCommand?.customAction == "ACTION_CLOSE" }
            ?: androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Close")
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
                .setIconResId(shura.media.R.drawable.ic_widget_close)
                .build()

        if (showWhenCompact) {
            return com.google.common.collect.ImmutableList.of(
                prevButton,
                playPauseButton,
                nextButton
            )
        }

        return com.google.common.collect.ImmutableList.of(
            prevButton,
            playPauseButton,
            nextButton,
            miniPlayerButton,
            closeButton
        )
    }
}
setMediaNotificationProvider(defaultProvider)
} catch (e: Exception) {
shura.media.LogKeeper.logError("PlaybackService", "Failed to set up MediaNotificationProvider", e)
}

cancelLegacyFallbackNotification()
val rawPlayer = PlayerManager.initializeOwnedPlayer(this, false)
isClosingOnEnd = false
decoderServiceRetryCount = 0
isRecoveringCodecError = false

serviceScope.launch {
    PlayerManager.playbackEndedCloseEvent.collect {
        handleNaturalPlaybackEndClose()
    }
}

val filter = android.content.IntentFilter("shura.media.ACTION_WIDGET_COMMAND")
filter.addAction("shura.media.ACTION_UPDATE_NOTIFICATION")
androidx.core.content.ContextCompat.registerReceiver(
    this,
    widgetCommandReceiver,
    filter,
    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
)

PlayerManager.exoPlayer?.addListener(object : Player.Listener {
override fun onIsPlayingChanged(isPlaying: Boolean) {
    shura.media.LogKeeper.log("PlaybackService: onIsPlayingChanged = $isPlaying", "PlaybackService")
    if (isPlaying) {
        cancelPauseGraceTimer()
        isClosingOnEnd = false
        if ((PlayerManager.exoPlayer?.mediaItemCount ?: 0) > 0) {
            promoteSessionNotificationNow(forceForeground = false)
        }
    } else if (PlayerManager.exoPlayer?.playWhenReady == false) {
        startPauseGraceTimerIfNeeded()
    }
    updateWidgetUI()
}
override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
    val reasonStr = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        else -> "UNKNOWN($reason)"
    }
    shura.media.LogKeeper.log("PlaybackService: onMediaItemTransition (reason: $reasonStr)", "PlaybackService")
    decoderServiceRetryCount = 0
    isRecoveringCodecError = false
    codecRecoveryJob?.cancel()
    updateWidgetUI()
    if (mediaItem != null && PlayerManager.exoPlayer?.playWhenReady == true) {
        promoteSessionNotificationNow(forceForeground = true)
    }
}
override fun onRepeatModeChanged(repeatMode: Int) {
    shura.media.LogKeeper.log("PlaybackService: onRepeatModeChanged = $repeatMode", "PlaybackService")
    PlayerManager.exoPlayer?.pauseAtEndOfMediaItems = (repeatMode == Player.REPEAT_MODE_OFF)
    updateWidgetUI()
}
override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
    val player = PlayerManager.exoPlayer ?: return
    val hasVideo = PlayerManager.hasSupportedVideoTrack(tracks)
    val shouldDisableVideo = (!tracks.isEmpty && !hasVideo) ||
        (!PlayerManager.isPlayerScreenVisible && !PlayerManager.isFloatingVideoActive)
    if (shouldDisableVideo) {
        if (!hasVideo && !tracks.isEmpty) {
            PlayerManager.isFloatingVideoActive = false
        }
        PlayerManager.detachVideoSurface()
        PlayerManager.setVideoTrackEnabled(false)
    }
}
override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    shura.media.LogKeeper.log("PlaybackService: onShuffleModeEnabledChanged = $shuffleModeEnabled", "PlaybackService")
    updateWidgetUI()
}
override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
    shura.media.LogKeeper.log("PlaybackService: onTimelineChanged (itemCount=${timeline.windowCount})", "PlaybackService")
    updateWidgetUI()
}
override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
    val isTimeout = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
    if (isTimeout) {
        shura.media.LogKeeper.log("PlaybackService: Timeout caught during surface detachment or release - ignored gracefully", "PlaybackService")
        return
    }
    var isPermissionError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
    var isNotFoundError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
    var currentCause: Throwable? = error.cause
    var securityCauseMessage: String? = null
    while (currentCause != null && !isPermissionError && !isNotFoundError) {
        if (currentCause is SecurityException ||
            currentCause.message?.contains("SecurityException", ignoreCase = true) == true ||
            currentCause.message?.contains("Permission Denial", ignoreCase = true) == true) {
            isPermissionError = true
            securityCauseMessage = currentCause.message
            break
        }
        if (currentCause is java.io.FileNotFoundException) {
            isNotFoundError = true
            securityCauseMessage = currentCause.message
            break
        }
        currentCause = currentCause.cause
    }
    if (isPermissionError) {
        val player = PlayerManager.exoPlayer
        val currentItem = player?.currentMediaItem
        val currentUri = currentItem?.localConfiguration?.uri
        if (player != null && currentUri != null) {
            val recoveredUri = shura.media.data.MediaUriResolver.resolveToPlayableUri(this@PlaybackService, currentUri)
            if (recoveredUri != currentUri) {
                shura.media.LogKeeper.log("PlaybackService: Recovered playable URI on SecurityException -> $recoveredUri", "PlaybackService")
                val updatedItem = currentItem.buildUpon().setUri(recoveredUri).build()
                val curPos = player.currentPosition.coerceAtLeast(0L)
                player.setMediaItem(updatedItem, curPos)
                player.prepare()
                player.play()
                return
            }
        }
        shura.media.LogKeeper.log("PlaybackService: Cannot read media URI (${error.errorCodeName}) - skipping item gracefully: ${securityCauseMessage ?: error.message}", "PlaybackService")
        PlayerManager.notifyPlaybackError("Cannot read file: Permission denied")
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player?.pause()
        }
        return
    }
    if (isNotFoundError) {
        val player = PlayerManager.exoPlayer
        shura.media.LogKeeper.log("PlaybackService: File not found (${error.errorCodeName}) - skipping item gracefully: ${securityCauseMessage ?: error.message}", "PlaybackService")
        PlayerManager.notifyPlaybackError("Cannot read file: File not found")
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player?.pause()
        }
        return
    }
    val cause = error.cause?.message ?: "Unknown"
    shura.media.LogKeeper.logError("PlaybackService", "Error: ${error.errorCodeName} - ${error.message} - Cause: $cause", error)
    val player = PlayerManager.exoPlayer
    if (player != null && (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)) {
        
        val decoderEx = error.cause as? androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
        val videoEx = error.cause as? androidx.media3.exoplayer.video.MediaCodecVideoDecoderException
        val failedCodecName = decoderEx?.codecInfo?.name 
            ?: videoEx?.codecInfo?.name 
            ?: if (error.message?.contains("Decoder failed:") == true) {
                error.message?.substringAfter("Decoder failed:")?.trim()?.substringBefore(' ')
            } else null

        if (!failedCodecName.isNullOrEmpty()) {
            PlayerManager.blacklistDecoder(failedCodecName)
        }

        if (!isRecoveringCodecError && decoderServiceRetryCount < 2) {
            isRecoveringCodecError = true
            decoderServiceRetryCount++
            val currentPos = player.currentPosition.coerceAtLeast(0L)
            shura.media.LogKeeper.log("PlaybackService: Debounced codec recovery (attempt $decoderServiceRetryCount)...", "PlaybackService")
            try { player.stop() } catch (_: Exception) {}
            codecRecoveryJob?.cancel()
            codecRecoveryJob = serviceScope.launch(Dispatchers.Main) {
                delay(150L)
                val activePlayer = PlayerManager.exoPlayer
                if (instance == this@PlaybackService && !isServiceDestroying && activePlayer != null && activePlayer.mediaItemCount > 0 && !isClosingOnEnd) {
                    isRecoveringCodecError = false
                    activePlayer.prepare()
                    if (currentPos > 0L) {
                        activePlayer.seekTo(currentPos)
                    }
                    activePlayer.play()
                } else {
                    isRecoveringCodecError = false
                }
            }
        } else if (decoderServiceRetryCount >= 2) {
            isRecoveringCodecError = false
            shura.media.LogKeeper.log("PlaybackService: Decoder error exceeded retry limit, pausing player.", "PlaybackService")
            player.pause()
            PlayerManager.notifyPlaybackError("Playback failed: unable to decode video/audio stream")
        }
    } else {
        PlayerManager.notifyPlaybackError(error.message ?: "Playback error occurred")
    }
}
override fun onPlaybackStateChanged(playbackState: Int) {
    val stateName = when (playbackState) {
        Player.STATE_IDLE -> "STATE_IDLE"
        Player.STATE_BUFFERING -> "STATE_BUFFERING"
        Player.STATE_READY -> "STATE_READY"
        Player.STATE_ENDED -> "STATE_ENDED"
        else -> "UNKNOWN"
    }
    shura.media.LogKeeper.log("Playback state changed to: $stateName", "PlaybackService")
    if (playbackState == Player.STATE_READY) {
        isRecoveringCodecError = false
        isClosingOnEnd = false
    } else if (playbackState == Player.STATE_BUFFERING) {
        isClosingOnEnd = false
    } else if (playbackState == Player.STATE_ENDED) {
        isRecoveringCodecError = false
        val player = PlayerManager.exoPlayer
        if (player?.repeatMode == Player.REPEAT_MODE_ALL) {
            shura.media.LogKeeper.log("Playback ended with repeatMode ALL -> restarting from first item", "PlaybackService")
            player.seekTo(0, 0L)
            player.play()
        } else if (player?.repeatMode == Player.REPEAT_MODE_OFF) {
            shura.media.LogKeeper.log("Playback ended with repeatMode OFF -> closing player/overlay/service", "PlaybackService")
            handleNaturalPlaybackEndClose()
        }
    } else if (playbackState == Player.STATE_IDLE && !isRecoveringCodecError) {
        val player = PlayerManager.exoPlayer
        if (player != null && player.mediaItemCount == 0 && !PlayerManager.isFloatingVideoActive && composeView == null && !PlayerManager.isPlayerScreenVisible) {
            shura.media.LogKeeper.log("Playback IDLE with 0 items and no UI -> stopping service & removing notification", "PlaybackService")
            try {
                androidx.core.app.ServiceCompat.stopForeground(this@PlaybackService, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
                cancelMediaNotifications()
            } catch (e: Exception) {}
            requestStopSelf()
        }
    }
}
override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
    shura.media.LogKeeper.log("PlaybackService: onPlayWhenReadyChanged = $playWhenReady, reason = $reason", "PlaybackService")
    if (playWhenReady && (PlayerManager.exoPlayer?.mediaItemCount ?: 0) > 0) {
        cancelPauseGraceTimer()
        promoteSessionNotificationNow(forceForeground = true)
    } else if (!playWhenReady) {
        startPauseGraceTimerIfNeeded()
    }
}
})


val intent = android.content.Intent(this, shura.media.ui.PlayerActivity::class.java).apply {
action = "shura.media.ACTION_OPEN_PLAYER"
}
val pendingIntent = android.app.PendingIntent.getActivity(
this, 0, intent,
android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
)
val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(rawPlayer) {
    override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
            .add(androidx.media3.common.Player.COMMAND_PREPARE)
            .add(androidx.media3.common.Player.COMMAND_STOP)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (command == androidx.media3.common.Player.COMMAND_PLAY_PAUSE ||
            command == androidx.media3.common.Player.COMMAND_PREPARE ||
            command == androidx.media3.common.Player.COMMAND_STOP ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ) {
            return true
        }
        return super.isCommandAvailable(command)
    }

    override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
        val base = super.getMediaMetadata()
        val currentItem = currentMediaItem
        val resolvedTitle = PlayerManager.getResolvedTitleForItem(currentItem)
        val needsTitle = base.title.isNullOrBlank() || (resolvedTitle != null && base.title.toString() != resolvedTitle)
        val needsArtwork = base.artworkUri == null && base.artworkData == null
        if (!needsTitle && !needsArtwork) {
            return base
        }
        val fallbackUri = currentItem?.localConfiguration?.uri
            ?: currentItem?.mediaId?.let { id -> try { android.net.Uri.parse(id) } catch (_: Exception) { null } }
        val builder = base.buildUpon()
        if (needsTitle) {
            val itemTitle = resolvedTitle ?: "Vian Media"
            builder.setTitle(itemTitle)
                .setDisplayTitle(itemTitle)
                .setArtist("Vian Media")
        }
        if (needsArtwork && fallbackUri != null) {
            builder.setArtworkUri(fallbackUri)
        }
        return builder.build()
    }
}

mediaSession = MediaSession.Builder(this, forwardingPlayer)
.setSessionActivity(pendingIntent)
.setBitmapLoader(shura.media.MyBitmapLoader(this))
.setCallback(object : MediaSession.Callback {
override fun onConnect(
session: MediaSession,
controller: MediaSession.ControllerInfo
): MediaSession.ConnectionResult {
if (!isTrustedController(controller)) {
    shura.media.LogKeeper.logWarn("PlaybackService", "Rejected untrusted MediaSession controller: ${controller.packageName}")
    return MediaSession.ConnectionResult.reject()
}
val defaultResult = super.onConnect(session, controller)
val customCommandsBuilder = defaultResult.availableSessionCommands.buildUpon()
    .add(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
    .add(androidx.media3.session.SessionCommand("ACTION_LOOP", android.os.Bundle.EMPTY))
    .add(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
if (isSelfController(controller)) {
    customCommandsBuilder
        .add(androidx.media3.session.SessionCommand("ADD_SUBTITLE", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("SET_BOOST_GAIN", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_MORE", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_LESS", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_PIP", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_VIDEO_OVERLAY", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_MINIPLAYER", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_HIDE_OVERLAY", android.os.Bundle.EMPTY))
        .add(androidx.media3.session.SessionCommand("ACTION_APPLY_STAGED_PLAYBACK", android.os.Bundle.EMPTY))
}
val playerCommands = defaultResult.availablePlayerCommands.buildUpon()
    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
    .build()
return MediaSession.ConnectionResult.accept(customCommandsBuilder.build(), playerCommands)
}

override fun onPlayerCommandRequest(
session: MediaSession,
controller: MediaSession.ControllerInfo,
playerCommand: Int
): Int {
if (!isTrustedController(controller)) {
    return androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED
}
if (playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT ||
    playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
    val player = session.player
    val wasEndedOrIdle = player.playbackState == androidx.media3.common.Player.STATE_ENDED ||
        player.playbackState == androidx.media3.common.Player.STATE_IDLE
    if (player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
    } else if (player.mediaItemCount > 1) {
        player.seekTo(0, 0)
    } else {
        player.seekTo(0)
    }
    if (wasEndedOrIdle || player.playWhenReady) {
        player.prepare()
        player.play()
    }
    return androidx.media3.session.SessionResult.RESULT_SUCCESS
}
if (playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS ||
    playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
    val player = session.player
    val wasEndedOrIdle = player.playbackState == androidx.media3.common.Player.STATE_ENDED ||
        player.playbackState == androidx.media3.common.Player.STATE_IDLE
    if (player.currentPosition > 3000L && !wasEndedOrIdle) {
        player.seekTo(0)
    } else if (player.hasPreviousMediaItem()) {
        player.seekToPreviousMediaItem()
    } else if (player.mediaItemCount > 1) {
        player.seekTo(player.mediaItemCount - 1, 0)
    } else {
        player.seekTo(0)
    }
    if (wasEndedOrIdle || player.playWhenReady) {
        player.prepare()
        player.play()
    }
    return androidx.media3.session.SessionResult.RESULT_SUCCESS
}
return super.onPlayerCommandRequest(session, controller, playerCommand)
}

override fun onCustomCommand(
session: MediaSession,
controller: MediaSession.ControllerInfo,
customCommand: androidx.media3.session.SessionCommand,
args: android.os.Bundle
): ListenableFuture<androidx.media3.session.SessionResult> {
if (!isTrustedController(controller)) {
    shura.media.LogKeeper.logWarn("PlaybackService", "Rejected custom command ${customCommand.customAction} from ${controller.packageName}")
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
}

if (customCommand.customAction == "SET_BOOST_GAIN") {
    if (!isSelfController(controller)) {
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    val gainMb = args.getInt("gainMb", 0).coerceIn(0, 3000)
    PlayerManager.setBoostGain(gainMb)
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_CLOSE") {
    performFullPlaybackTeardown(flushProgress = true)
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_APPLY_STAGED_PLAYBACK") {
    if (!isSelfController(controller)) {
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    applyStagedPlaybackRequest()
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_HIDE_OVERLAY") {
    if (!isSelfController(controller)) {
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    hideOverlay()
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_LOOP") {
    val player = session.player
    player.repeatMode = when (player.repeatMode) {
        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
    }
    ((player as? androidx.media3.exoplayer.ExoPlayer) ?: PlayerManager.exoPlayer)?.pauseAtEndOfMediaItems =
        (player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)
    updateCustomLayout()
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}
if (customCommand.customAction == "ACTION_OVERLAY" || customCommand.customAction == "ACTION_MINIPLAYER") {
    if (!android.provider.Settings.canDrawOverlays(this@PlaybackService)) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        shura.media.data.MediaUriResolver.safeStartActivity(this@PlaybackService, intent)
    } else {
        if (customCommand.customAction == "ACTION_MINIPLAYER") {
            showOverlay(false)
        } else if (composeView == null) {
            showOverlay(false)
        } else {
            hideOverlay()
        }
    }
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}
if (customCommand.customAction == "ACTION_PIP" || customCommand.customAction == "ACTION_VIDEO_OVERLAY") {
    if (!isSelfController(controller)) {
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    if (!android.provider.Settings.canDrawOverlays(this@PlaybackService)) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        shura.media.data.MediaUriResolver.safeStartActivity(this@PlaybackService, intent)
    } else {
        showOverlay(true)
    }
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ADD_SUBTITLE") {
    if (!isSelfController(controller)) {
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    val uriStr = args.getString("subtitle_uri")
    val parsedSubUri = uriStr?.let { try { android.net.Uri.parse(it) } catch (_: Exception) { null } }
    if (uriStr != null && isAllowedSubtitleUri(parsedSubUri)) {
        val player = session.player
        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val mimeType = if (uriStr.endsWith(".vtt", true)) androidx.media3.common.MimeTypes.TEXT_VTT
            else if (uriStr.endsWith(".ssa", true) || uriStr.endsWith(".ass", true)) androidx.media3.common.MimeTypes.TEXT_SSA
            else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(parsedSubUri!!)
                .setMimeType(mimeType)
                .setLanguage(null)
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .build()

            val newItemBuilder = currentItem.buildUpon()
            val oldConfigs = currentItem.localConfiguration?.subtitleConfigurations
            if (oldConfigs != null) {
                newItemBuilder.setSubtitleConfigurations(oldConfigs + subtitleConfig)
            } else {
                newItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }

            val newItem = newItemBuilder.build()
            val currentItemIndex = player.currentMediaItemIndex
            player.replaceMediaItem(currentItemIndex, newItem)

            val builder = player.trackSelectionParameters.buildUpon()
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            player.trackSelectionParameters = builder.build()
        }
    } else if (uriStr != null) {
        shura.media.LogKeeper.logWarn("PlaybackService", "Rejected disallowed subtitle URI scheme")
        return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED))
    }
    return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}
return super.onCustomCommand(session, controller, customCommand, args)
}

override fun onAddMediaItems(
mediaSession: MediaSession,
controller: MediaSession.ControllerInfo,
mediaItems: List<MediaItem>
): ListenableFuture<List<MediaItem>> {
if (!isSelfController(controller)) {
    shura.media.LogKeeper.logWarn("PlaybackService", "Rejected onAddMediaItems from external controller: ${controller.packageName}")
    return Futures.immediateFailedFuture(SecurityException("Untrusted controller cannot add media items"))
}
shura.media.LogKeeper.log("onAddMediaItems called with ${mediaItems.size} items", "PlaybackService")
val updatedMediaItems = mediaItems.mapNotNull { mediaItem ->
    val uriToUse = mediaItem.localConfiguration?.uri?.toString() ?: mediaItem.mediaId
    val parsed = try { android.net.Uri.parse(uriToUse) } catch (_: Exception) { null }
    if (!isAllowedMediaUri(parsed)) {
        shura.media.LogKeeper.logWarn("PlaybackService", "Skipped disallowed media URI scheme in onAddMediaItems")
        null
    } else {
        mediaItem.buildUpon()
            .setUri(uriToUse)
            .build()
    }
}
return Futures.immediateFuture(updatedMediaItems)
}
}).build()

updateCustomLayout()

addSession(mediaSession!!)
applyStagedPlaybackRequest()
}

override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
return mediaSession
}

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        shura.media.LogKeeper.log("onTaskRemoved called", "PlaybackService")
        val player = mediaSession?.player
        // Only stop if the queue is empty or playback has naturally ended with repeat OFF.
        // Paused background playback must remain alive in the notification shade so the user can unpause.
        if (player != null && (player.mediaItemCount == 0 || (player.playbackState == androidx.media3.common.Player.STATE_ENDED && player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF))) {
            try {
                androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
                cancelMediaNotifications()
            } catch (e: Exception) {}
            player.stop()
            resetSessionFloatingState()
            requestStopSelf()
        }
    }


@SuppressLint("ClickableViewAccessibility")
private fun showOverlay(startInVideoMode: Boolean = false) {
if (isServiceDestroying) return
isServiceStopping = false
shura.media.service.PlayerManager.flushProgressToStorage()
isOverlayVideoMode = startInVideoMode
val activePlayer = PlayerManager.exoPlayer
val activeUri = activePlayer?.currentMediaItem?.localConfiguration?.uri
val activeTracks = activePlayer?.currentTracks
val isAudioOnly = if (activeTracks != null && !activeTracks.isEmpty) {
    !PlayerManager.hasSupportedVideoTrack(activeTracks)
} else {
    PlayerManager.isAudioOnlyHint(this, activeUri)
}
if (startInVideoMode && !isAudioOnly) {
    shura.media.service.PlayerManager.isFloatingVideoActive = true
    shura.media.service.PlayerManager.setVideoTrackEnabled(true)
} else {
    shura.media.service.PlayerManager.isFloatingVideoActive = false
    shura.media.service.PlayerManager.detachVideoSurface()
    shura.media.service.PlayerManager.setVideoTrackEnabled(false)
}
val existingCv = composeView
val existingLp = layoutParams
if (existingCv != null) {
    if (existingLp != null && !sessionIsMinimized) {
        val metrics = resources.displayMetrics
        val minWidth = (200 * metrics.density).toInt()
        val maxWidth = (metrics.widthPixels * 0.95f).toInt()
        val maxHeight = (metrics.heightPixels * 0.7f).toInt()
        val topBarHeightPx = (32 * metrics.density).toInt()
        val vs = PlayerManager.exoPlayer?.videoSize
        val aspect = if (vs != null && vs.width > 0 && vs.height > 0) (vs.width.toFloat() / vs.height.toFloat()).coerceIn(0.4f, 2.5f) else 16f / 9f
        val targetW = (if (startInVideoMode) sessionWindowWidth else (sessionAudioWindowWidth ?: sessionWindowWidth))
            ?: (300 * metrics.density).toInt()
        existingLp.width = targetW.coerceIn(minWidth, maxWidth)
        existingLp.height = if (startInVideoMode) {
            sessionWindowHeight ?: (((existingLp.width / aspect).toInt() + topBarHeightPx).coerceAtMost(maxHeight))
        } else {
            sessionAudioWindowHeight ?: sessionWindowHeight ?: (200 * metrics.density).toInt()
        }
        existingLp.x = existingLp.x.coerceIn(0, (metrics.widthPixels - existingLp.width).coerceAtLeast(0))
        existingLp.y = existingLp.y.coerceIn(0, (metrics.heightPixels - existingLp.height).coerceAtLeast(0))
        safeUpdateViewLayout(existingCv, existingLp)
    }
    return
}
val cv = ComposeView(this)
composeView = cv
cv.setViewTreeLifecycleOwner(this@PlaybackService)
cv.setViewTreeViewModelStoreOwner(this@PlaybackService)
cv.setViewTreeSavedStateRegistryOwner(this@PlaybackService)

val prefs = getSharedPreferences("MiniPlayerPrefs", android.content.Context.MODE_PRIVATE)

cv.setContent {
var isMinimized by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sessionIsMinimized) }
val isVideoMode = isOverlayVideoMode
var videoAspectRatio by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(16f / 9f) }
var isAspectRatioBroken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sessionIsAspectRatioBroken) }

val handleMinimizeToggle: (Boolean) -> Unit = { minimized ->
    if (isMinimized != minimized) {
        shura.media.service.PlayerManager.flushProgressToStorage()
        isMinimized = minimized
        sessionIsMinimized = minimized
        if (minimized) {
            shura.media.service.PlayerManager.isFloatingVideoActive = false
            shura.media.service.PlayerManager.detachVideoSurface()
            shura.media.service.PlayerManager.setVideoTrackEnabled(false)
        } else {
            val activePlayer = shura.media.service.PlayerManager.exoPlayer
            val activeUri = activePlayer?.currentMediaItem?.localConfiguration?.uri
            val activeTracks = activePlayer?.currentTracks
            val isAudioOnly = if (activeTracks != null && !activeTracks.isEmpty) {
                !shura.media.service.PlayerManager.hasSupportedVideoTrack(activeTracks)
            } else {
                shura.media.service.PlayerManager.isAudioOnlyHint(this@PlaybackService, activeUri)
            }
            if (isVideoMode && !isAudioOnly) {
                shura.media.service.PlayerManager.isFloatingVideoActive = true
                shura.media.service.PlayerManager.setVideoTrackEnabled(true)
            } else {
                shura.media.service.PlayerManager.isFloatingVideoActive = false
                shura.media.service.PlayerManager.detachVideoSurface()
                shura.media.service.PlayerManager.setVideoTrackEnabled(false)
            }
        }
        val lp = layoutParams
        if (lp != null) {
            val metrics = resources.displayMetrics
            if (minimized) {
                // 1. Remember windowed position & size before collapsing (guarded against WRAP_CONTENT)
                if (lp.width > 0) {
                    if (isVideoMode) {
                        sessionWindowWidth = lp.width
                    } else {
                        sessionAudioWindowWidth = lp.width
                    }
                }
                if (lp.height > 0) {
                    if (isVideoMode) {
                        sessionWindowHeight = lp.height
                    } else {
                        sessionAudioWindowHeight = lp.height
                    }
                }
                sessionWindowX = lp.x
                sessionWindowY = lp.y

                // 2. Set WRAP_CONTENT for compact folded bubble
                lp.width = WindowManager.LayoutParams.WRAP_CONTENT
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT

                // 3. Restore remembered folded button position or default to docked right edge
                val bubbleSizePx = (40 * metrics.density).toInt()
                val targetBubbleX = sessionBubbleX ?: (metrics.widthPixels - bubbleSizePx - (16 * metrics.density).toInt()).coerceAtLeast(0)
                val targetBubbleY = sessionBubbleY ?: lp.y.coerceIn(0, (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0))

                lp.x = targetBubbleX.coerceIn(0, (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0))
                lp.y = targetBubbleY.coerceIn(0, (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0))
                sessionBubbleX = lp.x
                sessionBubbleY = lp.y
            } else {
                // 1. Remember folded bubble position before expanding
                sessionBubbleX = lp.x
                sessionBubbleY = lp.y

                // 2. Restore window dimensions respecting aspect ratio
                val topBarHeightPx = (32 * metrics.density).toInt()
                val minWidth = (200 * metrics.density).toInt()
                val maxWidth = (metrics.widthPixels * 0.95f).toInt()
                val maxHeight = (metrics.heightPixels * 0.7f).toInt()
                val aspect = videoAspectRatio.coerceIn(0.4f, 2.5f)

                val targetWidth: Int
                var targetHeight: Int

                if (isVideoMode) {
                    targetWidth = (sessionWindowWidth ?: (300 * metrics.density).toInt()).coerceIn(minWidth, maxWidth)
                    targetHeight = if (sessionIsAspectRatioBroken) {
                        sessionWindowHeight ?: (((targetWidth) / aspect).toInt() + topBarHeightPx)
                    } else {
                        (((targetWidth) / aspect).toInt() + topBarHeightPx)
                    }
                } else {
                    targetWidth = (sessionAudioWindowWidth ?: sessionWindowWidth ?: (300 * metrics.density).toInt()).coerceIn(minWidth, maxWidth)
                    targetHeight = (sessionAudioWindowHeight ?: sessionWindowHeight ?: (200 * metrics.density).toInt()).coerceIn((120 * metrics.density).toInt(), maxHeight)
                }

                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight
                }

                lp.width = targetWidth
                lp.height = targetHeight

                // 3. Restore remembered window position with screen bounds safety
                val defaultWinX = (24 * metrics.density).toInt()
                val defaultWinY = (100 * metrics.density).toInt()
                val targetWinX = sessionWindowX ?: defaultWinX
                val targetWinY = sessionWindowY ?: defaultWinY

                lp.x = targetWinX.coerceIn(0, (metrics.widthPixels - targetWidth).coerceAtLeast(0))
                lp.y = targetWinY.coerceIn(0, (metrics.heightPixels - targetHeight).coerceAtLeast(0))
                sessionWindowX = lp.x
                sessionWindowY = lp.y
            }
            safeUpdateViewLayout(cv, lp)
        }
    }
}

val updateWindowForAspectRatio: (Float) -> Unit = { aspect ->
    if (aspect > 0.2f && aspect < 5.0f) {
        videoAspectRatio = aspect
        val lp = layoutParams
        val currentCv = composeView
        if (lp != null && currentCv != null && isOverlayVideoMode && !sessionIsMinimized) {
            if (!isAspectRatioBroken || sessionWindowWidth == null || sessionWindowHeight == null) {
                val metrics = resources.displayMetrics
                val topBarHeightPx = (32 * metrics.density).toInt()
                val minWidth = (200 * metrics.density).toInt()
                val maxWidth = (metrics.widthPixels * 0.95f).toInt()
                val maxHeight = (metrics.heightPixels * 0.7f).toInt()

                var targetWidth = (sessionWindowWidth ?: lp.width).coerceIn(minWidth, maxWidth)
                var targetHeight = ((targetWidth) / aspect).toInt() + topBarHeightPx

                if (targetHeight > maxHeight) {
                    targetHeight = maxHeight
                    targetWidth = (((targetHeight - topBarHeightPx) * aspect).toInt()).coerceIn(minWidth, maxWidth)
                }

                lp.width = targetWidth
                lp.height = targetHeight

                lp.x = lp.x.coerceIn(0, (metrics.widthPixels - targetWidth).coerceAtLeast(0))
                lp.y = lp.y.coerceIn(0, (metrics.heightPixels - targetHeight).coerceAtLeast(0))

                sessionWindowWidth = targetWidth
                sessionWindowHeight = targetHeight
                sessionWindowX = lp.x
                sessionWindowY = lp.y

                safeUpdateViewLayout(currentCv, lp)
            }
        }
    }
}

shura.media.ui.theme.MyApplicationTheme {
if (isVideoMode) {
shura.media.ui.components.FloatingVideoPlayerOverlay(
player = shura.media.service.PlayerManager.exoPlayer,
onClose = {
stopPlaybackAndRelease(this@PlaybackService)
},
onMinimize = {
    handleMinimizeToggle(true)
},
isMinimizedExternal = isMinimized,
onMinimizeChange = { minimized ->
    handleMinimizeToggle(minimized)
},
isAspectRatioBroken = isAspectRatioBroken,
onToggleBreakAspectRatio = { broken ->
    isAspectRatioBroken = broken
    sessionIsAspectRatioBroken = broken
    if (!broken) {
        updateWindowForAspectRatio(videoAspectRatio)
    }
},
onDrag = { dx, dy ->
val lp = layoutParams
if (lp != null) {
    val metrics = resources.displayMetrics
    lp.x += dx.toInt()
    lp.y += dy.toInt()
    if (isMinimized) {
        val bubbleSizePx = (40 * metrics.density).toInt()
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0))
        sessionBubbleX = lp.x
        sessionBubbleY = lp.y
    } else {
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))
        sessionWindowX = lp.x
        sessionWindowY = lp.y
    }
    safeUpdateViewLayout(cv, lp)
}
},
onResize = { dw, dh ->
val lp = layoutParams
if (lp != null && !isMinimized) {
    val metrics = resources.displayMetrics
    val topBarHeightPx = (32 * metrics.density).toInt()
    val minWidth = (200 * metrics.density).toInt()
    val minHeight = (120 * metrics.density).toInt()
    val maxWidth = (metrics.widthPixels * 0.95f).toInt()
    val maxHeight = (metrics.heightPixels * 0.7f).toInt()

    if (isAspectRatioBroken) {
        val newWidth = (lp.width + dw.toInt()).coerceIn(minWidth, maxWidth)
        val newHeight = (lp.height + dh.toInt()).coerceIn(minHeight, maxHeight)
        lp.width = newWidth
        lp.height = newHeight
    } else {
        val aspect = videoAspectRatio.coerceIn(0.4f, 2.5f)
        val newWidth = (lp.width + dw.toInt()).coerceIn(minWidth, maxWidth)
        var newHeight = ((newWidth) / aspect).toInt() + topBarHeightPx
        if (newHeight > maxHeight) {
            newHeight = maxHeight
        }
        lp.width = newWidth
        lp.height = newHeight
    }

    lp.x = lp.x.coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
    lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))

    sessionWindowWidth = lp.width
    sessionWindowHeight = lp.height
    sessionWindowX = lp.x
    sessionWindowY = lp.y

    safeUpdateViewLayout(cv, lp)
}
},
onOpenMainPlayer = {
    val currentMedia = shura.media.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
        ?: shura.media.service.PlayerManager.playbackState.value.playlist.getOrNull(
            shura.media.service.PlayerManager.playbackState.value.currentIndex
        )?.mediaId
    val intent = android.content.Intent(this@PlaybackService, shura.media.ui.PlayerActivity::class.java).apply {
        action = "shura.media.ACTION_OPEN_PLAYER"
        if (!currentMedia.isNullOrEmpty()) {
            putExtra("uri", currentMedia)
        }
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    shura.media.data.MediaUriResolver.safeStartActivity(this@PlaybackService, intent)
    hideOverlay()
},
onSwitchToMiniPlayer = {
shura.media.service.PlayerManager.flushProgressToStorage()
isOverlayVideoMode = false
shura.media.service.PlayerManager.isFloatingVideoActive = false
shura.media.service.PlayerManager.detachVideoSurface()
shura.media.service.PlayerManager.setVideoTrackEnabled(false)
val lp = layoutParams
if (lp != null) {
    val metrics = resources.displayMetrics
    val targetAudioW = (sessionAudioWindowWidth ?: sessionWindowWidth ?: (300 * metrics.density).toInt()).coerceIn((200 * metrics.density).toInt(), (metrics.widthPixels * 0.95f).toInt())
    val targetAudioH = (sessionAudioWindowHeight ?: (200 * metrics.density).toInt()).coerceIn((120 * metrics.density).toInt(), (metrics.heightPixels * 0.7f).toInt())
    lp.width = targetAudioW
    lp.height = targetAudioH
    lp.x = lp.x.coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
    lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))
    sessionWindowX = lp.x
    sessionWindowY = lp.y
    safeUpdateViewLayout(cv, lp)
}
},
onAspectRatioChanged = updateWindowForAspectRatio
)
} else {
shura.media.ui.components.MiniPlayerOverlay(
player = shura.media.service.PlayerManager.exoPlayer,
onClose = {
stopPlaybackAndRelease(this@PlaybackService)
},
onMinimize = {
    handleMinimizeToggle(true)
},
onDrag = { dx, dy ->
val lp = layoutParams
if (lp != null) {
    val metrics = resources.displayMetrics
    lp.x += dx.toInt()
    lp.y += dy.toInt()
    if (isMinimized) {
        val bubbleSizePx = (40 * metrics.density).toInt()
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0))
        sessionBubbleX = lp.x
        sessionBubbleY = lp.y
    } else {
        lp.x = lp.x.coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))
        sessionWindowX = lp.x
        sessionWindowY = lp.y
    }
    safeUpdateViewLayout(cv, lp)
}
},
onResize = { dw, dh ->
val lp = layoutParams
if (lp != null && !isMinimized) {
    val metrics = resources.displayMetrics
    lp.width = (lp.width + dw.toInt()).coerceIn((200 * metrics.density).toInt(), (metrics.widthPixels * 0.95f).toInt())
    lp.height = (lp.height + dh.toInt()).coerceIn((120 * metrics.density).toInt(), (metrics.heightPixels * 0.7f).toInt())
    lp.x = lp.x.coerceIn(0, (metrics.widthPixels - lp.width).coerceAtLeast(0))
    lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))
    sessionAudioWindowWidth = lp.width
    sessionAudioWindowHeight = lp.height
    sessionWindowWidth = lp.width
    sessionWindowHeight = lp.height
    sessionWindowX = lp.x
    sessionWindowY = lp.y
    safeUpdateViewLayout(cv, lp)
}
},
isMinimizedExternal = isMinimized,
onMinimizeChange = { minimized ->
    handleMinimizeToggle(minimized)
},
onSwitchToVideo = {
    shura.media.service.PlayerManager.flushProgressToStorage()
    isOverlayVideoMode = true
    val player = shura.media.service.PlayerManager.exoPlayer
    val activeTracks = player?.currentTracks
    val activeUri = player?.currentMediaItem?.localConfiguration?.uri
    val isAudioOnly = if (activeTracks != null && !activeTracks.isEmpty) {
        !shura.media.service.PlayerManager.hasSupportedVideoTrack(activeTracks)
    } else {
        shura.media.service.PlayerManager.isAudioOnlyHint(this@PlaybackService, activeUri)
    }
    if (!isAudioOnly) {
        shura.media.service.PlayerManager.isFloatingVideoActive = true
        shura.media.service.PlayerManager.setVideoTrackEnabled(true)
    } else {
        shura.media.service.PlayerManager.isFloatingVideoActive = false
        shura.media.service.PlayerManager.detachVideoSurface()
        shura.media.service.PlayerManager.setVideoTrackEnabled(false)
    }
    val vs = player?.videoSize
    val aspect = if (vs != null && vs.width > 0 && vs.height > 0) {
        vs.width.toFloat() / vs.height.toFloat()
    } else {
        16f / 9f
    }
    updateWindowForAspectRatio(aspect)
}
)
}
}
}
val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
} else {
@Suppress("DEPRECATION")
WindowManager.LayoutParams.TYPE_PHONE
}
val metrics = resources.displayMetrics
val player = PlayerManager.exoPlayer
val vs = player?.videoSize
val initialAspect = if (vs != null && vs.width > 0 && vs.height > 0) {
    vs.width.toFloat() / vs.height.toFloat()
} else {
    16f / 9f
}
val topBarHeightPx = (32 * metrics.density).toInt()
val minWidth = (200 * metrics.density).toInt()
val maxWidth = (metrics.widthPixels * 0.95f).toInt()
val maxHeight = (metrics.heightPixels * 0.7f).toInt()
val defaultWidth = (300 * metrics.density).toInt().coerceIn(minWidth, maxWidth)
val widthPx = (if (startInVideoMode) sessionWindowWidth else (sessionAudioWindowWidth ?: sessionWindowWidth)) ?: defaultWidth
val widthPxCoerced = widthPx.coerceIn(minWidth, maxWidth)
var initialHeight = ((widthPxCoerced) / initialAspect).toInt() + topBarHeightPx
if (initialHeight > maxHeight) {
    initialHeight = maxHeight
}
val heightPx = if (startInVideoMode) {
    sessionWindowHeight ?: initialHeight
} else {
    sessionAudioWindowHeight ?: sessionWindowHeight ?: (200 * metrics.density).toInt()
}

val defaultX = (24 * metrics.density).toInt()
val defaultY = (100 * metrics.density).toInt()
val initialX = (sessionWindowX ?: defaultX).coerceIn(0, (metrics.widthPixels - widthPxCoerced).coerceAtLeast(0))
val initialY = (sessionWindowY ?: defaultY).coerceIn(0, (metrics.heightPixels - heightPx).coerceAtLeast(0))

sessionWindowWidth = widthPxCoerced
sessionWindowHeight = heightPx
sessionWindowX = initialX
sessionWindowY = initialY

val bubbleSizePx = (40 * metrics.density).toInt()
val isCurrentlyMinimized = sessionIsMinimized
val targetBubbleX = (sessionBubbleX ?: (metrics.widthPixels - bubbleSizePx - (16 * metrics.density).toInt())).coerceIn(0, (metrics.widthPixels - bubbleSizePx).coerceAtLeast(0))
val targetBubbleY = (sessionBubbleY ?: initialY).coerceIn(0, (metrics.heightPixels - bubbleSizePx).coerceAtLeast(0))

layoutParams = WindowManager.LayoutParams(
if (isCurrentlyMinimized) WindowManager.LayoutParams.WRAP_CONTENT else widthPxCoerced,
if (isCurrentlyMinimized) WindowManager.LayoutParams.WRAP_CONTENT else heightPx,
type,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
PixelFormat.TRANSLUCENT
).apply {
gravity = Gravity.TOP or Gravity.START
x = if (isCurrentlyMinimized) targetBubbleX else initialX
y = if (isCurrentlyMinimized) targetBubbleY else initialY
}
windowManager.addView(composeView, layoutParams)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
}

fun hideOverlay() {
isOverlayVideoMode = false
shura.media.service.PlayerManager.isFloatingVideoActive = false
shura.media.service.PlayerManager.setOverlayProgressTickerActive(false)
composeView?.let {
try {
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
windowManager.removeView(it)
} catch (e: Exception) {
shura.media.LogKeeper.log("PlaybackService: WindowManager removeView handled: ${e.message}", "PlaybackService")
}
}
composeView = null
}

    private fun updateCustomLayout() {
        val miniPlayerAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Mini Player")
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
            .setIconResId(shura.media.R.drawable.ic_widget_miniplayer)
            .build()

        val closeAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Close")
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
            .setIconResId(shura.media.R.drawable.ic_widget_close)
            .build()

        mediaSession?.setCustomLayout(listOf(miniPlayerAction, closeAction))
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        isServiceStopping = false
        applyStagedPlaybackRequest()
        val cmd = intent?.getStringExtra("command")
        if (cmd != null) {
            shura.media.LogKeeper.log("PlaybackService onStartCommand with command: $cmd", "PlaybackService")
        }
        if (cmd == "ACTION_VIDEO_OVERLAY") {
            if (android.provider.Settings.canDrawOverlays(this)) {
                ensureForegroundNotificationSlotForOverlayStart(abortAfterSatisfyingContract = false)
                showOverlay(true)
            } else {
                ensureForegroundNotificationSlotForOverlayStart(abortAfterSatisfyingContract = true)
                isServiceStopping = true
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        } else if (cmd == "ACTION_MINIPLAYER" || cmd == "ACTION_OVERLAY") {
            if (android.provider.Settings.canDrawOverlays(this)) {
                ensureForegroundNotificationSlotForOverlayStart(abortAfterSatisfyingContract = false)
                showOverlay(false)
            } else {
                ensureForegroundNotificationSlotForOverlayStart(abortAfterSatisfyingContract = true)
                isServiceStopping = true
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        } else if (cmd == "ACTION_HIDE_OVERLAY") {
            hideOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleNaturalPlaybackEndClose() {
        if (isClosingOnEnd) return
        isClosingOnEnd = true
        shura.media.LogKeeper.log("Playback ended with repeatMode OFF -> closing overlay/service & resetting widget", "PlaybackService")
        performFullPlaybackTeardown(flushProgress = false)
    }

    override fun onDestroy() {
        shura.media.LogKeeper.log("PlaybackService onDestroy: cleaning up notification, session, and owned player", "PlaybackService")
        isServiceDestroying = true
        isServiceStopping = true
        cancelPauseGraceTimer()
        codecRecoveryJob?.cancel()
        codecRecoveryJob = null
        isRecoveringCodecError = false
        try { unregisterReceiver(widgetCommandReceiver) } catch (e: Exception) {}
        hideOverlay()
        resetSessionFloatingState()
        try {
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            cancelMediaNotifications()
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("PlaybackService", "Error stopping foreground on onDestroy", e)
        }
        mediaSession?.run {
            try { release() } catch (_: Exception) {}
            mediaSession = null
        }
        PlayerManager.releaseOwnedPlayer()
        updateWidgetUI()
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        try {
            store.clear()
        } catch (_: Exception) {}
        try {
            serviceScope.cancel()
        } catch (e: Exception) {}
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun updateWidgetUI() {
        if (!shura.media.widget.MediaWidgetProvider.hasInstalledWidgets(this)) return
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val componentName = android.content.ComponentName(this, shura.media.widget.MediaWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            val player = PlayerManager.exoPlayer
            val hasActiveItem = player != null && player.mediaItemCount > 0 && player.currentMediaItem != null
            val defaultColor = android.graphics.Color.parseColor("#19202D")
            for (appWidgetId in appWidgetIds) {
                val views = android.widget.RemoteViews(packageName, shura.media.R.layout.widget_media)
                if (hasActiveItem && player != null) {
                    views.setTextViewText(shura.media.R.id.widget_title, PlayerManager.getResolvedTitleForItem(player.currentMediaItem) ?: "No Media")
                    views.setImageViewResource(shura.media.R.id.widget_btn_play, if (player.isPlaying) shura.media.R.drawable.ic_widget_pause else shura.media.R.drawable.ic_widget_play)
                    val duration = player.duration
                    val position = player.currentPosition.coerceAtLeast(0L)
                    val progress = if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                        ((position.toFloat() / duration.toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else 0
                    views.setProgressBar(shura.media.R.id.widget_progress, 100, progress, false)
                } else {
                    views.setTextViewText(shura.media.R.id.widget_title, "No Media")
                    views.setImageViewResource(shura.media.R.id.widget_btn_play, shura.media.R.drawable.ic_widget_play)
                    views.setProgressBar(shura.media.R.id.widget_progress, 100, 0, false)
                    views.setImageViewResource(shura.media.R.id.widget_btn_loop, shura.media.R.drawable.ic_widget_loop)
                    views.setInt(shura.media.R.id.widget_btn_loop, "setColorFilter", defaultColor)
                    views.setInt(shura.media.R.id.widget_btn_shuffle, "setColorFilter", defaultColor)
                }

                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, shura.media.R.id.widget_list)
            }
        }
    }

    private val widgetCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == "shura.media.ACTION_UPDATE_NOTIFICATION") {
                updateCustomLayout()
                promoteSessionNotificationNow(forceForeground = false)
                return
            }
            val player = PlayerManager.exoPlayer ?: return
            val cmd = intent.getStringExtra("command")
            shura.media.LogKeeper.log("widgetCommandReceiver received command: $cmd", "PlaybackService")
            when (cmd) {
                "ACTION_MINIPLAYER", "ACTION_OVERLAY" -> showOverlay(false)
                "ACTION_VIDEO_OVERLAY" -> showOverlay(true)
                "ACTION_HIDE_OVERLAY" -> hideOverlay()
                "ACTION_CLOSE" -> {
                    shura.media.LogKeeper.log("ACTION_CLOSE received -> stopping and removing notification", "PlaybackService")
                    performFullPlaybackTeardown(flushProgress = true)
                    return
                }
                "ACTION_PLAY_PAUSE" -> if (player.isPlaying) player.pause() else player.play()
                "ACTION_PREV" -> {
                    val wasEndedOrIdle = player.playbackState == androidx.media3.common.Player.STATE_ENDED ||
                        player.playbackState == androidx.media3.common.Player.STATE_IDLE
                    if (player.currentPosition > 3000L && !wasEndedOrIdle) {
                        player.seekTo(0)
                    } else if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                    } else if (player.mediaItemCount > 1) {
                        player.seekTo(player.mediaItemCount - 1, 0)
                    } else {
                        player.seekTo(0)
                    }
                    if (wasEndedOrIdle || player.playWhenReady) {
                        player.prepare()
                        player.play()
                    }
                }
                "ACTION_NEXT" -> {
                    val wasEndedOrIdle = player.playbackState == androidx.media3.common.Player.STATE_ENDED ||
                        player.playbackState == androidx.media3.common.Player.STATE_IDLE
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                    } else if (player.mediaItemCount > 1) {
                        player.seekTo(0, 0)
                    } else {
                        player.seekTo(0)
                    }
                    if (wasEndedOrIdle || player.playWhenReady) {
                        player.prepare()
                        player.play()
                    }
                }
                "ACTION_PLAY_ITEM" -> {
                    val index = intent.getIntExtra("index", -1)
                    if (index >= 0) player.seekToDefaultPosition(index)
                }
                "ACTION_PLAY_FILE" -> {
                    val uriStr = intent.getStringExtra("uri")
                    if (!uriStr.isNullOrBlank()) {
                        val rawUri = try { android.net.Uri.parse(uriStr) } catch (_: Exception) { null }
                        if (!isAllowedMediaUri(rawUri)) {
                            shura.media.LogKeeper.logWarn("PlaybackService", "Rejected disallowed URI scheme in ACTION_PLAY_FILE")
                        } else {
                            val resolvedUri = shura.media.data.MediaUriResolver.resolveToPlayableUri(applicationContext, rawUri!!)
                            val resolvedStr = resolvedUri.toString()
                            val fileName = shura.media.data.MediaUriResolver.extractCleanTitleFromUri(
                                rawUri,
                                shura.media.data.MediaUriResolver.extractCleanTitleFromUri(resolvedUri, "Media")
                            )
                            val mediaItem = androidx.media3.common.MediaItem.Builder()
                                .setUri(resolvedUri)
                                .setMediaId(resolvedStr)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(fileName)
                                        .setDisplayTitle(fileName)
                                        .build()
                                )
                                .build()
                            val settings = shura.media.data.SettingsManager.getInstance(applicationContext)
                            val isAudio = PlayerManager.isAudioOnlyHint(applicationContext, resolvedUri) || PlayerManager.isAudioOnlyHint(applicationContext, rawUri)
                            PlayerManager.initSessionBackgroundPlayForNewMedia(isAudio, settings.defaultAudioBackgroundPlay, resolvedStr)
                            val lastPos = settings.getPlaybackPosition(resolvedStr, fileName)
                            val startPos = if (lastPos > 0 && !settings.isFinished(resolvedStr, fileName)) lastPos else 0L
                            player.setMediaItem(mediaItem, startPos)
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
            updateWidgetUI()
        }
    }

    companion object {
        private const val PAUSE_GRACE_DURATION_MS = 5 * 60 * 1000L

        @Volatile
        internal var instance: PlaybackService? = null

        fun applyStagedPlaybackOrStart(context: android.content.Context) {
            val runBlock = {
                val inst = instance
                if (inst != null && !inst.isServiceDestroying) {
                    if (inst.isServiceStopping) {
                        inst.isServiceStopping = false
                        try {
                            val intent = android.content.Intent(context.applicationContext, PlaybackService::class.java).apply {
                                putExtra("command", "ACTION_APPLY_STAGED")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.applicationContext.startService(intent)
                        } catch (_: Exception) {}
                    }
                    inst.applyStagedPlaybackRequest()
                } else {
                    try {
                        val intent = android.content.Intent(context.applicationContext, PlaybackService::class.java).apply {
                            putExtra("command", "ACTION_APPLY_STAGED")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.applicationContext.startService(intent)
                    } catch (_: Exception) {}
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runBlock()
            } else {
                Handler(Looper.getMainLooper()).post { runBlock() }
            }
        }

        fun openOverlay(context: android.content.Context, videoMode: Boolean) {
            val runBlock = {
                val cmd = if (videoMode) "ACTION_VIDEO_OVERLAY" else "ACTION_MINIPLAYER"
                val inst = instance
                if (inst != null && !inst.isServiceDestroying) {
                    if (inst.isServiceStopping) {
                        inst.isServiceStopping = false
                        try {
                            val serviceIntent = android.content.Intent(context.applicationContext, PlaybackService::class.java).apply {
                                putExtra("command", cmd)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
                        } catch (_: Exception) {}
                    }
                    inst.applyStagedPlaybackRequest()
                    inst.showOverlay(videoMode)
                    inst.promoteSessionNotificationNow(forceForeground = true)
                } else {
                    try {
                        val serviceIntent = android.content.Intent(context.applicationContext, PlaybackService::class.java).apply {
                            putExtra("command", cmd)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
                    } catch (_: Exception) {}
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runBlock()
            } else {
                Handler(Looper.getMainLooper()).post { runBlock() }
            }
        }

        fun hideOverlay(context: android.content.Context) {
            val inst = instance
            if (inst != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    inst.hideOverlay()
                } else {
                    Handler(Looper.getMainLooper()).post { inst.hideOverlay() }
                }
            }
        }

        fun stopPlaybackAndRelease(context: android.content.Context) {
            val stopBlock = {
                val inst = instance
                if (inst != null) {
                    try {
                        inst.performFullPlaybackTeardown(flushProgress = false)
                    } catch (e: Exception) {
                        shura.media.LogKeeper.logError("PlaybackService", "Error in stopPlaybackAndRelease", e)
                        shura.media.widget.MediaWidgetProvider.resetAllWidgetsToIdle(context)
                    }
                } else {
                    try {
                        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                        nm?.cancel(androidx.media3.session.DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
                        nm?.cancel(1002)
                    } catch (_: Exception) {}
                    PlayerManager.isFloatingVideoActive = false
                    PlayerManager.floatingSession.reset()
                    PlayerManager.releaseOwnedPlayer()
                    shura.media.widget.MediaWidgetProvider.resetAllWidgetsToIdle(context)
                    try {
                        context.stopService(android.content.Intent(context, PlaybackService::class.java))
                    } catch (_: Exception) {}
                }
                Unit
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                stopBlock()
            } else {
                Handler(Looper.getMainLooper()).post { stopBlock() }
            }
        }
    }
}
