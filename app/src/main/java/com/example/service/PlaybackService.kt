package com.example.service

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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            .setIconResId(com.example.R.drawable.ic_widget_prev)
            .build()

        val playPauseButton = defaultButtons.firstOrNull {
            it.playerCommand == androidx.media3.common.Player.COMMAND_PLAY_PAUSE
        } ?: run {
            val isPlaying = session.player.isPlaying
            val icon = if (isPlaying) com.example.R.drawable.ic_widget_pause else com.example.R.drawable.ic_widget_play
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
            .setIconResId(com.example.R.drawable.ic_widget_next)
            .build()

        val miniPlayerButton = customLayout.firstOrNull { it.sessionCommand?.customAction == "ACTION_OVERLAY" }
            ?: androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Mini Player")
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
                .setIconResId(com.example.R.drawable.ic_widget_miniplayer)
                .build()

        val closeButton = customLayout.firstOrNull { it.sessionCommand?.customAction == "ACTION_CLOSE" }
            ?: androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Close")
                .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
                .setIconResId(com.example.R.drawable.ic_widget_close)
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
com.example.LogKeeper.logError("PlaybackService", "Failed to set up MediaNotificationProvider", e)
}

val settings = com.example.data.SettingsManager.getInstance(this)
PlayerManager.initialize(this, false)

val filter = android.content.IntentFilter("com.example.ACTION_WIDGET_COMMAND")
filter.addAction("com.example.ACTION_UPDATE_NOTIFICATION")
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
registerReceiver(widgetCommandReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
} else {
registerReceiver(widgetCommandReceiver, filter)
}

PlayerManager.exoPlayer?.addListener(object : Player.Listener {
override fun onIsPlayingChanged(isPlaying: Boolean) {
    com.example.LogKeeper.log("PlaybackService: onIsPlayingChanged = $isPlaying", "PlaybackService")
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
    com.example.LogKeeper.log("PlaybackService: onMediaItemTransition (reason: $reasonStr)", "PlaybackService")
    decoderServiceRetryCount = 0
    updateWidgetUI()
}
override fun onRepeatModeChanged(repeatMode: Int) {
    com.example.LogKeeper.log("PlaybackService: onRepeatModeChanged = $repeatMode", "PlaybackService")
    PlayerManager.exoPlayer?.pauseAtEndOfMediaItems = false
    updateWidgetUI()
}
override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    com.example.LogKeeper.log("PlaybackService: onShuffleModeEnabledChanged = $shuffleModeEnabled", "PlaybackService")
    updateWidgetUI()
}
override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
    com.example.LogKeeper.log("PlaybackService: onTimelineChanged (itemCount=${timeline.windowCount})", "PlaybackService")
    updateWidgetUI()
}
override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
        com.example.LogKeeper.log("PlaybackService: Timeout caught during surface detachment or release - ignored gracefully", "PlaybackService")
        return
    }
    val rootCause = error.cause
    if (rootCause is SecurityException || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION) {
        com.example.LogKeeper.log("PlaybackService: Permission denied reading media URI - skipping item gracefully: ${rootCause?.message}", "PlaybackService")
        val player = PlayerManager.exoPlayer
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player?.stop()
        }
        return
    }
    val cause = error.cause?.message ?: "Unknown"
    com.example.LogKeeper.logError("PlaybackService", "Error: ${error.errorCodeName} - ${error.message} - Cause: $cause", error)
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

        if (decoderServiceRetryCount < 2) {
            decoderServiceRetryCount++
            val currentPos = player.currentPosition
            com.example.LogKeeper.log("PlaybackService: Decoder error detected, preparing player with fallback (attempt $decoderServiceRetryCount)...", "PlaybackService")
            player.prepare()
            if (currentPos > 0) {
                player.seekTo(currentPos)
            }
            player.play()
        } else {
            com.example.LogKeeper.log("PlaybackService: Decoder error exceeded retry limit, pausing player.", "PlaybackService")
            player.pause()
        }
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
    com.example.LogKeeper.log("Playback state changed to: $stateName", "PlaybackService")
    if (playbackState == Player.STATE_ENDED) {
        val player = PlayerManager.exoPlayer
        if (player?.repeatMode == Player.REPEAT_MODE_OFF) {
            com.example.LogKeeper.log("Playback ended with repeatMode OFF -> stopping service & removing notification", "PlaybackService")
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancelAll()
            } catch (e: Exception) {
                com.example.LogKeeper.logError("PlaybackService", "Error stopping foreground on STATE_ENDED", e)
            }
            stopSelf()
        }
    } else if (playbackState == Player.STATE_IDLE) {
        val player = PlayerManager.exoPlayer
        if (player != null && player.mediaItemCount == 0) {
            com.example.LogKeeper.log("Playback IDLE with 0 items -> stopping service & removing notification", "PlaybackService")
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancelAll()
            } catch (e: Exception) {}
            stopSelf()
        }
    }
}
override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
    com.example.LogKeeper.log("PlaybackService: onPlayWhenReadyChanged = $playWhenReady, reason = $reason", "PlaybackService")
    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
        val player = PlayerManager.exoPlayer
        if (player?.repeatMode == Player.REPEAT_MODE_OFF && player.hasNextMediaItem() == false) {
            com.example.LogKeeper.log("End of media item reached with repeatMode OFF and no next item -> stopping player", "PlaybackService")
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancelAll()
            } catch (e: Exception) {}
            player.stop()
            player.clearMediaItems()
            stopSelf()
        }
    }
}
})


val intent = android.content.Intent(this, com.example.ui.PlayerActivity::class.java).apply {
action = "com.example.ACTION_OPEN_PLAYER"
}
val pendingIntent = android.app.PendingIntent.getActivity(
this, 0, intent,
android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
)

val rawPlayer = PlayerManager.exoPlayer!!
val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(rawPlayer) {
    override fun getAvailableCommands(): androidx.media3.common.Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ) {
            return true
        }
        return super.isCommandAvailable(command)
    }
}

mediaSession = MediaSession.Builder(this, forwardingPlayer)
.setSessionActivity(pendingIntent)
.setBitmapLoader(com.example.MyBitmapLoader(this))
.setCallback(object : MediaSession.Callback {
override fun onConnect(
session: MediaSession,
controller: MediaSession.ControllerInfo
): MediaSession.ConnectionResult {
val defaultResult = super.onConnect(session, controller)
val customCommands = defaultResult.availableSessionCommands.buildUpon()
.add(androidx.media3.session.SessionCommand("ADD_SUBTITLE", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("SET_BOOST_GAIN", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_MORE", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_LESS", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_LOOP", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
.add(androidx.media3.session.SessionCommand("ACTION_PIP", android.os.Bundle.EMPTY))
.build()
val playerCommands = defaultResult.availablePlayerCommands.buildUpon()
.add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
.add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
.add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
.add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
.build()
return MediaSession.ConnectionResult.accept(customCommands, playerCommands)
}

override fun onPlayerCommandRequest(
session: MediaSession,
controller: MediaSession.ControllerInfo,
playerCommand: Int
): Int {
if (playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT ||
    playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
    val player = session.player
    if (player.hasNextMediaItem()) {
        player.seekToNextMediaItem()
    } else if (player.mediaItemCount > 1) {
        player.seekTo(0, 0)
    } else {
        player.seekTo(0)
    }
    return androidx.media3.session.SessionResult.RESULT_SUCCESS
}
if (playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS ||
    playerCommand == androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
    val player = session.player
    if (player.currentPosition > 3000L) {
        player.seekTo(0)
    } else if (player.hasPreviousMediaItem()) {
        player.seekToPreviousMediaItem()
    } else if (player.mediaItemCount > 1) {
        player.seekTo(player.mediaItemCount - 1, 0)
    } else {
        player.seekTo(0)
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
if (customCommand.customAction == "SET_BOOST_GAIN") {
val gainMb = args.getInt("gainMb", 0)
PlayerManager.setBoostGain(gainMb)
return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_CLOSE") {
val player = session.player
player.stop()
player.clearMediaItems()
stopSelf()
return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ACTION_LOOP") {
val player = session.player
player.repeatMode = when (player.repeatMode) {
androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
else -> androidx.media3.common.Player.REPEAT_MODE_OFF
}
updateCustomLayout()
return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}
if (customCommand.customAction == "ACTION_OVERLAY") {
if (!android.provider.Settings.canDrawOverlays(this@PlaybackService)) {
val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
startActivity(intent)
} else {
if (composeView == null) {
showOverlay(false)
} else {
hideOverlay()
}
}
return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}
if (customCommand.customAction == "ACTION_PIP") {
if (!android.provider.Settings.canDrawOverlays(this@PlaybackService)) {
val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
startActivity(intent)
} else {
showOverlay(true)
}
return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
}

if (customCommand.customAction == "ADD_SUBTITLE") {
val uriStr = args.getString("subtitle_uri")
if (uriStr != null) {
val player = session.player
val currentItem = player.currentMediaItem
if (currentItem != null) {
val mimeType = if (uriStr.endsWith(".vtt", true)) androidx.media3.common.MimeTypes.TEXT_VTT
else if (uriStr.endsWith(".ssa", true) || uriStr.endsWith(".ass", true)) androidx.media3.common.MimeTypes.TEXT_SSA
else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP

val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(uriStr))
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

// Reset the track selection to enable text tracks
val builder = player.trackSelectionParameters.buildUpon()
builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
player.trackSelectionParameters = builder.build()
}
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
com.example.LogKeeper.log("onAddMediaItems called with ${mediaItems.size} items", "PlaybackService")
val updatedMediaItems = mediaItems.map { mediaItem ->
val uriToUse = mediaItem.localConfiguration?.uri?.toString() ?: mediaItem.mediaId
com.example.LogKeeper.log("Transforming mediaItem (item count: ${mediaItems.size})", "PlaybackService")
mediaItem.buildUpon()
.setUri(uriToUse)
.build()
}
return Futures.immediateFuture(updatedMediaItems)
}
}).build()

updateCustomLayout()

addSession(mediaSession!!)
}

override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
return mediaSession
}

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        super.onTaskRemoved(rootIntent)
        com.example.LogKeeper.log("onTaskRemoved called, cleaning up.", "PlaybackService")
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == androidx.media3.common.Player.STATE_ENDED)) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                notificationManager?.cancelAll()
            } catch (e: Exception) {}
            player.stop()
            resetSessionFloatingState()
            stopSelf()
        }
    }


@SuppressLint("ClickableViewAccessibility")
private fun showOverlay(startInVideoMode: Boolean = false) {
if (startInVideoMode) {
    com.example.service.PlayerManager.isFloatingVideoActive = true
}
if (composeView != null) return
val cv = ComposeView(this)
composeView = cv
cv.setViewTreeLifecycleOwner(this@PlaybackService)
cv.setViewTreeViewModelStoreOwner(this@PlaybackService)
cv.setViewTreeSavedStateRegistryOwner(this@PlaybackService)

val prefs = getSharedPreferences("MiniPlayerPrefs", android.content.Context.MODE_PRIVATE)

cv.setContent {
var isMinimized by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sessionIsMinimized) }
var isVideoMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(startInVideoMode) }
var videoAspectRatio by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(16f / 9f) }
var isAspectRatioBroken by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sessionIsAspectRatioBroken) }

val handleMinimizeToggle: (Boolean) -> Unit = { minimized ->
    if (isMinimized != minimized) {
        isMinimized = minimized
        sessionIsMinimized = minimized
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
            windowManager.updateViewLayout(cv, lp)
        }
    }
}

val updateWindowForAspectRatio: (Float) -> Unit = { aspect ->
    if (aspect > 0.2f && aspect < 5.0f) {
        videoAspectRatio = aspect
        val lp = layoutParams
        val currentCv = composeView
        if (lp != null && currentCv != null && isVideoMode && !isMinimized) {
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

                try {
                    windowManager.updateViewLayout(currentCv, lp)
                } catch (e: Exception) {
                    com.example.LogKeeper.logError("PlaybackService", "Error updating floating window aspect ratio", e)
                }
            }
        }
    }
}

com.example.ui.theme.MyApplicationTheme {
if (isVideoMode) {
com.example.ui.components.FloatingVideoPlayerOverlay(
player = com.example.service.PlayerManager.exoPlayer,
onClose = {
val player = com.example.service.PlayerManager.exoPlayer
player?.stop()
player?.clearMediaItems()
resetSessionFloatingState()
hideOverlay()
stopSelf()
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
    windowManager.updateViewLayout(cv, lp)
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

    windowManager.updateViewLayout(cv, lp)
}
},
onOpenMainPlayer = {
    val currentMedia = com.example.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
        ?: com.example.service.PlayerManager.playbackState.value.playlist.getOrNull(
            com.example.service.PlayerManager.playbackState.value.currentIndex
        )?.mediaId
    val intent = android.content.Intent(this@PlaybackService, com.example.ui.PlayerActivity::class.java).apply {
        action = "com.example.ACTION_OPEN_PLAYER"
        if (!currentMedia.isNullOrEmpty()) {
            putExtra("uri", currentMedia)
        }
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    startActivity(intent)
    hideOverlay()
},
onSwitchToMiniPlayer = {
isVideoMode = false
com.example.service.PlayerManager.detachVideoSurface()
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
    windowManager.updateViewLayout(cv, lp)
}
},
onAspectRatioChanged = updateWindowForAspectRatio
)
} else {
com.example.ui.components.MiniPlayerOverlay(
player = com.example.service.PlayerManager.exoPlayer,
onClose = {
val player = com.example.service.PlayerManager.exoPlayer
player?.stop()
player?.clearMediaItems()
resetSessionFloatingState()
hideOverlay()
stopSelf()
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
    windowManager.updateViewLayout(cv, lp)
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
    windowManager.updateViewLayout(cv, lp)
}
},
isMinimizedExternal = isMinimized,
onMinimizeChange = { minimized ->
    handleMinimizeToggle(minimized)
},
onSwitchToVideo = {
    isVideoMode = true
    val player = com.example.service.PlayerManager.exoPlayer
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
com.example.service.PlayerManager.isFloatingVideoActive = false
composeView?.let {
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
windowManager.removeView(it)
}
composeView = null
}

    private fun updateCustomLayout() {
        val miniPlayerAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Mini Player")
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_OVERLAY", android.os.Bundle.EMPTY))
            .setIconResId(com.example.R.drawable.ic_widget_miniplayer)
            .build()

        val closeAction = androidx.media3.session.CommandButton.Builder()
            .setDisplayName("Close")
            .setSessionCommand(androidx.media3.session.SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY))
            .setIconResId(com.example.R.drawable.ic_widget_close)
            .build()

        mediaSession?.setCustomLayout(listOf(miniPlayerAction, closeAction))
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val cmd = intent?.getStringExtra("command")
        com.example.LogKeeper.log("PlaybackService onStartCommand with command: $cmd", "PlaybackService")
        if (cmd == "ACTION_VIDEO_OVERLAY") {
            if (android.provider.Settings.canDrawOverlays(this)) {
                showOverlay(true)
            }
        } else if (cmd == "ACTION_MINIPLAYER" || cmd == "ACTION_OVERLAY") {
            if (android.provider.Settings.canDrawOverlays(this)) {
                showOverlay(false)
            }
        } else if (cmd == "ACTION_HIDE_OVERLAY") {
            hideOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        com.example.LogKeeper.log("PlaybackService onDestroy: cleaning up notification and session", "PlaybackService")
        try { unregisterReceiver(widgetCommandReceiver) } catch (e: Exception) {}
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            notificationManager?.cancelAll()
        } catch (e: Exception) {
            com.example.LogKeeper.logError("PlaybackService", "Error stopping foreground on onDestroy", e)
        }
        mediaSession?.run {
            PlayerManager.release()
            release()
            mediaSession = null
        }
        lifecycleRegistry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        resetSessionFloatingState()
        hideOverlay()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun updateWidgetUI() {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val componentName = android.content.ComponentName(this, com.example.widget.MediaWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            val player = PlayerManager.exoPlayer ?: return
            for (appWidgetId in appWidgetIds) {
                val views = android.widget.RemoteViews(packageName, com.example.R.layout.widget_media)
                views.setTextViewText(com.example.R.id.widget_title, player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "No Media")
                views.setImageViewResource(com.example.R.id.widget_btn_play, if (player.isPlaying) com.example.R.drawable.ic_widget_pause else com.example.R.drawable.ic_widget_play)
                val duration = player.duration.coerceAtLeast(1)
                val position = player.currentPosition
                val progress = if (duration > 0) ((position.toFloat() / duration.toFloat()) * 100).toInt() else 0
                views.setProgressBar(com.example.R.id.widget_progress, 100, progress, false)
                

                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, com.example.R.id.widget_list)
            }
        }
    }

    private val widgetCommandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == "com.example.ACTION_UPDATE_NOTIFICATION") {
                updateCustomLayout()
                return
            }
            val player = PlayerManager.exoPlayer ?: return
            val cmd = intent.getStringExtra("command")
            com.example.LogKeeper.log("widgetCommandReceiver received command: $cmd", "PlaybackService")
            when (cmd) {
                "ACTION_MINIPLAYER", "ACTION_OVERLAY" -> showOverlay(false)
                "ACTION_VIDEO_OVERLAY" -> showOverlay(true)
                "ACTION_HIDE_OVERLAY" -> hideOverlay()
                "ACTION_CLOSE" -> {
                    com.example.LogKeeper.log("ACTION_CLOSE received -> stopping and removing notification", "PlaybackService")
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        }
                        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                        notificationManager?.cancelAll()
                    } catch (e: Exception) {}
                    player.stop()
                    player.clearMediaItems()
                    resetSessionFloatingState()
                    hideOverlay()
                    stopSelf()
                }
                "ACTION_PLAY_PAUSE" -> if (player.isPlaying) player.pause() else player.play()
                "ACTION_PREV" -> player.seekToPreviousMediaItem()
                "ACTION_NEXT" -> player.seekToNextMediaItem()
                "ACTION_PLAY_ITEM" -> {
                    val index = intent.getIntExtra("index", -1)
                    if (index >= 0) player.seekToDefaultPosition(index)
                }
                "ACTION_PLAY_FILE" -> {
                    val uriStr = intent.getStringExtra("uri")
                    if (uriStr != null) {
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setUri(uriStr)
                            .setMediaId(uriStr)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(android.net.Uri.parse(uriStr).lastPathSegment ?: "Unknown")
                                    .build()
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    }
                }
            }
            updateWidgetUI()
        }
    }

    companion object {
        private var instance: PlaybackService? = null

        fun hideOverlay(context: android.content.Context) {
            val inst = instance
            if (inst != null) {
                inst.hideOverlay()
            } else {
                try {
                    val intent = android.content.Intent(context, PlaybackService::class.java).apply {
                        putExtra("command", "ACTION_HIDE_OVERLAY")
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    try {
                        val broadcastIntent = android.content.Intent("com.example.ACTION_WIDGET_COMMAND").apply {
                            putExtra("command", "ACTION_HIDE_OVERLAY")
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(broadcastIntent)
                    } catch (e2: Exception) {}
                }
            }
        }
    }
}
