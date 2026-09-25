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

// Removed inactivity timeout

override fun onCreate() {
super.onCreate()
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
    PlayerManager.exoPlayer?.pauseAtEndOfMediaItems = (repeatMode == Player.REPEAT_MODE_OFF)
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
    val cause = error.cause?.message ?: "Unknown"
    com.example.LogKeeper.logError("PlaybackService", "Error: ${error.errorCodeName} - ${error.message} - Cause: $cause", error)
    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
        com.example.LogKeeper.log("PlaybackService: Timeout caught during surface detachment or release - ignored gracefully", "PlaybackService")
        return
    }
    val player = PlayerManager.exoPlayer
    if (player != null && (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)) {
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
        if (player?.repeatMode == Player.REPEAT_MODE_OFF) {
            com.example.LogKeeper.log("End of media item reached with repeatMode OFF -> stopping player", "PlaybackService")
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


val intent = android.content.Intent(this, com.example.MainActivity::class.java).apply {
action = "com.example.ACTION_OPEN_PLAYER"
}
val pendingIntent = android.app.PendingIntent.getActivity(
this, 0, intent,
android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
)
mediaSession = MediaSession.Builder(this, PlayerManager.exoPlayer!!)
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
    stopSelf()
}
}


@SuppressLint("ClickableViewAccessibility")
private fun showOverlay(startInVideoMode: Boolean = false) {
if (composeView != null) return
val cv = ComposeView(this)
composeView = cv
cv.setViewTreeLifecycleOwner(this@PlaybackService)
cv.setViewTreeViewModelStoreOwner(this@PlaybackService)
cv.setViewTreeSavedStateRegistryOwner(this@PlaybackService)

val prefs = getSharedPreferences("MiniPlayerPrefs", android.content.Context.MODE_PRIVATE)

cv.setContent {
var isMinimized by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
var isVideoMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(startInVideoMode) }
var videoAspectRatio by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(16f / 9f) }

val updateWindowForAspectRatio: (Float) -> Unit = { aspect ->
    if (aspect > 0.2f && aspect < 5.0f) {
        videoAspectRatio = aspect
        val lp = layoutParams
        val currentCv = composeView
        if (lp != null && currentCv != null && isVideoMode) {
            val metrics = resources.displayMetrics
            val topBarHeightPx = (32 * metrics.density).toInt()
            val minWidth = (200 * metrics.density).toInt()
            val maxWidth = (metrics.widthPixels * 0.95f).toInt()
            val maxHeight = (metrics.heightPixels * 0.7f).toInt()

            var targetWidth = lp.width.coerceIn(minWidth, maxWidth)
            var targetHeight = ((targetWidth) / aspect).toInt() + topBarHeightPx

            if (targetHeight > maxHeight) {
                targetHeight = maxHeight
                targetWidth = (((targetHeight - topBarHeightPx) * aspect).toInt()).coerceIn(minWidth, maxWidth)
            }

            if (lp.y + targetHeight > metrics.heightPixels) {
                lp.y = (metrics.heightPixels - targetHeight - (16 * metrics.density).toInt()).coerceAtLeast(0)
            }
            if (lp.x + targetWidth > metrics.widthPixels) {
                lp.x = (metrics.widthPixels - targetWidth - (16 * metrics.density).toInt()).coerceAtLeast(0)
            }

            if (lp.width != targetWidth || lp.height != targetHeight) {
                lp.width = targetWidth
                lp.height = targetHeight
                try {
                    windowManager.updateViewLayout(currentCv, lp)
                    prefs.edit().putInt("width", lp.width).putInt("height", lp.height).apply()
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
hideOverlay()
stopSelf()
},
onMinimize = {
    isMinimized = true
    val lp = layoutParams
    if (lp != null) {
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(cv, lp)
    }
},
isMinimizedExternal = isMinimized,
onMinimizeChange = { minimized ->
    isMinimized = minimized
    val lp = layoutParams
    if (lp != null) {
        if (minimized) {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            val metrics = resources.displayMetrics
            val topBarHeightPx = (32 * metrics.density).toInt()
            val minWidth = (200 * metrics.density).toInt()
            val maxWidth = (metrics.widthPixels * 0.95f).toInt()
            val maxHeight = (metrics.heightPixels * 0.7f).toInt()
            val aspect = videoAspectRatio.coerceIn(0.4f, 2.5f)
            val w = prefs.getInt("width", (300 * metrics.density).toInt()).coerceIn(minWidth, maxWidth)
            var h = ((w / aspect).toInt() + topBarHeightPx)
            if (h > maxHeight) {
                h = maxHeight
            }
            lp.width = w
            lp.height = h
        }
        windowManager.updateViewLayout(cv, lp)
    }
},
onDrag = { dx, dy ->
val lp = layoutParams
if (lp != null) {
lp.x += dx.toInt()
lp.y += dy.toInt()
windowManager.updateViewLayout(cv, lp)
prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
}
},
onResize = { dw, dh ->
val lp = layoutParams
if (lp != null) {
val metrics = resources.displayMetrics
val topBarHeightPx = (32 * metrics.density).toInt()
val minWidth = (200 * metrics.density).toInt()
val maxWidth = (metrics.widthPixels * 0.95f).toInt()
val maxHeight = (metrics.heightPixels * 0.7f).toInt()

val aspect = videoAspectRatio.coerceIn(0.4f, 2.5f)
val newWidth = (lp.width + dw.toInt()).coerceIn(minWidth, maxWidth)
var newHeight = ((newWidth) / aspect).toInt() + topBarHeightPx
if (newHeight > maxHeight) {
    newHeight = maxHeight
}

lp.width = newWidth
lp.height = newHeight
windowManager.updateViewLayout(cv, lp)
prefs.edit().putInt("width", lp.width).putInt("height", lp.height).apply()
}
},
onOpenMainPlayer = {
    val currentMedia = com.example.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
        ?: com.example.service.PlayerManager.playbackState.value.playlist.getOrNull(
            com.example.service.PlayerManager.playbackState.value.currentIndex
        )?.mediaId
    val intent = android.content.Intent(this@PlaybackService, com.example.MainActivity::class.java).apply {
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
    lp.width = (300 * metrics.density).toInt()
    lp.height = (200 * metrics.density).toInt()
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
hideOverlay()
stopSelf()
},
onMinimize = {
hideOverlay()
},
onDrag = { dx, dy ->
val lp = layoutParams
if (lp != null) {
lp.x += dx.toInt()
lp.y += dy.toInt()
windowManager.updateViewLayout(cv, lp)
prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
}
},
onResize = { dw, dh ->
val lp = layoutParams
if (lp != null) {
lp.width = (lp.width + dw.toInt()).coerceAtLeast(400)
lp.height = (lp.height + dh.toInt()).coerceAtLeast(400)
windowManager.updateViewLayout(cv, lp)
prefs.edit().putInt("width", lp.width).putInt("height", lp.height).apply()
}
},
isMinimizedExternal = isMinimized,
onMinimizeChange = { minimized ->
isMinimized = minimized
val lp = layoutParams
if (lp != null) {
if (minimized) {
lp.width = WindowManager.LayoutParams.WRAP_CONTENT
lp.height = WindowManager.LayoutParams.WRAP_CONTENT
} else {
val metrics = resources.displayMetrics
lp.width = prefs.getInt("width", (300 * metrics.density).toInt())
lp.height = prefs.getInt("height", (200 * metrics.density).toInt())
}
windowManager.updateViewLayout(cv, lp)
}
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
val defaultWidth = (300 * metrics.density).toInt()
val widthPx = prefs.getInt("width", defaultWidth)
val initialHeight = ((widthPx) / initialAspect).toInt() + topBarHeightPx
val heightPx = if (startInVideoMode) initialHeight else prefs.getInt("height", (200 * metrics.density).toInt())
layoutParams = WindowManager.LayoutParams(
widthPx,
heightPx,
type,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
PixelFormat.TRANSLUCENT
).apply {
gravity = Gravity.TOP or Gravity.START
x = prefs.getInt("x", 100)
y = prefs.getInt("y", 100)
}
windowManager.addView(composeView, layoutParams)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
}

private fun hideOverlay() {
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
        hideOverlay()
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
}
