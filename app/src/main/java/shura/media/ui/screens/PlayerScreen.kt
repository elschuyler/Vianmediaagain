@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package shura.media.ui.screens
import shura.media.ui.screens.dialogs.*
import shura.media.ui.screens.hud.*
import shura.media.ui.screens.player.*
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import shura.media.ui.screens.player.PlayerBottomBar
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Switch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoSize
import android.content.pm.ActivityInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import shura.media.service.PlaybackService
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun getDeviceSystemBrightness(ctx: Context): Float {
    return try {
        val brightnessInt = android.provider.Settings.System.getInt(
            ctx.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS
        )
        // Android framework perceptual gamma decompression matching Quick Settings slider (P = sqrt(raw / 255.0))
        val normalized = (brightnessInt.toDouble() / 255.0).coerceIn(0.0, 1.0)
        kotlin.math.sqrt(normalized).toFloat().coerceIn(0.01f, 1f)
    } catch (e: Exception) {
        0.5f
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

enum class GestureType { NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM_PAN }

fun getFastImmediateName(uri: Uri): String {
    val decodedSegment = try {
        val last = uri.lastPathSegment
        if (last != null) Uri.decode(last) else null
    } catch (e: Exception) { uri.lastPathSegment }
    val segment = decodedSegment?.substringBeforeLast('.')?.ifBlank { null }
        ?: uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null }
    return segment ?: "Video"
}

fun getDisplayNameFromUri(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
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
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
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
    val decodedSegment = try {
        val last = uri.lastPathSegment
        if (last != null) Uri.decode(last) else null
    } catch (e: Exception) { uri.lastPathSegment }
    val segment = decodedSegment?.substringBeforeLast('.')?.ifBlank { null }
        ?: uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null }
    return segment ?: "Video"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Suppress("OPT_IN_IS_NOT_ENABLED", "OPT_IN_USAGE")
@Composable
fun PlayerScreen(
    uriString: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { shura.media.data.SettingsManager.getInstance(context) }
    val liteModeEnabled by settingsManager.liteModeEnabled.collectAsState()
    var mediaController by remember { mutableStateOf(shura.media.service.PlayerManager.exoPlayer) }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var controlsInteractionTrigger by remember { mutableLongStateOf(0L) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsName by remember { mutableStateOf("Unknown") }
    var detailsSize by remember { mutableStateOf("Unknown") }
    var detailsDate by remember { mutableStateOf("Unknown") }
    var detailsPath by remember { mutableStateOf("Unknown") }
    var isLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { androidx.compose.runtime.mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var videoWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var videoHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var topBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var bottomBarHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var brightnessInteractionTime by remember { mutableLongStateOf(0L) }
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var isInteractingWithControls by remember { mutableStateOf(false) }
    val initialBrightness = remember {
        val win = context.findActivity()?.window
        val winBrightness = win?.attributes?.screenBrightness ?: -1f
        if (winBrightness in 0.01f..1f) winBrightness else getDeviceSystemBrightness(context)
    }
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeHudTime by remember { mutableLongStateOf(0L) }
    var boostGainMb by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var activeGesture by remember { mutableStateOf(GestureType.NONE) }
    var gestureText by remember { mutableStateOf("") }
    var gestureVolumeRatio by remember { mutableFloatStateOf(0f) }
    var seekOffsetSec by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showPlayPauseFlash by remember { mutableStateOf(false) }
    var flashIsPlaying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var timeStr by remember { mutableStateOf("") }
    var batteryPct by remember { androidx.compose.runtime.mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        val date = java.util.Date()
        while (true) {
            date.time = System.currentTimeMillis()
            timeStr = sdf.format(date).lowercase(java.util.Locale.US)
            kotlinx.coroutines.delay(10000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                batteryPct = if (scale > 0) (level * 100) / scale else 100
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Auto-hide brightness slider after inactivity (extended from 4s to 6s for relaxed interaction)
    LaunchedEffect(showBrightnessSlider, brightnessInteractionTime, isAdjustingBrightness) {
        if (showBrightnessSlider && !isAdjustingBrightness) {
            kotlinx.coroutines.delay(6000L)
            showBrightnessSlider = false
        }
    }

    // Auto-hide volume HUD after gesture release (Next Player style smooth fade)
    LaunchedEffect(showVolumeHud, volumeHudTime, activeGesture) {
        if (showVolumeHud && activeGesture != GestureType.VOLUME) {
            kotlinx.coroutines.delay(1500L)
            showVolumeHud = false
        }
    }

    // Dismiss any remote mini/floating overlay to enforce main player exclusivity
    LaunchedEffect(Unit) {
        shura.media.service.PlayerManager.isFloatingVideoActive = false
        shura.media.service.PlaybackService.hideOverlay(context)
    }
    
    LaunchedEffect(showPlayPauseFlash) {
        if (showPlayPauseFlash) {
            kotlinx.coroutines.delay(400)
            showPlayPauseFlash = false
        }
    }
    val decodedUriString = remember(uriString) {
        try {
            String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        } catch (e: Exception) {
            uriString
        }
    }
    val decodedUri = remember(uriString) { Uri.parse(decodedUriString) }
    var currentMediaTitle by remember {
        val initialTitle = shura.media.service.PlayerManager.exoPlayer?.mediaMetadata?.title?.toString()
        mutableStateOf(initialTitle ?: getFastImmediateName(decodedUri))
    }
    var currentMediaUri by remember { mutableStateOf(decodedUri) }

    val playerSnapshot by shura.media.service.PlayerManager.playbackState.collectAsState()
    var isPlaying by remember { mutableStateOf(playerSnapshot.isPlaying) }

    LaunchedEffect(playerSnapshot.isPlaying) {
        isPlaying = playerSnapshot.isPlaying
    }
    
    var abRepeatStart by remember { mutableStateOf<Long?>(null) }
    var abRepeatEnd by remember { mutableStateOf<Long?>(null) }
    var sleepTimerEndTime by remember { mutableStateOf<Long?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var lastKnownIsPortrait by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    var wasPlayingBeforePause by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }
    val backgroundPlayEnabledRef = androidx.compose.runtime.rememberUpdatedState(backgroundPlayEnabled)
    val forceBackgroundPlay = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    var decoderRetryCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    androidx.activity.compose.BackHandler {
        shura.media.LogKeeper.log("BackHandler triggered in PlayerScreen (bgPlay=${backgroundPlayEnabledRef.value})", "PlayerScreen")
        try {
            context.findActivity()?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } catch (e: Exception) {}
        mediaController?.let { controller ->
            val pos = controller.currentPosition
            val dur = controller.duration
            if (pos > 0L) {
                shura.media.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
            }
        }
        if (!backgroundPlayEnabledRef.value && !forceBackgroundPlay.get()) {
            mediaController?.let { controller ->
                try { controller.pause() } catch (e: Exception) {}
                try { controller.stop() } catch (e: Exception) {}
            }
            try { playerViewRef.value?.player = null } catch (e: Exception) {}
            mediaController?.let { controller ->
                if (controller.playerError == null) {
                    try { controller.clearVideoSurface() } catch (e: Exception) {}
                    try { controller.clearMediaItems() } catch (e: Exception) {}
                }
            }
            try {
                val stopIntent = android.content.Intent(context, shura.media.service.PlaybackService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {}
        } else {
            try { playerViewRef.value?.player = null } catch (e: Exception) {}
            if (!shura.media.service.PlayerManager.isFloatingVideoActive) {
                shura.media.service.PlayerManager.detachVideoSurface()
            }
        }
        onNavigateBack()
    }

    DisposableEffect(mediaController) {
        val controller = mediaController ?: return@DisposableEffect onDispose {}
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = controller.isPlaying
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val title = mediaItem.mediaMetadata.title?.toString()
                        ?: mediaItem.mediaMetadata.displayTitle?.toString()
                    if (!title.isNullOrBlank()) {
                        currentMediaTitle = title
                    }
                    mediaItem.localConfiguration?.uri?.let { uri ->
                        currentMediaUri = uri
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val title = mediaMetadata.title?.toString() ?: mediaMetadata.displayTitle?.toString()
                if (!title.isNullOrBlank()) {
                    currentMediaTitle = title
                }
            }
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        controller.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }?.let {
            currentMediaTitle = it
        }

        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(mediaController, isPlaying, abRepeatStart, abRepeatEnd, sleepTimerEndTime) {
        if (!isPlaying) return@LaunchedEffect
        var lastSavedTime = System.currentTimeMillis()
        while (true) {
            val controller = mediaController ?: break

            // A-B repeat check
            if (abRepeatStart != null && abRepeatEnd != null) {
                val currentPos = controller.currentPosition
                if (currentPos >= abRepeatEnd!!) {
                    shura.media.LogKeeper.log("A-B repeat triggered: seeking back to $abRepeatStart", "PlayerScreen")
                    controller.seekTo(abRepeatStart!!)
                }
            }

            // Sleep timer check
            if (sleepTimerEndTime != null) {
                if (System.currentTimeMillis() >= sleepTimerEndTime!!) {
                    shura.media.LogKeeper.log("Sleep timer reached: pausing playback", "PlayerScreen")
                    controller.pause()
                    sleepTimerEndTime = null
                }
            }

            // Periodic progress save
            val now = System.currentTimeMillis()
            if (now - lastSavedTime >= 3000L) {
                val pos = controller.currentPosition
                val dur = controller.duration
                if (pos > 0L) {
                    shura.media.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
                    lastSavedTime = now
                }
            }

            val delayMs = if (liteModeEnabled && !showControls) 1000L else 500L
            kotlinx.coroutines.delay(delayMs)
        }
    }

    val activity = remember(context) { context.findActivity() }

    DisposableEffect(liteModeEnabled, isPlaying, mediaController) {
        val window = activity?.window
        if (window != null) {
            if (liteModeEnabled) {
                if (isPlaying) {
                    val fps = mediaController?.videoFormat?.frameRate ?: -1f
                    val targetRate = when {
                        fps in 23f..25f -> 24f
                        fps in 29f..31f -> 30f
                        fps in 59f..61f -> 60f
                        fps > 0f -> fps
                        else -> 60f
                    }
                    val lp = window.attributes
                    lp.preferredRefreshRate = targetRate
                    window.attributes = lp
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            (playerViewRef.value?.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface?.let { surf ->
                                if (surf.isValid) {
                                    surf.setFrameRate(targetRate, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                } else {
                    // During non-active playback periods (pause, buffering, idle), clamp display to 60Hz to save battery
                    val lp = window.attributes
                    lp.preferredRefreshRate = 60f
                    window.attributes = lp
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            (playerViewRef.value?.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface?.let { surf ->
                                if (surf.isValid) {
                                    surf.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            } else {
                val lp = window.attributes
                lp.preferredRefreshRate = 0f
                window.attributes = lp
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        (playerViewRef.value?.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface?.let { surf ->
                            if (surf.isValid) {
                                surf.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        onDispose {
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.preferredRefreshRate = 0f
                win.attributes = lp
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    (playerViewRef.value?.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface?.let { surf ->
                        if (surf.isValid) {
                            surf.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    val keepScreenAwake by settingsManager.keepScreenAwake.collectAsState()
    val decodedUriStringForInit = remember(uriString) { String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)) }
    var playbackSpeed by remember { mutableFloatStateOf(settingsManager.getPlaybackSpeed(decodedUriStringForInit)) }
    var skipSilence by remember { mutableStateOf(false) }
    var repeatMode by remember { androidx.compose.runtime.mutableIntStateOf(androidx.media3.common.Player.REPEAT_MODE_OFF) }
        var showSpeedDialog by remember { mutableStateOf(false) }
        var showAudioDialog by remember { mutableStateOf(false) }
        var showSubtitleDialog by remember { mutableStateOf(false) }
        var showTopMenu by remember { mutableStateOf(false) }
        
        var subtitleColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color.White) }
        var subtitleSize by remember { mutableStateOf(16f) }
        var subtitleDelay by remember { mutableStateOf(0f) }

        val memoizedCaptionStyle = remember(subtitleColor) {
            val colorInt = android.graphics.Color.argb(
                (subtitleColor.alpha * 255).toInt(),
                (subtitleColor.red * 255).toInt(),
                (subtitleColor.green * 255).toInt(),
                (subtitleColor.blue * 255).toInt()
            )
            androidx.media3.ui.CaptionStyleCompat(
                colorInt,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                android.graphics.Color.BLACK,
                null
            )
        }

        val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                Toast.makeText(context, "Added audio: $uri", Toast.LENGTH_SHORT).show()
                // In a full implementation, you'd send this URI to the PlaybackService to add to the MergingMediaSource
            }
        }

        val subtitlePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                Toast.makeText(context, "Added subtitle: $uri", Toast.LENGTH_SHORT).show()
                shura.media.service.PlayerManager.addSubtitle(uri.toString())
            }
        }
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    var isInPipMode by remember { 
        val activity = context.findActivity() as? androidx.activity.ComponentActivity
        val isPipInitially = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
        mutableStateOf(isPipInitially) 
    }

    LaunchedEffect(showControls, isInPipMode) {
        val window = context.findActivity()?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.isAppearanceLightStatusBars = false
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (showControls && !isInPipMode) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(mediaController) {
        mediaController?.let {
            repeatMode = it.repeatMode
            it.pauseAtEndOfMediaItems = false
        }
    }

    val isAnyDialogOpen = showPlayerSettingsDialog || showDetailsDialog || showSleepTimerDialog ||
            showSpeedDialog || showAudioDialog || showSubtitleDialog || showTopMenu

    val isPlayingState = isPlaying || mediaController?.isPlaying == true
    LaunchedEffect(showControls, controlsInteractionTrigger, isAnyDialogOpen, showBrightnessSlider, isPlayingState, isInteractingWithControls) {
        if (showControls && isPlayingState && !isAnyDialogOpen && !showBrightnessSlider && !isInteractingWithControls) {
            kotlinx.coroutines.delay(3500L)
            showControls = false
            showBrightnessSlider = false
        }
    }

    LaunchedEffect(decodedUri) {
        val playable = shura.media.data.MediaUriResolver.resolveToPlayableUri(context, decodedUri)
        if (playable.scheme == "content") {
            try {
                context.grantUriPermission(context.packageName, playable, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
        }
        val mimeType = try { context.contentResolver.getType(playable) } catch (e: Exception) { null }
        val isAudio = mimeType?.startsWith("audio/") == true
        if (isAudio) {
            val settingsManager = shura.media.data.SettingsManager.getInstance(context)
            backgroundPlayEnabled = settingsManager.defaultAudioBackgroundPlay
        }
    }


    LaunchedEffect(uriString, mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        val settingsManager = shura.media.data.SettingsManager.getInstance(context)
        
        if (controller.currentMediaItem?.mediaId != decodedUri.toString()) {
            val fileName = getFastImmediateName(decodedUri)
            val pendingQueue = shura.media.service.PlayerManager.consumePendingQueue()
            
            if (pendingQueue != null && pendingQueue.first.isNotEmpty()) {
                val (queueItems, queueIndex) = pendingQueue
                val sanitizedQueue = queueItems.map { item ->
                    val itemUri = item.localConfiguration?.uri
                    if (itemUri != null) {
                        val playable = shura.media.data.MediaUriResolver.resolveToPlayableUri(context, itemUri)
                        if (playable != itemUri) {
                            item.buildUpon().setUri(playable).build()
                        } else item
                    } else item
                }
                val activeItem = sanitizedQueue.getOrNull(queueIndex)
                val activeUri = activeItem?.localConfiguration?.uri ?: decodedUri
                val activeUriString = activeUri.toString()
                val activeName = activeItem?.mediaMetadata?.title?.toString() ?: fileName
                val lastPos = settingsManager.getPlaybackPosition(activeUriString, activeName)
                val startPos = if (lastPos > 0 && !settingsManager.isFinished(activeUriString, activeName)) lastPos else 0L

                controller.setMediaItems(sanitizedQueue, queueIndex, startPos)
                controller.pauseAtEndOfMediaItems = false
                controller.prepare()
                controller.play()
                shura.media.service.PlayerManager.isImplicitFolderQueue = true
                settingsManager.markAsOpened(activeUriString, activeName)

                try {
                    val db = shura.media.data.AppDatabase.getDatabase(context)
                    val playlistRepo = shura.media.data.PlaylistRepository(db.playlistDao())
                    val uris = queueItems.map { it.mediaId }
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try { playlistRepo.saveOrUpdateTemporaryPlaylist(uris) } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            } else {
                val playableUri = shura.media.data.MediaUriResolver.resolveToPlayableUri(context, decodedUri)
                val mediaMetadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(fileName)
                    .setDisplayTitle(fileName)

                val siblingSubs = shura.media.service.SubtitleAutoDetector.detectSiblingSubtitles(context, playableUri)
                val initialMediaItemBuilder = MediaItem.Builder()
                    .setUri(playableUri)
                    .setMediaId(decodedUri.toString())
                    .setMediaMetadata(mediaMetadataBuilder.build())

                if (siblingSubs.isNotEmpty()) {
                    initialMediaItemBuilder.setSubtitleConfigurations(siblingSubs)
                }
                val initialMediaItem = initialMediaItemBuilder.build()

                val lastPos = settingsManager.getPlaybackPosition(decodedUriString, fileName)
                val startPos = if (lastPos > 0 && !settingsManager.isFinished(decodedUriString, fileName)) lastPos else 0L

                controller.setMediaItem(initialMediaItem, startPos)
                controller.pauseAtEndOfMediaItems = false
                controller.prepare()
                controller.play()
                shura.media.service.PlayerManager.isImplicitFolderQueue = true
                settingsManager.markAsOpened(decodedUriString, fileName)
            }

            // Resolve full display name asynchronously on IO dispatcher if not already resolved by PlayerActivity
            if (decodedUri.scheme == "content" && currentMediaTitle == fileName) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.query(decodedUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx != -1) {
                                    val resolvedName = cursor.getString(idx)
                                    if (!resolvedName.isNullOrBlank() && resolvedName != fileName) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            currentMediaTitle = resolvedName
                                            val currentItem = controller.currentMediaItem
                                            if (currentItem?.mediaId == decodedUri.toString()) {
                                                val updatedMeta = controller.mediaMetadata.buildUpon()
                                                    .setTitle(resolvedName)
                                                    .setDisplayTitle(resolvedName)
                                                    .build()
                                                val updatedItem = currentItem.buildUpon().setMediaMetadata(updatedMeta).build()
                                                controller.replaceMediaItem(controller.currentMediaItemIndex, updatedItem)
                                                settingsManager.markAsOpened(decodedUriString, resolvedName)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }

        // If not already populated via pending folder queue, resolve sibling playlist items targeted by parent directory / BUCKET_ID
        if (controller.mediaItemCount <= 1) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (shura.media.service.FFmpegStatus.isRunning || shura.media.service.CompressionStatus.isRunning) {
                    shura.media.LogKeeper.log("PlayerScreen: Deferred background playlist scan because FFmpeg/Compression service is running", "PlayerScreen")
                    return@launch
                }

                val (playlistItems, startIndex) = resolveSiblingPlaylistItems(context, decodedUri, decodedUri.toString())
                
                if (playlistItems.size > 1 && startIndex != -1) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val currentId = controller.currentMediaItem?.mediaId
                        if ((currentId == decodedUri.toString() || currentId == decodedUriString) && controller.mediaItemCount <= 1) {
                            val precedingItems = playlistItems.subList(0, startIndex)
                            val succeedingItems = playlistItems.subList(startIndex + 1, playlistItems.size)
                            if (precedingItems.isNotEmpty()) {
                                controller.addMediaItems(0, precedingItems)
                            }
                            if (succeedingItems.isNotEmpty()) {
                                controller.addMediaItems(controller.mediaItemCount, succeedingItems)
                            }
                            shura.media.service.PlayerManager.isImplicitFolderQueue = true
                        }
                    }
                    
                    try {
                        val db = shura.media.data.AppDatabase.getDatabase(context)
                        val playlistRepo = shura.media.data.PlaylistRepository(db.playlistDao())
                        val uris = playlistItems.map { it.mediaId }
                        playlistRepo.saveOrUpdateTemporaryPlaylist(uris)
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
            

        } else if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            controller.seekTo(0)
            controller.prepare()
            controller.play()
            wasPlayingBeforePause = true
        } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            controller.prepare()
            if (wasPlayingBeforePause) {
                controller.play()
            }
        } else {
            if (wasPlayingBeforePause) {
                controller.play()
            }
        }
    }

    var hoistedMainListener by remember { mutableStateOf<androidx.media3.common.Player.Listener?>(null) }

    DisposableEffect(uriString) {
        val settingsManager = shura.media.data.SettingsManager.getInstance(context)
        shura.media.LogKeeper.log("Starting player session", "PlayerScreen")
        
        // Ensure player is initialized
        shura.media.service.PlayerManager.initialize(context, false)
        mediaController = shura.media.service.PlayerManager.exoPlayer
        
        // Start the service for MediaSession features
        val intent = android.content.Intent(context, shura.media.service.PlaybackService::class.java).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            data = decodedUri
        }
        try {
            context.startService(intent)
        } catch(e: Exception) {}

        val controller = mediaController ?: return@DisposableEffect onDispose {}
        
        // Native decoder orientation: if video size is already available, sync orientation immediately
        if (controller.videoSize.width > 0 && controller.videoSize.height > 0) {
            val act = context.findActivity()
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                @Suppress("DEPRECATION")
                val w = if (controller.videoSize.unappliedRotationDegrees % 180 == 0) controller.videoSize.width else controller.videoSize.height
                @Suppress("DEPRECATION")
                val h = if (controller.videoSize.unappliedRotationDegrees % 180 == 0) controller.videoSize.height else controller.videoSize.width
                if (w > 0 && h > 0) {
                    val isPortrait = h > w
                    lastKnownIsPortrait = isPortrait
                    val target = if (isPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (act.requestedOrientation != target) {
                        act.requestedOrientation = target
                    }
                }
            }
        }
        
        val savedGain = settingsManager.boostGainMb
        boostGainMb = savedGain
        if (savedGain > 0) {
            shura.media.service.PlayerManager.applyAudioBoosterSettings(settingsManager.audioBoosterEnabled, savedGain)
        }
        controller.setPlaybackSpeed(playbackSpeed)
        

        fun updateOrientation(videoSize: androidx.media3.common.VideoSize) {
            val act = context.findActivity()
            if (act == null || act.isFinishing || act.isDestroyed) return
            if (controller.currentMediaItem == null || controller.playbackState == androidx.media3.common.Player.STATE_IDLE || controller.playbackState == androidx.media3.common.Player.STATE_ENDED) return
            if (videoSize.width > 0 && videoSize.height > 0) {
                @Suppress("DEPRECATION")
                val w = if (videoSize.unappliedRotationDegrees % 180 == 0) videoSize.width else videoSize.height
                @Suppress("DEPRECATION")
                val h = if (videoSize.unappliedRotationDegrees % 180 == 0) videoSize.height else videoSize.width
                val isPortrait = h > w
                lastKnownIsPortrait = isPortrait
                val targetOrientation = if (isPortrait) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                if (act.requestedOrientation != targetOrientation) {
                    shura.media.LogKeeper.log("updateOrientation called with videoSize: ${videoSize.width}x${videoSize.height} rot=${videoSize.unappliedRotationDegrees}. isPortrait=$isPortrait", "PlayerScreen")
                    act.requestedOrientation = targetOrientation
                }
            }
        }

        // updateOrientation(controller.videoSize) removed to prevent old video size from forcing incorrect orientation
        var didTriggerCompletionBack = false
        val mainListener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                shura.media.LogKeeper.log("PlayerScreen: onIsPlayingChanged = $playing", "PlayerScreen")
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                didTriggerCompletionBack = false
                val reasonStr = when (reason) {
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN($reason)"
                }
                val uri = mediaItem?.localConfiguration?.uri ?: mediaItem?.mediaId?.let { Uri.parse(it) }
                val title = mediaItem?.mediaMetadata?.title?.toString()
                shura.media.LogKeeper.log("PlayerScreen: onMediaItemTransition (reason: $reasonStr)", "PlayerScreen")
                if (uri != null) {
                    currentMediaUri = uri
                    val immediateName = title ?: getFastImmediateName(uri)
                    currentMediaTitle = immediateName
                    shura.media.data.SettingsManager.getInstance(context).markAsOpened(uri.toString(), currentMediaTitle)
                    if (title == null && uri.scheme == "content") {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            var resolved: String? = null
                            try {
                                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (idx != -1) resolved = cursor.getString(idx)
                                    }
                                }
                            } catch (e: Exception) {}
                            if (!resolved.isNullOrBlank()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    currentMediaTitle = resolved!!
                                }
                            }
                        }
                    }
                }

                // Handle auto-transition log
                if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    shura.media.LogKeeper.log("Auto-transition occurred to next item in playlist", "PlayerScreen")
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                shura.media.LogKeeper.log("PlayerScreen: onTracksChanged", "PlayerScreen")
                // Restore track selection
                val audioTrackIdx = settingsManager.getTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_AUDIO)
                val subtitleTrackIdx = settingsManager.getTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_TEXT)
                
                var builder = controller.trackSelectionParameters.buildUpon()
                var changed = false
                
                if (audioTrackIdx != -1) {
                    val audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                    var found = false
                    var totalIdx = 0
                    for (group in audioGroups) {
                        for (i in 0 until group.length) {
                            if (totalIdx == audioTrackIdx) {
                                builder.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                changed = true
                                found = true
                                break
                            }
                            totalIdx++
                        }
                        if (found) break
                    }
                }
                
                if (subtitleTrackIdx != -1) {
                    val subtitleGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                    var found = false
                    var totalIdx = 0
                    for (group in subtitleGroups) {
                        for (i in 0 until group.length) {
                            if (totalIdx == subtitleTrackIdx) {
                                builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                builder.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                changed = true
                                found = true
                                break
                            }
                            totalIdx++
                        }
                        if (found) break
                    }
                }
                
                if (changed) {
                    controller.trackSelectionParameters = builder.build()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    androidx.media3.common.Player.STATE_IDLE -> "STATE_IDLE"
                    androidx.media3.common.Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "STATE_READY"
                    androidx.media3.common.Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN"
                }
                shura.media.LogKeeper.log("PlayerScreen: PlaybackState changed to: $stateName", "PlayerScreen")
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val currentMode = controller.repeatMode
                    val hasNext = controller.hasNextMediaItem()
                    if (hasNext) {
                        shura.media.LogKeeper.log("Player reached STATE_ENDED with next item available -> advancing to next", "PlayerScreen")
                        controller.seekToNextMediaItem()
                        controller.prepare()
                        controller.play()
                    } else if (currentMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
                        shura.media.LogKeeper.log("Player reached STATE_ENDED with REPEAT_MODE_ALL -> looping to beginning", "PlayerScreen")
                        controller.seekTo(0, 0L)
                        controller.prepare()
                        controller.play()
                    } else if (currentMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        if (!didTriggerCompletionBack) {
                            didTriggerCompletionBack = true
                            shura.media.LogKeeper.log("Player reached STATE_ENDED with no loop/next -> navigating back", "PlayerScreen")
                            onNavigateBack()
                        }
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                shura.media.LogKeeper.log("PlayerScreen: PositionDiscontinuity from ${oldPosition.positionMs}ms to ${newPosition.positionMs}ms (reason: $reason)", "PlayerScreen")
            }

            override fun onRepeatModeChanged(mode: Int) {
                shura.media.LogKeeper.log("PlayerScreen: onRepeatModeChanged = $mode", "PlayerScreen")
                repeatMode = mode
                controller.pauseAtEndOfMediaItems = false
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shura.media.LogKeeper.log("PlayerScreen: onPlayWhenReadyChanged = $playWhenReady, reason = $reason", "PlayerScreen")
                if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    if (controller.hasNextMediaItem()) {
                        controller.seekToNextMediaItem()
                        controller.prepare()
                        controller.play()
                    } else if (controller.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
                        controller.seekTo(0, 0L)
                        controller.prepare()
                        controller.play()
                    } else if (controller.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        if (!didTriggerCompletionBack) {
                            didTriggerCompletionBack = true
                            shura.media.LogKeeper.log("End of media item reached with repeatMode OFF and no next item -> navigating back", "PlayerScreen")
                            onNavigateBack()
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
                    shura.media.LogKeeper.log("PlayerScreen: Timeout caught during surface detachment or release - ignored gracefully", "PlayerScreen")
                    return
                }
                val rootCause = error.cause

                // Deep scan the throwable cause chain for SecurityException or Permission Denial
                var isSecurityDenied = false
                var th: Throwable? = error
                while (th != null) {
                    if (th is SecurityException || 
                        th.message?.contains("SecurityException", ignoreCase = true) == true ||
                        th.message?.contains("Permission Denial", ignoreCase = true) == true) {
                        isSecurityDenied = true
                        break
                    }
                    th = th.cause
                }
                if (!isSecurityDenied && error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION) {
                    isSecurityDenied = true
                }

                if (isSecurityDenied) {
                    shura.media.LogKeeper.log("PlayerScreen: Permission denied reading media URI (${error.cause?.message}). Attempting dynamic MediaStore resolution fallback...", "PlayerScreen")
                    val currentItem = controller.currentMediaItem
                    val currentUri = currentItem?.localConfiguration?.uri ?: decodedUri
                    val recoveredUri = shura.media.data.MediaUriResolver.resolveToPlayableUri(context, currentUri)
                    if (recoveredUri != currentUri) {
                        shura.media.LogKeeper.log("PlayerScreen: Recovered playable URI: $recoveredUri. Re-preparing ExoPlayer.", "PlayerScreen")
                        val updatedItem = (currentItem?.buildUpon() ?: androidx.media3.common.MediaItem.Builder().setMediaId(decodedUri.toString()))
                            .setUri(recoveredUri)
                            .build()
                        val currentPos = controller.currentPosition.coerceAtLeast(0L)
                        controller.setMediaItem(updatedItem, currentPos)
                        controller.prepare()
                        controller.play()
                        return
                    }

                    shura.media.LogKeeper.log("PlayerScreen: Permission denied reading media URI - skipping item gracefully: ${error.cause?.message}", "PlayerScreen")
                    showControls = true
                    controlsInteractionTrigger = System.currentTimeMillis()
                    try {
                        android.widget.Toast.makeText(context, "Cannot read file: Permission denied", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {}
                    if (controller.hasNextMediaItem()) {
                        controller.seekToNextMediaItem()
                    } else {
                        controller.pause()
                    }
                    return
                }
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    rootCause is java.io.FileNotFoundException) {
                    shura.media.LogKeeper.log("PlayerScreen: File not found or unreadable provider - surfacing controls gracefully: ${rootCause?.message}", "PlayerScreen")
                    showControls = true
                    controlsInteractionTrigger = System.currentTimeMillis()
                    try {
                        android.widget.Toast.makeText(context, "Cannot read file: File not found", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {}
                    if (controller.hasNextMediaItem()) {
                        controller.seekToNextMediaItem()
                    } else {
                        controller.pause()
                    }
                    return
                }
                showControls = true
                controlsInteractionTrigger = System.currentTimeMillis()
                shura.media.LogKeeper.logError("PlayerScreen", "ExoPlayer Error: ${error.message}", error)
                val currentPos = controller.currentPosition
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    if (decoderRetryCount < 2) {
                        decoderRetryCount++
                        shura.media.LogKeeper.log("Decoder error encountered, attempting auto-retry $decoderRetryCount with fallback...", "PlayerScreen")
                        controller.prepare()
                        if (currentPos > 0) {
                            controller.seekTo(currentPos)
                        }
                        controller.play()
                    } else {
                        shura.media.LogKeeper.log("Decoder error exceeded max retries. Pausing playback to avoid crash loop.", "PlayerScreen")
                        controller.pause()
                    }
                }
            }
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                if (events.contains(androidx.media3.common.Player.EVENT_VIDEO_SIZE_CHANGED) || events.contains(androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    updateOrientation(player.videoSize)
                }
            }
        }
        hoistedMainListener = mainListener
        controller.addListener(mainListener)
        

        

        
        val pipListener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                @Suppress("DEPRECATION")
                val w = if (videoSize.unappliedRotationDegrees % 180 == 0) videoSize.width else videoSize.height
                @Suppress("DEPRECATION")
                val h = if (videoSize.unappliedRotationDegrees % 180 == 0) videoSize.height else videoSize.width
                PipHelper.updatePipParams(context, controller, w, h)
                videoWidth = w
                videoHeight = h
            }
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                if (events.contains(androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED) || events.contains(androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    val vs = player.videoSize
                    @Suppress("DEPRECATION")
                    val w = if (vs.unappliedRotationDegrees % 180 == 0) vs.width else vs.height
                    @Suppress("DEPRECATION")
                    val h = if (vs.unappliedRotationDegrees % 180 == 0) vs.height else vs.width
                    PipHelper.updatePipParams(context, player, w, h)
                    videoWidth = w
                    videoHeight = h
                }
            }
        }
        controller.addListener(pipListener)
        val vs = controller.videoSize
        @Suppress("DEPRECATION")
                    val w = if (vs.unappliedRotationDegrees % 180 == 0) vs.width else vs.height
        @Suppress("DEPRECATION")
                    val h = if (vs.unappliedRotationDegrees % 180 == 0) vs.height else vs.width
        PipHelper.updatePipParams(context, controller, w, h)
        videoWidth = w
        videoHeight = h
        
        val pipReceiver = PipActionReceiver(controller)
        val filter = android.content.IntentFilter(PipActionReceiver.ACTION_PIP_CONTROL)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            pipReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        
        onDispose {
            controller.removeListener(mainListener)
            controller.removeListener(pipListener)
            try {
                context.unregisterReceiver(pipReceiver)
            } catch(e: Exception) {}
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mediaController) {
        val currentController = mediaController
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            shura.media.LogKeeper.log("LifecycleEventObserver received: $event", "PlayerScreen")
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                shura.media.service.PlayerManager.flushProgressToStorage()
                currentController?.let { controller ->
                    val currentPos = controller.currentPosition
                    val dur = controller.duration
                    if (currentPos > 0L) {
                        shura.media.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, currentPos, dur, currentMediaTitle)
                    }
                    
                    val activity = context.findActivity()
                    val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
                    
                    val isCurrentlyPlaying = controller.isPlaying || controller.playWhenReady
                    wasPlayingBeforePause = isCurrentlyPlaying
                    
                    // We don't call stop() here anymore. The PlaybackService will handle
                    // inactivity timeouts (5 mins) to release resources gracefully.
                    if (!isPip && !backgroundPlayEnabledRef.value && !forceBackgroundPlay.get()) {
                        controller.pause()
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                shura.media.service.PlayerManager.setVideoTrackEnabled(true)
                currentController?.let { controller ->
                    // Re-apply orientation if known
                    lastKnownIsPortrait?.let { isPortrait ->
                        context.findActivity()?.requestedOrientation = if (isPortrait) {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else {
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }

                    // Check if playerView lost its player or needs re-binding
                    try {
                        if (playerViewRef.value?.player == null) {
                            playerViewRef.value?.player = controller
                        }
                    } catch (e: Exception) {}

                    val isCurrentItem = controller.currentMediaItem?.mediaId == decodedUri.toString()
                    if (isCurrentItem) {
                        if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                            shura.media.LogKeeper.log("PlayerScreen ON_RESUME: Controller is in STATE_IDLE for current item, calling prepare()", "PlayerScreen")
                            controller.prepare()
                        }
                        if (wasPlayingBeforePause) {
                            shura.media.LogKeeper.log("PlayerScreen ON_RESUME: Resuming playback (wasPlayingBeforePause=true)", "PlayerScreen")
                            controller.play()
                        }
                    } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE || controller.mediaItemCount == 0 || controller.currentMediaItem == null) {
                        shura.media.LogKeeper.log("PlayerScreen ON_RESUME: Controller is in STATE_IDLE or empty, re-preparing media item", "PlayerScreen")
                        val mediaMetadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                        val fileName = currentMediaTitle.ifEmpty { decodedUri.lastPathSegment ?: "Media" }
                        mediaMetadataBuilder.setTitle(fileName)
                        mediaMetadataBuilder.setDisplayTitle(fileName)
                        mediaMetadataBuilder.setArtworkUri(decodedUri)

                        val initialMediaItem = MediaItem.Builder()
                            .setUri(decodedUri)
                            .setMediaId(decodedUri.toString())
                            .setMediaMetadata(mediaMetadataBuilder.build())
                            .build()

                        controller.setMediaItem(initialMediaItem)
                        controller.prepare()

                        val savedPos = shura.media.data.SettingsManager.getInstance(context).getPlaybackPosition(decodedUriString, fileName)
                        if (savedPos > 0) {
                            controller.seekTo(savedPos)
                        }
                        if (wasPlayingBeforePause) {
                            controller.play()
                        }
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val activity = context.findActivity()
                val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) activity?.isInPictureInPictureMode == true else false
                if (!isPip && (backgroundPlayEnabledRef.value || forceBackgroundPlay.get())) {
                    if (!shura.media.service.PlayerManager.isFloatingVideoActive) {
                        shura.media.LogKeeper.log("PlayerScreen ON_STOP: Detaching video surface and halting video codec for background audio playback", "PlayerScreen")
                        try { playerViewRef.value?.player = null } catch (e: Exception) {}
                        shura.media.service.PlayerManager.detachVideoSurface()
                        shura.media.service.PlayerManager.setVideoTrackEnabled(false)
                    } else {
                        shura.media.LogKeeper.log("PlayerScreen ON_STOP: Preserving video surface for floating video overlay", "PlayerScreen")
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            try {
                context.findActivity()?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } catch (e: Exception) {}
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.findActivity()?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build()
                )
            }
            shura.media.service.PlayerManager.flushProgressToStorage()
            currentController?.let { controller ->
                val currentPos = controller.currentPosition
                val dur = controller.duration
                if (currentPos > 0L) {
                    shura.media.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, currentPos, dur, currentMediaTitle)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val activity = context.findActivity() as? androidx.activity.ComponentActivity
        val pipListener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .playerGestureDetector(
            enabled = !isLocked && !isInPipMode,
            density = density,
            touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop,
            coroutineScope = coroutineScope,
            context = context,
            showControls = showControls,
            showBrightnessSlider = showBrightnessSlider,
            boosterEnabled = shura.media.data.SettingsManager.getInstance(context).audioBoosterEnabled,
            currentBoostGainMb = boostGainMb,
            startPositionMs = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = mediaController?.duration?.coerceAtLeast(1L) ?: 1L,
            isPlaying = mediaController?.isPlaying == true,
            currentBrightness = currentBrightness,
            getDeviceBrightness = {
                val win = context.findActivity()?.window
                val winBrightness = win?.attributes?.screenBrightness ?: -1f
                if (winBrightness in 0.01f..1f) winBrightness else getDeviceSystemBrightness(context)
            },
            onSingleTap = {
                if (!showControls) {
                    showControls = true
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            },
            onDoubleTap = {
                mediaController?.let { controller ->
                    if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        controller.seekTo(0)
                        controller.prepare()
                        controller.play()
                        wasPlayingBeforePause = true
                    } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                        controller.prepare()
                        controller.play()
                        wasPlayingBeforePause = true
                    } else if (controller.isPlaying) {
                        controller.pause()
                        wasPlayingBeforePause = false
                    } else {
                        controller.play()
                        wasPlayingBeforePause = true
                    }
                    flashIsPlaying = controller.isPlaying
                    showPlayPauseFlash = true
                }
                if (showControls) {
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            },
            onSeekStart = {
                mediaController?.pause()
            },
            onSeekUpdate = { offsetMs, seekSec ->
                val curDuration = mediaController?.duration?.coerceAtLeast(1L) ?: 1L
                val targetPos = ((mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L) + offsetMs).coerceIn(0L, curDuration)
                mediaController?.seekTo(targetPos)
                seekOffsetSec = seekSec
            },
            onSeekCommit = { targetPos, resumePlay ->
                mediaController?.seekTo(targetPos)
                if (resumePlay) {
                    mediaController?.play()
                }
                if (showControls) {
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            },
            onBrightnessStart = {
                isAdjustingBrightness = true
                brightnessInteractionTime = System.currentTimeMillis()
                controlsInteractionTrigger = System.currentTimeMillis()
            },
            onBrightnessUpdate = { newBrightness ->
                currentBrightness = newBrightness
                val win = context.findActivity()?.window
                if (win != null) {
                    val lp = win.attributes
                    lp.screenBrightness = newBrightness
                    win.attributes = lp
                }
                brightnessInteractionTime = System.currentTimeMillis()
                controlsInteractionTrigger = System.currentTimeMillis()
            },
            onBrightnessEnd = { finalBrightness ->
                isAdjustingBrightness = false
                currentBrightness = finalBrightness
                val win = context.findActivity()?.window
                if (win != null) {
                    val lp = win.attributes
                    lp.screenBrightness = finalBrightness
                    win.attributes = lp
                }
                brightnessInteractionTime = System.currentTimeMillis()
                if (showControls) {
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            },
            onVolumeStart = {
                showVolumeHud = true
                volumeHudTime = System.currentTimeMillis()
            },
            onVolumeUpdate = { ratio, targetSysVol, targetBoostMb, displayText ->
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetSysVol, 0)
                if (targetBoostMb != boostGainMb) {
                    boostGainMb = targetBoostMb
                    val settings = shura.media.data.SettingsManager.getInstance(context)
                    settings.boostGainMb = targetBoostMb
                    shura.media.service.PlayerManager.applyAudioBoosterSettings(settings.audioBoosterEnabled, targetBoostMb)
                }
                gestureVolumeRatio = ratio
                gestureText = displayText
                showVolumeHud = true
                volumeHudTime = System.currentTimeMillis()
            },
            onVolumeEnd = {
                volumeHudTime = System.currentTimeMillis()
                if (showControls) {
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            },
            onZoomPanRotate = { zoomChange, panChange, rotChange ->
                scale = (scale * zoomChange).coerceIn(0.25f, 4f)
                offsetX += panChange.x
                offsetY += panChange.y
                rotationAngle += rotChange
            },
            onGestureStateChange = { newGesture ->
                activeGesture = newGesture
                if (newGesture == GestureType.NONE) {
                    gestureText = ""
                }
            }
        )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    fitsSystemWindows = false
                    setOnApplyWindowInsetsListener { _, insets -> insets }
                    playerViewRef.value = this
                }
            },
            update = { view ->
                if (view.player != mediaController) {
                    view.player = mediaController
                }
                view.resizeMode = if (resizeMode == 5) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT else resizeMode
                view.keepScreenOn = keepScreenAwake && isPlaying
                view.subtitleView?.let { subtitleView ->
                    val currentTag = subtitleView.tag as? Pair<*, *>
                    if (currentTag?.first != memoizedCaptionStyle) {
                        subtitleView.setStyle(memoizedCaptionStyle)
                    }
                    if (currentTag?.second != subtitleSize) {
                        subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSize)
                    }
                    subtitleView.tag = Pair(memoizedCaptionStyle, subtitleSize)
                }
            },
            onReset = { view ->
                // Do not strip player during view recycling/recomposition to avoid surface detachment freezes
            },
            onRelease = { view ->
                view.player = null
            },
            modifier = (if (resizeMode == 5 && videoWidth > 0 && videoHeight > 0) Modifier.size((videoWidth / density).dp, (videoHeight / density).dp) else Modifier.fillMaxSize()).graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
                rotationZ = rotationAngle
            }
        )

        PlayPauseFlashHud(
            showFlash = showPlayPauseFlash,
            flashIsPlaying = flashIsPlaying,
            modifier = Modifier.align(Alignment.Center)
        )

        if (activeGesture != GestureType.NONE && !isInPipMode) {
            when (activeGesture) {
                GestureType.SEEK -> SeekHudCapsule(seekOffsetSec = seekOffsetSec)
                else -> if (activeGesture != GestureType.VOLUME) GestureTextHudCapsule(gestureText = gestureText) else Unit
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showVolumeHud && !isInPipMode,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            VolumeHudCapsule(gestureVolumeRatio = gestureVolumeRatio)
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !showControls && !isInPipMode && !isLocked,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
        ) {
            Box(
                modifier = Modifier.padding(end = 16.dp)
            ) {
                BatteryTimeHudRow(
                    timeStr = timeStr,
                    batteryPct = batteryPct,
                    isCharging = isCharging
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showControls && !isInPipMode && !isLocked,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: Background Scrim Backdrop (captures taps on empty video area to dismiss controls & slider)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                    mediaController?.let { controller ->
                                        if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                            controller.seekTo(0)
                                            controller.prepare()
                                            controller.play()
                                            wasPlayingBeforePause = true
                                        } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                            controller.prepare()
                                            controller.play()
                                            wasPlayingBeforePause = true
                                        } else if (controller.isPlaying) {
                                            controller.pause()
                                            wasPlayingBeforePause = false
                                        } else {
                                            controller.play()
                                            wasPlayingBeforePause = true
                                        }
                                        flashIsPlaying = controller.isPlaying
                                        showPlayPauseFlash = true
                                    }
                                },
                                onTap = {
                                    // Tapping empty backdrop when controls are visible: dismiss synchronously!
                                    showControls = false
                                    showBrightnessSlider = false
                                }
                            )
                        }
                )

                // Layer 2: Top controls container (traps taps and touch drags so controls remain visible)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isInteractingWithControls = true
                                controlsInteractionTrigger = System.currentTimeMillis()
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                                isInteractingWithControls = false
                                controlsInteractionTrigger = System.currentTimeMillis()
                            }
                        }
                ) {
                        // Top controls background
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                            .align(Alignment.TopCenter)
                        )
                    
                    // Top controls overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .onGloballyPositioned { coordinates ->
                                topBarHeightPx = coordinates.size.height.toFloat()
                            }
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility.union(androidx.compose.foundation.layout.WindowInsets.displayCutout).only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Top))
                    ) {
                        LaunchedEffect(currentMediaUri) {
                            detailsName = currentMediaUri.lastPathSegment ?: "Unknown"
                            detailsPath = currentMediaUri.toString()
                            if (currentMediaUri.scheme == "file") {
                                try {
                                    val file = java.io.File(currentMediaUri.path!!)
                                    detailsName = file.name
                                    val size = file.length()
                                    detailsSize = if (size > 1024 * 1024) "${size / (1024 * 1024)} MB" else "${size / 1024} KB"
                                    detailsDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                                    detailsPath = file.absolutePath
                                } catch (e: Exception) {}
                            } else if (currentMediaUri.scheme == "content") {
                                try {
                                    context.contentResolver.query(currentMediaUri, null, null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                                            if (nameCol != -1) cursor.getString(nameCol)?.let { detailsName = it }
                                            val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                                            if (sizeCol != -1) {
                                                val size = cursor.getLong(sizeCol)
                                                detailsSize = if (size > 1024 * 1024) "${size / (1024 * 1024)} MB" else "${size / 1024} KB"
                                            }
                                            val dateCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_ADDED)
                                            if (dateCol != -1) {
                                                val dateAdded = cursor.getLong(dateCol)
                                                val dateMs = if (dateAdded < 10000000000L) dateAdded * 1000 else dateAdded
                                                if (dateMs > 0) {
                                                    detailsDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dateMs))
                                                }
                                            }
                                            val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                                            if (dataCol != -1) cursor.getString(dataCol)?.let { detailsPath = it }
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                        }

                        PlayerTopBar(
                            title = currentMediaTitle,
                            playbackSpeed = playbackSpeed,
                            showTopMenu = showTopMenu,
                            onShowTopMenuChange = { isOpen ->
                                showTopMenu = isOpen
                                if (isOpen) {
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                }
                            },
                            onNavigateBack = {
                                shura.media.LogKeeper.log("Back button clicked in PlayerScreen top bar", "PlayerScreen")
                                mediaController?.let { controller ->
                                    val pos = controller.currentPosition
                                    val dur = controller.duration
                                    if (pos > 0L) {
                                        shura.media.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
                                    }
                                }
                                if (!backgroundPlayEnabledRef.value && !forceBackgroundPlay.get()) {
                                    mediaController?.let { controller ->
                                        val hasError = controller.playerError != null
                                        controller.pause()
                                        controller.stop()
                                        if (!hasError) {
                                            controller.clearMediaItems()
                                        }
                                    }
                                    try {
                                        val stopIntent = android.content.Intent(context, shura.media.service.PlaybackService::class.java)
                                        context.stopService(stopIntent)
                                    } catch (e: Exception) {}
                                }
                                onNavigateBack()
                            },
                            onSpeedClick = {
                                val speedCycle = listOf(1.0f, 1.2f, 2.0f, 0.5f, 0.25f)
                                val currentIndex = speedCycle.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.05f }
                                val nextSpeed = if (currentIndex != -1) {
                                    speedCycle[(currentIndex + 1) % speedCycle.size]
                                } else {
                                    1.0f
                                }
                                playbackSpeed = nextSpeed
                                mediaController?.setPlaybackSpeed(nextSpeed)
                                settingsManager.savePlaybackSpeed(decodedUriString, nextSpeed)
                                shura.media.LogKeeper.log("Speed toggled to ${nextSpeed}x", "PlayerScreen")
                            },
                            onSpeedLongClick = {
                                shura.media.LogKeeper.log("Speed button long pressed - opening dialog", "PlayerScreen")
                                showSpeedDialog = true
                            },
                            onAudioClick = {
                                shura.media.LogKeeper.log("Audio tracks button clicked", "PlayerScreen")
                                showAudioDialog = true
                            },
                            onSubtitleClick = {
                                shura.media.LogKeeper.log("Subtitles button clicked", "PlayerScreen")
                                showSubtitleDialog = true
                            },
                            onDetailsClick = {
                                showDetailsDialog = true
                            },
                            onEditClick = {
                                val mimeType = context.contentResolver.getType(decodedUri)
                                val isAudio = mimeType?.startsWith("audio/") == true
                                val isVideo = mimeType?.startsWith("video/") == true

                                val route = if (isAudio) "audio_trimmer/$uriString"
                                else if (isVideo) "video_editor/$uriString"
                                else "photo_editor/$uriString"

                                onNavigateToEdit(route)
                            },
                            onPlayerSettingsClick = {
                                showPlayerSettingsDialog = true
                            },
                            onInteraction = {
                                controlsInteractionTrigger = System.currentTimeMillis()
                            }
                        )

                        PlayerLeftTools(
                            abRepeatStart = abRepeatStart,
                            abRepeatEnd = abRepeatEnd,
                            onAbRepeatClick = {
                                val currentPos = mediaController?.currentPosition ?: 0L
                                if (abRepeatStart == null) {
                                    abRepeatStart = currentPos
                                } else if (abRepeatEnd == null) {
                                    abRepeatEnd = currentPos
                                } else {
                                    abRepeatStart = null
                                    abRepeatEnd = null
                                }
                            },
                            isSleepTimerActive = sleepTimerEndTime != null,
                            onSleepTimerClick = {
                                showSleepTimerDialog = true
                            },
                            isBrightnessSliderVisible = showBrightnessSlider,
                            onBrightnessClick = {
                                showBrightnessSlider = !showBrightnessSlider
                                brightnessInteractionTime = System.currentTimeMillis()
                                controlsInteractionTrigger = System.currentTimeMillis()
                            },
                            onRotateClick = {
                                val currentOrientation = context.findActivity()?.requestedOrientation
                                if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            },
                            onScreenshotClick = {
                                val surfaceView = playerViewRef.value?.videoSurfaceView as? android.view.SurfaceView
                                if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    val bitmap = android.graphics.Bitmap.createBitmap(surfaceView.width, surfaceView.height, android.graphics.Bitmap.Config.ARGB_8888)
                                    android.view.PixelCopy.request(surfaceView, bitmap, { result ->
                                        if (result == android.view.PixelCopy.SUCCESS) {
                                            val filename = "Screenshot_${System.currentTimeMillis()}.png"
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
                                                }
                                            }
                                            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                            uri?.let {
                                                context.contentResolver.openOutputStream(it)?.use { out ->
                                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                                }
                                                Toast.makeText(context, "Screenshot saved to Photos", Toast.LENGTH_SHORT).show()
                                            }
                                            bitmap.recycle()
                                        } else {
                                            Toast.makeText(context, "Screenshot failed (PixelCopy error)", Toast.LENGTH_SHORT).show()
                                            bitmap.recycle()
                                        }
                                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                                } else {
                                    Toast.makeText(context, "Screenshot failed: Surface not ready or unsupported OS", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onInteraction = {
                                controlsInteractionTrigger = System.currentTimeMillis()
                            }
                        )
                    }
                }
            }
        }

        // Bottom Bar (contains lock button - stays permanently visible when locked, in exact same OG position)
        androidx.compose.animation.AnimatedVisibility(
            visible = (showControls || isLocked) && !isInPipMode,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val isPortrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                PlayerBottomBar(
                    mediaController = mediaController,
                    isPlaying = isPlaying,
                    isLocked = isLocked,
                    onLockClick = {
                        controlsInteractionTrigger = System.currentTimeMillis()
                        if (isLocked) {
                            isLocked = false
                            showControls = true
                        } else {
                            isLocked = true
                        }
                    },
                    abRepeatStart = abRepeatStart,
                    abRepeatEnd = abRepeatEnd,
                    repeatMode = repeatMode,
                    onRepeatModeClick = {
                        val nextMode = when (repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                        shura.media.LogKeeper.log("Repeat mode toggled from $repeatMode to $nextMode", "PlayerScreen")
                        repeatMode = nextMode
                        mediaController?.repeatMode = nextMode
                        mediaController?.pauseAtEndOfMediaItems = false
                    },
                    backgroundPlayEnabled = backgroundPlayEnabled,
                    onBackgroundPlayClick = {
                        backgroundPlayEnabled = !backgroundPlayEnabled
                        shura.media.LogKeeper.log("Background play toggled -> $backgroundPlayEnabled", "PlayerScreen")
                        Toast.makeText(context, "Background play " + if (backgroundPlayEnabled) "enabled" else "disabled", Toast.LENGTH_SHORT).show()
                    },
                    resizeMode = resizeMode,
                    onResizeModeClick = {
                        scale = 1.0f
                        offsetX = 0f
                        offsetY = 0f
                        rotationAngle = 0f
                        resizeMode = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 5
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onPreviousClick = {
                        shura.media.LogKeeper.log("Previous button clicked", "PlayerScreen")
                        mediaController?.let { controller ->
                            if (controller.hasPreviousMediaItem()) {
                                controller.seekToPreviousMediaItem()
                            } else {
                                controller.seekTo(0)
                            }
                        }
                    },
                    onPlayPauseClick = {
                        shura.media.LogKeeper.log("Play/Pause button clicked (currently isPlaying=$isPlaying)", "PlayerScreen")
                        mediaController?.let { controller ->
                            if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                controller.seekTo(0)
                                controller.prepare()
                                controller.play()
                                wasPlayingBeforePause = true
                            } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                controller.prepare()
                                controller.play()
                                wasPlayingBeforePause = true
                            } else if (controller.isPlaying) {
                                controller.pause()
                                wasPlayingBeforePause = false
                            } else {
                                controller.play()
                                wasPlayingBeforePause = true
                            }
                        }
                    },
                    onNextClick = {
                        shura.media.LogKeeper.log("Next button clicked", "PlayerScreen")
                        mediaController?.let { controller ->
                            if (controller.hasNextMediaItem()) {
                                controller.seekToNextMediaItem()
                            }
                        }
                    },
                    onStopClick = {
                        shura.media.LogKeeper.log("Stop button clicked in main player center controls", "PlayerScreen")
                        mediaController?.let { controller ->
                            val dur = controller.duration
                            val targetDur = if (dur > 0L) dur else controller.currentPosition.takeIf { it > 0L } ?: 1000L
                            shura.media.data.SettingsManager.getInstance(context).markAsFinished(
                                decodedUriString,
                                currentMediaTitle,
                                targetDur
                            )
                            controller.pause()
                            controller.stop()
                            controller.clearMediaItems()
                        }
                        shura.media.service.PlayerManager.release()
                        try {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                            nm?.cancelAll()
                        } catch (e: Exception) {}
                        onNavigateBack()
                        (context.findActivity() as? androidx.activity.ComponentActivity)?.finish()
                    },
                    onMiniPlayerClick = {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            val overlayIntent = android.content.Intent("shura.media.ACTION_WIDGET_COMMAND")
                            overlayIntent.putExtra("command", "ACTION_MINIPLAYER")
                            overlayIntent.setPackage(context.packageName)
                            context.sendBroadcast(overlayIntent)
                            forceBackgroundPlay.set(true)
                            backgroundPlayEnabled = true
                            onNavigateBack()
                        } else {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                    onFloatingPlayerClick = {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            shura.media.service.PlayerManager.isFloatingVideoActive = true
                            val overlayIntent = android.content.Intent("shura.media.ACTION_WIDGET_COMMAND")
                            overlayIntent.putExtra("command", "ACTION_VIDEO_OVERLAY")
                            overlayIntent.setPackage(context.packageName)
                            context.sendBroadcast(overlayIntent)
                            forceBackgroundPlay.set(true)
                            backgroundPlayEnabled = true
                            onNavigateBack()
                        } else {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                    onInteraction = {
                        controlsInteractionTrigger = System.currentTimeMillis()
                    },
                    onHeightMeasured = { h ->
                        bottomBarHeightPx = h
                    },
                    isPortrait = isPortrait,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showBrightnessSlider && !isInPipMode,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            BrightnessHudCapsule(
                currentBrightness = currentBrightness,
                onBrightnessChange = { newVal ->
                    currentBrightness = newVal
                    brightnessInteractionTime = System.currentTimeMillis()
                    controlsInteractionTrigger = System.currentTimeMillis()
                    val window = context.findActivity()?.window
                    if (window != null) {
                        val lp = window.attributes
                        if (kotlin.math.abs(newVal - lp.screenBrightness) >= 0.008f) {
                            lp.screenBrightness = newVal
                            window.attributes = lp
                        }
                    }
                },
                onDragStateChange = { isDragging ->
                    isAdjustingBrightness = isDragging
                    isInteractingWithControls = isDragging
                    brightnessInteractionTime = System.currentTimeMillis()
                    controlsInteractionTrigger = System.currentTimeMillis()
                    if (!isDragging) {
                        val window = context.findActivity()?.window
                        if (window != null) {
                            val lp = window.attributes
                            lp.screenBrightness = currentBrightness
                            window.attributes = lp
                        }
                    }
                },
                onTap = {
                    brightnessInteractionTime = System.currentTimeMillis()
                    controlsInteractionTrigger = System.currentTimeMillis()
                }
            )
        }
    }

    if (showDetailsDialog) {
        val duration = mediaController?.duration ?: 0L
        val durationStr = if (duration > 0) formatTime(duration) else "Unknown"
        MediaPropertiesDialog(
            name = detailsName,
            size = detailsSize,
            durationFormatted = durationStr,
            dateAdded = detailsDate,
            path = detailsPath,
            onDismissRequest = { showDetailsDialog = false }
        )
    }

    if (showAudioDialog) {
        val settings = remember { shura.media.data.SettingsManager.getInstance(context) }
        AudioTracksDialog(
            player = mediaController,
            audioBoosterEnabled = settings.audioBoosterEnabled,
            boostGainMb = boostGainMb,
            onBoostGainChange = { newGain ->
                boostGainMb = newGain
                settings.boostGainMb = newGain
                shura.media.service.PlayerManager.applyAudioBoosterSettings(settings.audioBoosterEnabled, newGain)
            },
            onTrackSelected = { groupIndex, trackIndex, totalIdx ->
                val currentTracks = mediaController?.currentTracks
                val audioGroups = currentTracks?.groups?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO } ?: emptyList()
                if (groupIndex < audioGroups.size) {
                    val group = audioGroups[groupIndex]
                    val builder = mediaController?.trackSelectionParameters?.buildUpon()
                    builder?.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    builder?.let { mediaController?.trackSelectionParameters = it.build() }
                    settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_AUDIO, totalIdx)
                }
                showAudioDialog = false
            },
            onAddAudioFile = {
                audioPickerLauncher.launch("audio/*")
                showAudioDialog = false
            },
            onDismissRequest = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleTracksDialog(
            player = mediaController,
            subtitleDelay = subtitleDelay,
            onSubtitleDelayChange = { subtitleDelay = it },
            subtitleSize = subtitleSize,
            onSubtitleSizeChange = { subtitleSize = it },
            subtitleColor = subtitleColor,
            onSubtitleColorChange = { subtitleColor = it },
            onTrackSelected = { groupIndex, trackIndex, totalIdx ->
                val currentTracks = mediaController?.currentTracks
                val textGroups = currentTracks?.groups?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT } ?: emptyList()
                if (groupIndex < textGroups.size) {
                    val group = textGroups[groupIndex]
                    val builder = mediaController?.trackSelectionParameters?.buildUpon()
                    builder?.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                    builder?.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    builder?.let { mediaController?.trackSelectionParameters = it.build() }
                    settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_TEXT, totalIdx)
                }
                showSubtitleDialog = false
            },
            onDisableSubtitles = {
                val builder = mediaController?.trackSelectionParameters?.buildUpon()
                builder?.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                builder?.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                builder?.let { mediaController?.trackSelectionParameters = it.build() }
                settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_TEXT, -1)
                showSubtitleDialog = false
            },
            onAddSubtitleFile = {
                subtitlePickerLauncher.launch("application/octet-stream")
                showSubtitleDialog = false
            },
            onDismissRequest = { showSubtitleDialog = false }
        )
    }

    if (showSpeedDialog) {
        SpeedControlsSheet(
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { newSpeed ->
                playbackSpeed = newSpeed
                mediaController?.setPlaybackSpeed(newSpeed)
                settingsManager.savePlaybackSpeed(decodedUriString, newSpeed)
            },
            skipSilence = skipSilence,
            onSkipSilenceChange = { newSkip ->
                skipSilence = newSkip
                mediaController?.skipSilenceEnabled = newSkip
            },
            onDismissRequest = { showSpeedDialog = false }
        )
    }

    if (showPlayerSettingsDialog) {
        PlayerSettingsDialog(onDismissRequest = { showPlayerSettingsDialog = false })
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            duration = mediaController?.duration ?: 0L,
            currentPosition = mediaController?.currentPosition ?: 0L,
            onSetTimerMs = { durationMs ->
                if (durationMs == null) {
                    sleepTimerEndTime = null
                } else {
                    sleepTimerEndTime = System.currentTimeMillis() + durationMs
                }
            },
            onDismissRequest = { showSleepTimerDialog = false }
        )
    }
}

private suspend fun resolveSiblingPlaylistItems(
    context: android.content.Context,
    videoUri: android.net.Uri,
    currentMediaId: String
): Pair<List<androidx.media3.common.MediaItem>, Int> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val result = mutableListOf<androidx.media3.common.MediaItem>()
    var currentIndex = -1

    try {
        if (videoUri.scheme == "file" || (videoUri.scheme == null && videoUri.path?.startsWith("/") == true)) {
            val file = java.io.File(videoUri.path ?: "")
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.isDirectory) {
                val files = parentDir.listFiles()?.filter { f ->
                    f.isFile && f.extension.lowercase(java.util.Locale.ROOT) in shura.media.data.COMMON_VIDEO_EXTENSIONS
                }?.sortedBy { it.name.lowercase(java.util.Locale.ROOT) } ?: emptyList()

                for ((idx, f) in files.withIndex()) {
                    val u = android.net.Uri.fromFile(f)
                    val idStr = u.toString()
                    if (f.absolutePath == file.absolutePath || idStr == currentMediaId) {
                        currentIndex = idx
                    }
                    val meta = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(f.nameWithoutExtension)
                        .setDisplayTitle(f.nameWithoutExtension)
                        .build()
                    result.add(
                        androidx.media3.common.MediaItem.Builder()
                            .setUri(u)
                            .setMediaId(idStr)
                            .setMediaMetadata(meta)
                            .build()
                    )
                }
            }
        } else if (videoUri.scheme == "content") {
            val auth = videoUri.authority ?: ""
            val isDocumentUri = auth.contains("documents") || auth.contains("externalstorage") || !auth.contains("media")
            var bucketId: String? = null
            if (!isDocumentUri) {
                try {
                    context.contentResolver.query(
                        videoUri,
                        arrayOf(
                            android.provider.MediaStore.Video.Media._ID,
                            android.provider.MediaStore.Video.Media.BUCKET_ID,
                            android.provider.MediaStore.Video.Media.DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val bIdx = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.BUCKET_ID)
                            if (bIdx != -1) bucketId = cursor.getString(bIdx)
                        }
                    }
                } catch (e: SecurityException) {
                    shura.media.LogKeeper.log("resolveSiblingPlaylistItems: Content query permission denied for $videoUri", "PlayerScreen")
                } catch (e: Exception) {}
            }

            if (!bucketId.isNullOrBlank()) {
                val projection = arrayOf(
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME
                )
                val selection = "${android.provider.MediaStore.Video.Media.BUCKET_ID} = ?"
                val selectionArgs = arrayOf(bucketId)
                val sortOrder = "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} ASC"

                context.contentResolver.query(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                    var indexCounter = 0
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Video"
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val idStr = contentUri.toString()
                        if (contentUri == videoUri || idStr == currentMediaId) {
                            currentIndex = indexCounter
                        }
                        val title = name.substringBeforeLast('.')
                        val meta = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title)
                            .setDisplayTitle(title)
                            .build()
                        result.add(
                            androidx.media3.common.MediaItem.Builder()
                                .setUri(contentUri)
                                .setMediaId(idStr)
                                .setMediaMetadata(meta)
                                .build()
                        )
                        indexCounter++
                    }
                }
            } else {
                // If not in MediaStore BUCKET_ID, fallback to in-memory cached folders ONLY (never full disk scan)
                val repository = shura.media.data.MediaRepository(context.applicationContext as android.app.Application)
                val cachedFolders = repository.getCachedMediaFolders()
                if (cachedFolders != null) {
                    for (folder in cachedFolders) {
                        val idx = folder.mediaItems.indexOfFirst {
                            it.uri == videoUri || it.uri.toString() == currentMediaId
                        }
                        if (idx != -1) {
                            currentIndex = idx
                            result.addAll(folder.mediaItems.map { item ->
                                val meta = androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(item.name)
                                    .setDisplayTitle(item.name)
                                    .build()
                                androidx.media3.common.MediaItem.Builder()
                                    .setUri(item.uri)
                                    .setMediaId(item.uri.toString())
                                    .setMediaMetadata(meta)
                                    .build()
                            })
                            break
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        shura.media.LogKeeper.log("PlayerScreen: Failed to resolve sibling playlist items: ${e.message}", "PlayerScreen")
    }

    Pair(result, currentIndex)
}


