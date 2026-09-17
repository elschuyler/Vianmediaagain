@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.screens
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import com.example.service.PlaybackService
import kotlinx.coroutines.delay

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

fun getDisplayNameFromUri(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayIndex != -1) {
                        val name = cursor.getString(displayIndex)
                        if (!name.isNullOrBlank()) return name.substringBeforeLast('.')
                    }
                    val titleIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.TITLE)
                    if (titleIndex != -1) {
                        val title = cursor.getString(titleIndex)
                        if (!title.isNullOrBlank()) return title
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    return uri.lastPathSegment?.substringBeforeLast('.') ?: "Unknown"
}

@Composable
fun CompactPlayerDialog(
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 380.dp, max = 440.dp).padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 350.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(12.dp)
            ) {
                content()
            }
        }
    }
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
    var mediaController by remember { mutableStateOf(com.example.service.PlayerManager.exoPlayer) }
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
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var brightnessInteractionTime by remember { mutableLongStateOf(0L) }
    var currentBrightness by remember { mutableFloatStateOf(context.findActivity()?.window?.attributes?.screenBrightness.takeIf { it != -1f } ?: 0.5f) }
    var boostGainMb by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Auto-hide brightness slider after inactivity
    LaunchedEffect(showBrightnessSlider, brightnessInteractionTime) {
        if (showBrightnessSlider) {
            kotlinx.coroutines.delay(3000)
            showBrightnessSlider = false
        }
    }

    var activeGesture by remember { mutableStateOf(GestureType.NONE) }
    var gestureText by remember { mutableStateOf("") }
    var gestureVolumeRatio by remember { mutableFloatStateOf(0f) }
    var seekOffsetSec by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showPlayPauseFlash by remember { mutableStateOf(false) }
    var flashIsPlaying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var pendingSingleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
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
    var currentMediaTitle by remember { mutableStateOf(getDisplayNameFromUri(context, decodedUri)) }
    var currentMediaUri by remember { mutableStateOf(decodedUri) }

    var isPlaying by remember { mutableStateOf(false) }
    
    var abRepeatStart by remember { mutableStateOf<Long?>(null) }
    var abRepeatEnd by remember { mutableStateOf<Long?>(null) }
    var sleepTimerEndTime by remember { mutableStateOf<Long?>(null) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var lastKnownIsPortrait by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    var wasPlayingBeforePause by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }
    val backgroundPlayEnabledRef = androidx.compose.runtime.rememberUpdatedState(backgroundPlayEnabled)
    val forceBackgroundPlay = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    var decoderRetryCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    androidx.activity.compose.BackHandler {
        com.example.LogKeeper.log("BackHandler triggered in PlayerScreen (bgPlay=${backgroundPlayEnabledRef.value})", "PlayerScreen")
        try {
            context.findActivity()?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } catch (e: Exception) {}
        mediaController?.let { controller ->
            val pos = controller.currentPosition
            val dur = controller.duration
            if (pos > 0L) {
                com.example.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
            }
        }
        if (!backgroundPlayEnabledRef.value && !forceBackgroundPlay.get()) {
            mediaController?.let { controller ->
                try { controller.pause() } catch (e: Exception) {}
                try { controller.stop() } catch (e: Exception) {}
            }
            try { playerViewRef.value?.player = null } catch (e: Exception) {}
            mediaController?.let { controller ->
                try { controller.clearVideoSurface() } catch (e: Exception) {}
                try { controller.clearMediaItems() } catch (e: Exception) {}
            }
            try {
                val stopIntent = android.content.Intent(context, com.example.service.PlaybackService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {}
        }
        onNavigateBack()
    }

    LaunchedEffect(mediaController) {
        var lastSavedTime = 0L
        while (true) {
            val actualIsPlaying = mediaController?.isPlaying == true
            if (isPlaying != actualIsPlaying) {
                com.example.LogKeeper.log("[UI_MISMATCH_DETECTED] UI isPlaying=$isPlaying, ExoPlayer isPlaying=$actualIsPlaying. Resyncing.", "PlayerScreen")
                isPlaying = actualIsPlaying
            }
            
            // A-B repeat check
            if (abRepeatStart != null && abRepeatEnd != null && isPlaying) {
                val currentPos = mediaController?.currentPosition ?: 0L
                if (currentPos >= abRepeatEnd!!) {
                    com.example.LogKeeper.log("A-B repeat triggered: seeking back to $abRepeatStart", "PlayerScreen")
                    mediaController?.seekTo(abRepeatStart!!)
                }
            }
            
            // Sleep timer check
            if (sleepTimerEndTime != null && isPlaying) {
                if (System.currentTimeMillis() >= sleepTimerEndTime!!) {
                    com.example.LogKeeper.log("Sleep timer reached: pausing playback", "PlayerScreen")
                    mediaController?.pause()
                    sleepTimerEndTime = null
                }
            }

            // Periodic progress save
            if (isPlaying) {
                val now = System.currentTimeMillis()
                if (now - lastSavedTime >= 3000L) {
                    mediaController?.let { controller ->
                        val pos = controller.currentPosition
                        val dur = controller.duration
                        if (pos > 0L) {
                            com.example.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
                            lastSavedTime = now
                        }
                    }
                }
            }
            
            kotlinx.coroutines.delay(300)
        }
    }
    val settingsManager = com.example.data.SettingsManager.getInstance(context)
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
                com.example.service.PlayerManager.addSubtitle(uri.toString())
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
            it.pauseAtEndOfMediaItems = (it.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)
        }
    }

    LaunchedEffect(showControls, controlsInteractionTrigger) {
        if (!showControls) {
            showBrightnessSlider = false
        } else {
            kotlinx.coroutines.delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(decodedUri) {
        val mimeType = context.contentResolver.getType(decodedUri)
        val isAudio = mimeType?.startsWith("audio/") == true
        if (isAudio) {
            val settingsManager = com.example.data.SettingsManager.getInstance(context)
            backgroundPlayEnabled = settingsManager.defaultAudioBackgroundPlay
        }
    }


    LaunchedEffect(uriString, mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        val settingsManager = com.example.data.SettingsManager.getInstance(context)
        
        if (controller.currentMediaItem?.mediaId != decodedUri.toString()) {
            val mediaMetadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            var fileName = decodedUri.lastPathSegment ?: "Unknown"
            if (decodedUri.scheme == "file") {
                try { fileName = java.io.File(decodedUri.path!!).name } catch (e: Exception) {}
            } else if (decodedUri.scheme == "content") {
                try {
                    context.contentResolver.query(decodedUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                            if (nameCol != -1) cursor.getString(nameCol)?.let { fileName = it }
                        }
                    }
                } catch (e: Exception) {}
            }
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
            
            settingsManager.markAsOpened(decodedUriString, fileName)
            
            val lastPos = settingsManager.getPlaybackPosition(decodedUriString, fileName)
            if (lastPos > 0 && !settingsManager.isFinished(decodedUriString, fileName)) {
                controller.seekTo(lastPos)
            }
            controller.play()
            
            // Load playlist in background with resource awareness
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                // Yield storage I/O and CPU to initial video buffering and decoder setup
                kotlinx.coroutines.delay(1000)
                
                // Do not perform heavy device-wide library scan if background transcoding/compression is running
                if (com.example.service.FFmpegStatus.isRunning || com.example.service.CompressionStatus.isRunning) {
                    com.example.LogKeeper.log("PlayerScreen: Deferred background playlist scan because FFmpeg/Compression service is running", "PlayerScreen")
                    return@launch
                }

                val repository = com.example.data.MediaRepository(context.applicationContext as android.app.Application)
                val folders = repository.getMediaFolders()
                var playlistItems = emptyList<MediaItem>()
                var startIndex = 0
                var found = false
                
                for (folder in folders) {
                    val index = folder.mediaItems.indexOfFirst { it.uri == decodedUri }
                    if (index != -1) {
                        playlistItems = folder.mediaItems.map { item ->
                            val meta = androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(item.name)
                                .setDisplayTitle(item.name)
                                .setArtworkUri(item.uri)
                                .build()
                            MediaItem.Builder()
                                .setUri(item.uri)
                                .setMediaId(item.uri.toString())
                                .setMediaMetadata(meta)
                                .build()
                        }
                        startIndex = index
                        found = true
                        break
                    }
                }
                
                if (found && playlistItems.size > 1) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (controller.currentMediaItem?.mediaId == decodedUri.toString()) {
                            val currentPos = controller.currentPosition
                            val isPlaying = controller.isPlaying
                            controller.setMediaItems(playlistItems, startIndex, currentPos)
                            if (isPlaying) {
                                controller.play()
                            }
                        }
                    }
                    
                    // Generate Temp Current playlist
                    try {
                        val db = com.example.data.AppDatabase.getDatabase(context)
                        val playlistRepo = com.example.data.PlaylistRepository(db.playlistDao())
                        val uris = playlistItems.map { it.mediaId }
                        playlistRepo.saveOrUpdateTemporaryPlaylist(uris)
                    } catch(e: Exception) {
                        e.printStackTrace()
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
        val settingsManager = com.example.data.SettingsManager.getInstance(context)
        com.example.LogKeeper.log("Starting player for $decodedUri", "PlayerScreen")
        
        // Ensure player is initialized
        com.example.service.PlayerManager.initialize(context, false)
        mediaController = com.example.service.PlayerManager.exoPlayer
        
        // Start the service for MediaSession features
        val intent = android.content.Intent(context, com.example.service.PlaybackService::class.java).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            data = decodedUri
        }
        try {
            context.startService(intent)
        } catch(e: Exception) {}

        val controller = mediaController ?: return@DisposableEffect onDispose {}
        
        // Lightweight check for dimensions on start
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                val u = android.net.Uri.parse(decodedUriString)
                var set = false
                try {
                    retriever.setDataSource(context, u)
                    set = true
                } catch (e1: Exception) {
                    try {
                        context.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                            set = true
                        }
                    } catch (e2: Exception) {}
                }
                if (set) {
                    val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    retriever.release()
                    if (widthStr != null && heightStr != null) {
                        var w = widthStr.toIntOrNull() ?: 0
                        var h = heightStr.toIntOrNull() ?: 0
                        val rot = rotationStr?.toIntOrNull() ?: 0
                        if (rot == 90 || rot == 270) {
                            val temp = w
                            w = h
                            h = temp
                        }
                        if (w > 0 && h > 0) {
                            val isVideoPortrait = h > w
                            lastKnownIsPortrait = isVideoPortrait
                            com.example.LogKeeper.log("Lightweight check: rot=$rot w=$w h=$h isPortrait=$isVideoPortrait", "PlayerScreen")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                context.findActivity()?.requestedOrientation = if (isVideoPortrait) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                com.example.LogKeeper.logError("PlayerScreen", "Error in lightweight dimension check", e)
            }
        }
        
        val savedGain = settingsManager.boostGainMb
        boostGainMb = savedGain
        if (savedGain > 0) {
            com.example.service.PlayerManager.applyAudioBoosterSettings(settingsManager.audioBoosterEnabled, savedGain)
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
                com.example.LogKeeper.log("updateOrientation called with videoSize: ${videoSize.width}x${videoSize.height} rot=${videoSize.unappliedRotationDegrees}. isPortrait=$isPortrait", "PlayerScreen")
                act.requestedOrientation = if (isPortrait) {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }

        // updateOrientation(controller.videoSize) removed to prevent old video size from forcing incorrect orientation
        val mainListener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                com.example.LogKeeper.log("PlayerScreen: onIsPlayingChanged = $playing", "PlayerScreen")
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val reasonStr = when (reason) {
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN($reason)"
                }
                val uri = mediaItem?.localConfiguration?.uri ?: mediaItem?.mediaId?.let { Uri.parse(it) }
                val title = mediaItem?.mediaMetadata?.title?.toString()
                com.example.LogKeeper.log("PlayerScreen: onMediaItemTransition to '$title' / $uri (reason: $reasonStr)", "PlayerScreen")
                if (uri != null) {
                    currentMediaUri = uri
                    currentMediaTitle = title ?: getDisplayNameFromUri(context, uri)
                    com.example.data.SettingsManager.getInstance(context).markAsOpened(uri.toString(), currentMediaTitle)
                }

                // If transition happened automatically and repeat mode is OFF, do not auto-play next video
                if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val currentMode = controller.repeatMode
                    if (currentMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        com.example.LogKeeper.log("Auto-transition occurred with repeatMode OFF -> pausing playback and navigating back", "PlayerScreen")
                        controller.pause()
                        onNavigateBack()
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                com.example.LogKeeper.log("PlayerScreen: onTracksChanged", "PlayerScreen")
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
                com.example.LogKeeper.log("PlayerScreen: PlaybackState changed to: $stateName", "PlayerScreen")
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val currentMode = controller.repeatMode
                    val hasNext = controller.hasNextMediaItem()
                    if (currentMode == androidx.media3.common.Player.REPEAT_MODE_OFF && !hasNext) {
                        com.example.LogKeeper.log("Player reached STATE_ENDED with no loop/next -> navigating back", "PlayerScreen")
                        onNavigateBack()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                com.example.LogKeeper.log("PlayerScreen: PositionDiscontinuity from ${oldPosition.positionMs}ms to ${newPosition.positionMs}ms (reason: $reason)", "PlayerScreen")
            }

            override fun onRepeatModeChanged(mode: Int) {
                com.example.LogKeeper.log("PlayerScreen: onRepeatModeChanged = $mode", "PlayerScreen")
                repeatMode = mode
                controller.pauseAtEndOfMediaItems = (mode == androidx.media3.common.Player.REPEAT_MODE_OFF)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                com.example.LogKeeper.log("PlayerScreen: onPlayWhenReadyChanged = $playWhenReady, reason = $reason", "PlayerScreen")
                if (reason == androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    if (controller.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        com.example.LogKeeper.log("End of media item reached with repeatMode OFF -> navigating back", "PlayerScreen")
                        onNavigateBack()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                com.example.LogKeeper.logError("PlayerScreen", "ExoPlayer Error: ${error.message}", error)
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT) {
                    com.example.LogKeeper.log("PlayerScreen: Timeout caught during surface detachment or release - ignored gracefully", "PlayerScreen")
                    return
                }
                val currentPos = controller.currentPosition
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                    if (decoderRetryCount < 2) {
                        decoderRetryCount++
                        com.example.LogKeeper.log("Decoder error encountered, attempting auto-retry $decoderRetryCount with fallback...", "PlayerScreen")
                        controller.prepare()
                        if (currentPos > 0) {
                            controller.seekTo(currentPos)
                        }
                        controller.play()
                    } else {
                        com.example.LogKeeper.log("Decoder error exceeded max retries. Pausing playback to avoid crash loop.", "PlayerScreen")
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
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
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
            com.example.LogKeeper.log("LifecycleEventObserver received: $event", "PlayerScreen")
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                currentController?.let { controller ->
                    val currentPos = controller.currentPosition
                    val dur = controller.duration
                    if (currentPos > 0L) {
                        com.example.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, currentPos, dur, currentMediaTitle)
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
                            com.example.LogKeeper.log("PlayerScreen ON_RESUME: Controller is in STATE_IDLE for current item, calling prepare()", "PlayerScreen")
                            controller.prepare()
                        }
                        if (wasPlayingBeforePause) {
                            com.example.LogKeeper.log("PlayerScreen ON_RESUME: Resuming playback (wasPlayingBeforePause=true)", "PlayerScreen")
                            controller.play()
                        }
                    } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE || controller.mediaItemCount == 0 || controller.currentMediaItem == null) {
                        com.example.LogKeeper.log("PlayerScreen ON_RESUME: Controller is in STATE_IDLE or empty, re-preparing media item for $decodedUriString", "PlayerScreen")
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

                        val savedPos = com.example.data.SettingsManager.getInstance(context).getPlaybackPosition(decodedUriString, fileName)
                        if (savedPos > 0) {
                            controller.seekTo(savedPos)
                        }
                        if (wasPlayingBeforePause) {
                            controller.play()
                        }
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
            currentController?.let { controller ->
                val currentPos = controller.currentPosition
                val dur = controller.duration
                if (currentPos > 0L) {
                    com.example.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, currentPos, dur, currentMediaTitle)
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
        .pointerInput(isLocked, isInPipMode, showBrightnessSlider) {
            if (isLocked || isInPipMode) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var currentGesture = GestureType.NONE
                var dragDistanceX = 0f
                var dragDistanceY = 0f
                var lastSeekTime = 0L
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val startVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val settings = com.example.data.SettingsManager.getInstance(context)
                val boosterEnabled = settings.audioBoosterEnabled
                val effectiveBoost = if (boosterEnabled) boostGainMb else 0
                val startVolumeRatio = if (maxVolume > 0) {
                    if (effectiveBoost > 0) {
                        0.5f + (effectiveBoost.toFloat() / 1500f) * 0.5f
                    } else {
                        (startVolume.toFloat() / maxVolume.toFloat()) * 0.5f
                    }
                } else 0f
                var lastAppliedSystemVolume = startVolume
                var lastAppliedBoost = effectiveBoost
                
                val window = context.findActivity()?.window
                var startBrightness = window?.attributes?.screenBrightness ?: -1f
                if (startBrightness < 0) startBrightness = 0.5f
                
                val startPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                var wasPlayingBeforeSeek = false

                do {
                    val event = awaitPointerEvent()
                    val changes = event.changes
                    
                    if (changes.size > 1) {
                        currentGesture = GestureType.ZOOM_PAN
                        activeGesture = GestureType.ZOOM_PAN
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        
                        scale = (scale * zoomChange).coerceIn(0.25f, 4f)
                        offsetX += panChange.x
                        offsetY += panChange.y
                        
                        changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (currentGesture != GestureType.ZOOM_PAN) {
                        val change = changes.firstOrNull()
                        if (change != null) {
                            val posChange = change.positionChange()
                            dragDistanceX += posChange.x
                            dragDistanceY += posChange.y
                            
                            if (currentGesture == GestureType.NONE) {
                                if (kotlin.math.abs(dragDistanceX) > 20f || kotlin.math.abs(dragDistanceY) > 20f) {
                                    if (kotlin.math.abs(dragDistanceX) > kotlin.math.abs(dragDistanceY)) {
                                        currentGesture = GestureType.SEEK
                                        wasPlayingBeforeSeek = mediaController?.isPlaying == true
                                        mediaController?.pause()
                                    } else {
                                        currentGesture = if (showBrightnessSlider) GestureType.BRIGHTNESS else GestureType.VOLUME
                                    }
                                    activeGesture = currentGesture
                                }
                            }
                            
                            when (currentGesture) {
                                GestureType.SEEK -> {
                                    val currentDuration = mediaController?.duration?.coerceAtLeast(1L) ?: 1L
                                    val seekOffsetMs = (dragDistanceX / size.width) * 120_000
                                    val targetPos = (startPosition + seekOffsetMs.toLong()).coerceIn(0L, currentDuration)
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastSeekTime > 300) {
                                        mediaController?.seekTo(targetPos)
                                        lastSeekTime = currentTime
                                    }
                                    seekOffsetSec = ((dragDistanceX / size.width) * 120).toInt()
                                    change.consume()
                                }
                                GestureType.BRIGHTNESS -> {
                                    val brightnessChange = -(dragDistanceY / size.height)
                                    val newBrightness = (startBrightness + brightnessChange).coerceIn(0f, 1f)
                                    
                                    if (kotlin.math.abs(newBrightness - currentBrightness) > 0.01f) {
                                        currentBrightness = newBrightness
                                        val window = context.findActivity()?.window
                                        if (window != null) {
                                            val layoutParams = window.attributes
                                            layoutParams.screenBrightness = newBrightness
                                            window.attributes = layoutParams
                                        }
                                        brightnessInteractionTime = System.currentTimeMillis()
                                    }
                                    
                                    gestureVolumeRatio = newBrightness
                                    gestureText = "Brightness: ${(newBrightness * 100).roundToInt()}%"
                                    change.consume()
                                }
                                GestureType.VOLUME -> {
                                    val volumeScale = if (boosterEnabled) 1.0f else 0.5f
                                    val volumeChange = -(dragDistanceY / size.height) * volumeScale
                                    val currentRatio = (startVolumeRatio + volumeChange).coerceIn(0f, volumeScale)
                                    
                                    val targetSystemVolume = if (currentRatio <= 0.5f) {
                                        if (maxVolume > 0) ((currentRatio / 0.5f) * maxVolume).roundToInt().coerceIn(0, maxVolume) else 0
                                    } else {
                                        maxVolume
                                    }
                                    val targetBoost = if (boosterEnabled && currentRatio > 0.5f) {
                                        (((currentRatio - 0.5f) / 0.5f) * 1500f).roundToInt().coerceIn(0, 1500)
                                    } else {
                                        0
                                    }
                                    
                                    if (targetSystemVolume != lastAppliedSystemVolume) {
                                        lastAppliedSystemVolume = targetSystemVolume
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetSystemVolume, 0)
                                    }
                                    if (targetBoost != lastAppliedBoost) {
                                        lastAppliedBoost = targetBoost
                                        boostGainMb = targetBoost
                                        settings.boostGainMb = targetBoost
                                        com.example.service.PlayerManager.applyAudioBoosterSettings(settings.audioBoosterEnabled, targetBoost)
                                    }
                                    
                                    gestureVolumeRatio = currentRatio
                                    gestureText = "Volume: ${(currentRatio * 200).roundToInt()}"
                                    change.consume()
                                }
                                else -> {}
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
                
                if (currentGesture == GestureType.SEEK) {
                    val currentDuration = mediaController?.duration?.coerceAtLeast(1L) ?: 1L
                    val seekOffsetMs = (dragDistanceX / size.width) * 120_000
                    val targetPos = (startPosition + seekOffsetMs.toLong()).coerceIn(0L, currentDuration)
                    mediaController?.seekTo(targetPos)
                    if (wasPlayingBeforeSeek) {
                        mediaController?.play()
                    }
                } else if (currentGesture == GestureType.NONE && kotlin.math.abs(dragDistanceX) <= 20f && kotlin.math.abs(dragDistanceY) <= 20f) {
                    // Tap handling
                    if (showBrightnessSlider) {
                        pendingSingleTapJob?.cancel()
                        showBrightnessSlider = false
                        lastTapTime = 0L
                    } else {
                        val now = System.currentTimeMillis()
                        val tapPos = down.position
                        val timeDiff = now - lastTapTime
                        val dist = (tapPos - lastTapPosition).getDistance()
                        
                        if (timeDiff in 40L..400L && dist < 120f * density) {
                            // Double tap: Only pause (play/pause toggle)
                            pendingSingleTapJob?.cancel()
                            lastTapTime = 0L
                            
                            mediaController?.let { controller ->
                                if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                    controller.seekTo(0)
                                    controller.prepare()
                                    controller.play()
                                    wasPlayingBeforePause = true
                                    showControls = false
                                } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                    controller.prepare()
                                    controller.play()
                                    wasPlayingBeforePause = true
                                    showControls = false
                                } else if (controller.isPlaying) {
                                    controller.pause()
                                    wasPlayingBeforePause = false
                                    showControls = true
                                } else {
                                    controller.play()
                                    wasPlayingBeforePause = true
                                    showControls = false
                                }
                                flashIsPlaying = controller.isPlaying
                                showPlayPauseFlash = true
                            }
                        } else {
                            // First tap: record and schedule single tap action
                            lastTapTime = now
                            lastTapPosition = tapPos
                            pendingSingleTapJob?.cancel()
                            pendingSingleTapJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(260L)
                                if (showBrightnessSlider) {
                                    showBrightnessSlider = false
                                } else {
                                    showControls = !showControls
                                }
                            }
                        }
                    }
                }
                
                activeGesture = GestureType.NONE
                gestureText = ""
            }
        }
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
                    val colorInt = android.graphics.Color.argb(
                        (subtitleColor.alpha * 255).toInt(),
                        (subtitleColor.red * 255).toInt(),
                        (subtitleColor.green * 255).toInt(),
                        (subtitleColor.blue * 255).toInt()
                    )
                    val style = androidx.media3.ui.CaptionStyleCompat(
                        colorInt,
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        null
                    )
                    subtitleView.setStyle(style)
                    subtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSize)
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
            }
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = showPlayPauseFlash,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(100)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (flashIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(72.dp)
            )
        }

        if (activeGesture != GestureType.NONE && !isInPipMode) {
            if (activeGesture == GestureType.VOLUME) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .height(170.dp)
                            .width(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "${(gestureVolumeRatio * 200).roundToInt()}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(4.dp)
                                .background(Color.DarkGray.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight((gestureVolumeRatio * 2f).coerceIn(0f, 1f))
                                    .width(4.dp)
                                    .background(Color(0xFF2196F3), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            )
                            val whiteRatio = ((gestureVolumeRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                            if (whiteRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(whiteRatio)
                                        .width(4.dp)
                                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Icon(androidx.compose.material.icons.Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else if (activeGesture == GestureType.SEEK) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val sign = if (seekOffsetSec >= 0) "+" else ""
                    Text(
                        text = "$sign${seekOffsetSec}s",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(16.dp)
                    )
                }
            } else if (gestureText.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = gestureText,
                        color = Color.White,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(16.dp)
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !showControls && !isInPipMode && !isLocked,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
            ) {
                var timeStr by remember { mutableStateOf("") }
                var batteryPct by remember { mutableStateOf(100) }
                var isCharging by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                    while (true) {
                        timeStr = sdf.format(java.util.Date()).lowercase(java.util.Locale.US)
                        kotlinx.coroutines.delay(1000)
                    }
                }
                
                DisposableEffect(Unit) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                            batteryPct = if (scale > 0) (level * 100) / scale else 100
                        }
                    }
                    val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                    context.registerReceiver(receiver, filter)
                    onDispose {
                        context.unregisterReceiver(receiver)
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 0.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                        contentDescription = if (isCharging) "Charging" else "Battery",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$batteryPct%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top controls container (traps taps so controls remain visible)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures {
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
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility.union(androidx.compose.foundation.layout.WindowInsets.displayCutout).only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Top))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            IconButton(onClick = {
                                com.example.LogKeeper.log("Back button clicked in PlayerScreen top bar", "PlayerScreen")
                                mediaController?.let { controller ->
                                    val pos = controller.currentPosition
                                    val dur = controller.duration
                                    if (pos > 0L) {
                                        com.example.data.SettingsManager.getInstance(context).savePlaybackState(decodedUriString, pos, dur, currentMediaTitle)
                                    }
                                }
                                if (!backgroundPlayEnabledRef.value && !forceBackgroundPlay.get()) {
                                    mediaController?.let { controller ->
                                        controller.pause()
                                        controller.stop()
                                        controller.clearMediaItems()
                                    }
                                    try {
                                        val stopIntent = android.content.Intent(context, com.example.service.PlaybackService::class.java)
                                        context.stopService(stopIntent)
                                    } catch (e: Exception) {}
                                }
                                onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(
                                text = currentMediaTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pointerInput(playbackSpeed) {
                                        detectTapGestures(
                                            onLongPress = {
                                                com.example.LogKeeper.log("Speed button long pressed - opening dialog", "PlayerScreen")
                                                showSpeedDialog = true
                                            },
                                            onTap = {
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
                                                com.example.LogKeeper.log("Speed toggled to ${nextSpeed}x", "PlayerScreen")
                                                controlsInteractionTrigger = System.currentTimeMillis()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (playbackSpeed == 1.0f) {
                                    Icon(Icons.Filled.Speed, contentDescription = "Speed: 1x (Long press for controls)", tint = Color.White)
                                } else {
                                    val speedDisplay = if (playbackSpeed == 0.25f) "0.25"
                                        else if (playbackSpeed == 0.5f) "0.5"
                                        else if (playbackSpeed == 1.2f) "1.2"
                                        else if (playbackSpeed == 2.0f) "2"
                                        else String.format("%.1f", playbackSpeed)
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF2196F3).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${speedDisplay}x",
                                            color = Color(0xFF2196F3),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { 
                                com.example.LogKeeper.log("Audio tracks button clicked", "PlayerScreen")
                                showAudioDialog = true 
                            }) {
                                Icon(Icons.Filled.MusicNote, contentDescription = "Audio track", tint = Color.White)
                            }
                            IconButton(onClick = { 
                                com.example.LogKeeper.log("Subtitles button clicked", "PlayerScreen")
                                showSubtitleDialog = true 
                            }) {
                                Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                            }
                            Box {

                                
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

                                IconButton(onClick = { showTopMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Details") },
                                        onClick = { 
                                            showTopMenu = false
                                            showDetailsDialog = true 
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            showTopMenu = false
                                            val mimeType = context.contentResolver.getType(decodedUri)
                                            val isAudio = mimeType?.startsWith("audio/") == true
                                            val isVideo = mimeType?.startsWith("video/") == true
                                            val isAnimatedImage = mimeType == "image/gif" || mimeType == "image/webp"
                                            val isImage = mimeType?.startsWith("image/") == true

                                            val route = if (isAudio) "audio_trimmer/$uriString"
                                            else if (isVideo) "video_editor/$uriString"
                                            else if (isImage && !isAnimatedImage) "photo_editor/$uriString"
                                            else "video_editor/$uriString"
                                            
                                            onNavigateToEdit(route)
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Player settings") },
                                        onClick = {
                                            showTopMenu = false
                                            showPlayerSettingsDialog = true
                                        }
                                    )
                                }
                                
                                
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            // A-B Repeat Icon
                            IconButton(onClick = {
                                val currentPos = mediaController?.currentPosition ?: 0L
                                if (abRepeatStart == null) {
                                    abRepeatStart = currentPos
                                } else if (abRepeatEnd == null) {
                                    abRepeatEnd = currentPos
                                } else {
                                    abRepeatStart = null
                                    abRepeatEnd = null
                                }
                            }) {
                                val tint = if (abRepeatStart != null) Color(0xFF2196F3) else Color.White
                                val text = if (abRepeatStart != null && abRepeatEnd == null) "A" else if (abRepeatEnd != null) "A-B" else ""
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Loop, contentDescription = "A-B Repeat", tint = tint)
                                    if (text.isNotEmpty()) {
                                        Text(
                                            text = text,
                                            color = tint,
                                            fontSize = 8.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        )
                                    }
                                }
                            }
                            
                            // Sleep Timer Icon
                            IconButton(onClick = { showSleepTimerDialog = true }) {
                                Icon(Icons.Filled.Timer, contentDescription = "Sleep Timer", tint = if (sleepTimerEndTime != null) Color(0xFF2196F3) else Color.White)
                            }
                            IconButton(onClick = { 
                                showBrightnessSlider = !showBrightnessSlider 
                                if (showBrightnessSlider) {
                                    brightnessInteractionTime = System.currentTimeMillis()
                                }
                            }) {
                                Icon(Icons.Filled.LightMode, contentDescription = "Brightness", tint = Color.White)
                            }
                            IconButton(onClick = {
                                val currentOrientation = context.findActivity()?.requestedOrientation
                                if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }) {
                                Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotation", tint = Color.White)
                            }
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Filled.Screenshot, contentDescription = "Screenshot", tint = Color.White)
                            }
                        }
                    }
                }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showBrightnessSlider,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
                    ) {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .height(170.dp)
                                .width(56.dp)
                                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "${(currentBrightness.coerceIn(0f, 1f) * 100).roundToInt()}%",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .width(56.dp)
                                    .pointerInput(Unit) {
                                        var lastAppliedBrightness = -1f
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val dragRatio = -dragAmount / 100.dp.toPx()
                                                val newVal = (currentBrightness + dragRatio).coerceIn(0f, 1f)
                                                brightnessInteractionTime = System.currentTimeMillis()
                                                currentBrightness = newVal
                                                
                                                if (kotlin.math.abs(newVal - lastAppliedBrightness) > 0.02f) {
                                                    lastAppliedBrightness = newVal
                                                    val window = context.findActivity()?.window
                                                    window?.let {
                                                        val lp = it.attributes
                                                        lp.screenBrightness = newVal
                                                        it.attributes = lp
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(4.dp)
                                        .background(Color.DarkGray.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight(currentBrightness.coerceIn(0f, 1f))
                                            .width(4.dp)
                                            .background(Color(0xFF2196F3), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Icon(Icons.Filled.LightMode, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Bottom controls container (traps taps so controls remain visible)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    controlsInteractionTrigger = System.currentTimeMillis()
                                }
                            }
                    ) {
                        // Bottom controls background
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                        )

                    // Bottom controls overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility.union(androidx.compose.foundation.layout.WindowInsets.displayCutout).only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal + androidx.compose.foundation.layout.WindowInsetsSides.Bottom))
                            .padding(bottom = 4.dp)
                    ) {
                        com.example.ui.screens.PlaybackProgressRow(
                            mediaController = mediaController,
                            abRepeatStart = abRepeatStart,
                            abRepeatEnd = abRepeatEnd,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                        
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            // Left alignment
                            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                                IconButton(onClick = { isLocked = true }) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White)
                                }
                            }
                            
                            // Center alignment
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                            
                                IconButton(
                                    onClick = {
                                        com.example.LogKeeper.log("Previous button clicked", "PlayerScreen")
                                        mediaController?.let { controller ->
                                            if (controller.hasPreviousMediaItem()) {
                                                controller.seekToPreviousMediaItem()
                                            } else {
                                                controller.seekTo(0)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        com.example.LogKeeper.log("Play/Pause button clicked (currently isPlaying=$isPlaying)", "PlayerScreen")
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
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        com.example.LogKeeper.log("Next button clicked", "PlayerScreen")
                                        mediaController?.let { controller ->
                                            if (controller.hasNextMediaItem()) {
                                                controller.seekToNextMediaItem()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                            }
                            
                            // Right alignment
                            val isPortrait = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                            var showToolsStack by remember { mutableStateOf(false) }

                            val RightTools: @Composable () -> Unit = {
                                IconButton(modifier = Modifier.size(36.dp), onClick = {
                                    val nextMode = when (repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                    }
                                    com.example.LogKeeper.log("Repeat mode toggled from $repeatMode to $nextMode", "PlayerScreen")
                                    repeatMode = nextMode
                                    mediaController?.repeatMode = nextMode
                                }) {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) androidx.compose.material.icons.Icons.Filled.RepeatOne else androidx.compose.material.icons.Icons.Filled.Repeat,
                                        contentDescription = "Repeat",
                                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) androidx.compose.ui.graphics.Color(0xFF2196F3) else androidx.compose.ui.graphics.Color.White
                                    )
                                }
                                IconButton(modifier = Modifier.size(36.dp), onClick = { 
                                    backgroundPlayEnabled = !backgroundPlayEnabled
                                    com.example.LogKeeper.log("Background play toggled -> $backgroundPlayEnabled", "PlayerScreen")
                                    Toast.makeText(context, "Background play " + if (backgroundPlayEnabled) "enabled" else "disabled", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(modifier = Modifier.size(20.dp), imageVector = Icons.Filled.Headphones, contentDescription = "Background play", tint = if (backgroundPlayEnabled) Color(0xFF2196F3) else Color.White)
                                }
                                IconButton(modifier = Modifier.size(36.dp), onClick = {
                                    scale = 1.0f
                                    offsetX = 0f
                                    offsetY = 0f
                                    resizeMode = when (resizeMode) {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 5
                                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }) {
                                    val resizeIcon = when (resizeMode) {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.compose.material.icons.Icons.Filled.AspectRatio
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> androidx.compose.material.icons.Icons.Filled.Fullscreen
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.compose.material.icons.Icons.Filled.Crop
                                        else -> androidx.compose.material.icons.Icons.Filled.FullscreenExit
                                    }
                                    Icon(resizeIcon, modifier = Modifier.size(20.dp), contentDescription = "Aspect Ratio", tint = Color.White)
                                }
                                IconButton(modifier = Modifier.size(36.dp), onClick = {
                                    if (android.provider.Settings.canDrawOverlays(context)) {
                                        val overlayIntent = android.content.Intent("com.example.ACTION_WIDGET_COMMAND")
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
                                }) {
                                    Icon(modifier = Modifier.size(20.dp), imageVector = Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Mini Player", tint = Color.White)
                                }
                                IconButton(modifier = Modifier.size(36.dp), onClick = {
                                    if (android.provider.Settings.canDrawOverlays(context)) {
                                        val overlayIntent = android.content.Intent("com.example.ACTION_WIDGET_COMMAND")
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
                                }) {
                                    Icon(modifier = Modifier.size(20.dp), painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_pip), contentDescription = "PiP", tint = Color.White)
                                }
                            }
                            
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                if (isPortrait) {
                                    Box(contentAlignment = Alignment.BottomCenter) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopCenter).layout { measurable, constraints ->
                                                val placeable = measurable.measure(constraints)
                                                layout(0, 0) {
                                                    placeable.place(-placeable.width / 2, -placeable.height - 8.dp.roundToPx())
                                                }
                                            }
                                        ) {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = showToolsStack,
                                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
                                                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom)
                                            ) {
                                                Column(
                                                    modifier = Modifier.background(Color.Black.copy(alpha=0.6f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    RightTools()
                                                }
                                            }
                                        }
                                        IconButton(onClick = { showToolsStack = !showToolsStack }) {
                                            Icon(if (showToolsStack) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = "More tools", tint = Color.White)
                                        }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RightTools()
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showDetailsDialog) {
        val duration = mediaController?.duration ?: 0L
        val durationStr = if (duration > 0) formatTime(duration) else "Unknown"
        
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Properties", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text("Name: $detailsName", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Size: $detailsSize", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Duration: $durationStr", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Date Added: $detailsDate", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Path: $detailsPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDetailsDialog = false }) { Text("OK", color = Color(0xFF2196F3)) }
            }
        )
    }

    if (showAudioDialog) {
        CompactPlayerDialog(
            onDismissRequest = { showAudioDialog = false }
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                    Text("Select Audio Track", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val settings = remember { com.example.data.SettingsManager.getInstance(context) }
                    if (settings.audioBoosterEnabled) {
                        Text(
                            text = "Volume Booster: " + if (boostGainMb == 0) "Off" else "+${boostGainMb / 100}dB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = boostGainMb.toFloat(),
                            onValueChange = { newVal ->
                                val newGain = newVal.toInt()
                                boostGainMb = newGain
                                settings.boostGainMb = newGain
                                com.example.service.PlayerManager.applyAudioBoosterSettings(settings.audioBoosterEnabled, newGain)
                            },
                            valueRange = 0f..1500f,
                            steps = 14
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Text(
                            text = "Volume Booster is disabled in Player Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    val currentTracks = mediaController?.currentTracks
                    val audioGroups = currentTracks?.groups?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO } ?: emptyList()
                    
                    if (audioGroups.isEmpty()) {
                        Text("No audio tracks available", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(audioGroups.size) { groupIndex ->
                                val group = audioGroups[groupIndex]
                                for (trackIndex in 0 until group.length) {
                                    val format = group.getTrackFormat(trackIndex)
                                    val isSelected = group.isSelected
                                    val title = format.language ?: format.label ?: "Track ${trackIndex + 1}"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            val builder = mediaController?.trackSelectionParameters?.buildUpon()
                                            builder?.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                            builder?.let { mediaController?.trackSelectionParameters = it.build() }
                                            var totalIdx = 0
                                            for (g in 0 until groupIndex) {
                                                totalIdx += audioGroups[g].length
                                            }
                                            totalIdx += trackIndex
                                            settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_AUDIO, totalIdx)
                                            showAudioDialog = false
                                        }.padding(vertical = 12.dp)
                                    ) {
                                        Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
        }
    }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            audioPickerLauncher.launch("audio/*")
                            showAudioDialog = false
                        }.padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add More", tint = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add More...", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { showAudioDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleDialog) {
        CompactPlayerDialog(
            onDismissRequest = { showSubtitleDialog = false }
        ) {
            var selectedTab by remember { mutableStateOf(0) }
            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                androidx.compose.material3.TabRow(selectedTabIndex = selectedTab) {
                    androidx.compose.material3.Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tracks") })
                    androidx.compose.material3.Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Settings") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                if (selectedTab == 0) {
                    val currentTracks = mediaController?.currentTracks
                    val textGroups = currentTracks?.groups?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT } ?: emptyList()
                    val trackSelectionParameters = mediaController?.trackSelectionParameters
                    val isTextDisabled = trackSelectionParameters?.disabledTrackTypes?.contains(androidx.media3.common.C.TRACK_TYPE_TEXT) ?: false

                    if (textGroups.isEmpty()) {
                        Text("No subtitles available", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val builder = mediaController?.trackSelectionParameters?.buildUpon()
                                        builder?.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                        builder?.clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                        builder?.let { mediaController?.trackSelectionParameters = it.build() }
                                        settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_TEXT, -1)
                                        showSubtitleDialog = false
                                    }.padding(vertical = 12.dp)
                                ) {
                                    Text("Off", fontWeight = if (isTextDisabled) FontWeight.Bold else FontWeight.Normal, color = if (isTextDisabled) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            
                            items(textGroups.size) { groupIndex ->
                                val group = textGroups[groupIndex]
                                for (trackIndex in 0 until group.length) {
                                    val format = group.getTrackFormat(trackIndex)
                                    val isSelected = !isTextDisabled && group.isSelected
                                    val title = format.language ?: format.label ?: "Track ${trackIndex + 1}"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            val builder = mediaController?.trackSelectionParameters?.buildUpon()
                                            builder?.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                            builder?.setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                            builder?.let { mediaController?.trackSelectionParameters = it.build() }
                                            var totalIdx = 0
                                            for (g in 0 until groupIndex) {
                                                totalIdx += textGroups[g].length
                                            }
                                            totalIdx += trackIndex
                                            settingsManager.saveTrackSelection(decodedUriString, androidx.media3.common.C.TRACK_TYPE_TEXT, totalIdx)
                                            showSubtitleDialog = false
                                        }.padding(vertical = 12.dp)
                                    ) {
                                        Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            subtitlePickerLauncher.launch("application/octet-stream")
                            showSubtitleDialog = false
                        }.padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add More", tint = Color(0xFF2196F3))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Subtitle File...", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text("Subtitle Delay (ms): ${subtitleDelay.toInt()}", fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Slider(
                            value = subtitleDelay,
                            onValueChange = { subtitleDelay = it },
                            valueRange = -5000f..5000f,
                            steps = 100
                        )
                        Text("Delay is not natively supported by this ExoPlayer version without reloading.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Font Size: ${subtitleSize.toInt()}sp", fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Slider(
                            value = subtitleSize,
                            onValueChange = { subtitleSize = it },
                            valueRange = 8f..48f
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Subtitle Color", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val colors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta)
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(2.dp, if (subtitleColor == c) Color(0xFF2196F3) else Color.Transparent, CircleShape)
                                        .clickable { subtitleColor = c }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        CompactPlayerDialog(
            onDismissRequest = { showSpeedDialog = false }
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                    Text("Select playback speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { 
                                val newSpeed = maxOf(0.1f, playbackSpeed - 0.1f)
                                playbackSpeed = Math.round(newSpeed * 10.0f) / 10.0f
                                mediaController?.setPlaybackSpeed(playbackSpeed)
                                settingsManager.savePlaybackSpeed(decodedUriString, playbackSpeed)
                            },
                            modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                        }
                        
                        Text(String.format("%.1f", playbackSpeed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        
                        IconButton(
                            onClick = { 
                                val newSpeed = minOf(4.0f, playbackSpeed + 0.1f)
                                playbackSpeed = Math.round(newSpeed * 10.0f) / 10.0f
                                mediaController?.setPlaybackSpeed(playbackSpeed)
                                settingsManager.savePlaybackSpeed(decodedUriString, playbackSpeed)
                            },
                            modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeedSliderRow(
                            playbackSpeed = playbackSpeed,
                            onSpeedChange = { newSpeed ->
                                playbackSpeed = newSpeed
                                mediaController?.setPlaybackSpeed(newSpeed)
                                settingsManager.savePlaybackSpeed(decodedUriString, newSpeed)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { 
                            playbackSpeed = 1.0f
                            mediaController?.setPlaybackSpeed(playbackSpeed)
                            settingsManager.savePlaybackSpeed(decodedUriString, playbackSpeed)
                        }) {
                            Icon(Icons.Filled.Restore, contentDescription = "Reset")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        val predefinedSpeeds = listOf(0.5f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                        predefinedSpeeds.forEach { speed ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        playbackSpeed = speed
                                        mediaController?.setPlaybackSpeed(playbackSpeed)
                                        settingsManager.savePlaybackSpeed(decodedUriString, playbackSpeed)
                                    }
                                    .border(1.dp, if (playbackSpeed == speed) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline, CircleShape)
                                    .background(if (playbackSpeed == speed) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("${speed}x", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Skip silence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = { 
                                skipSilence = it
                                mediaController?.skipSilenceEnabled = skipSilence
                            }
                        )
                    }
        }
    }
    }

    if (showPlayerSettingsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPlayerSettingsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.example.ui.screens.PlayerSettingsScreen(onNavigateBack = { showPlayerSettingsDialog = false })
        }
    }

    if (showSleepTimerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "Off" to null,
                        "15 minutes" to 15 * 60 * 1000L,
                        "30 minutes" to 30 * 60 * 1000L,
                        "60 minutes" to 60 * 60 * 1000L,
                        "End of video" to (mediaController?.duration?.let { dur -> if (dur > 0) (dur - (mediaController?.currentPosition ?: 0)) else null })
                    )
                    options.forEach { (label, duration) ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (duration == null) {
                                    sleepTimerEndTime = null
                                } else {
                                    sleepTimerEndTime = System.currentTimeMillis() + duration
                                }
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close", color = Color(0xFF2196F3)) }
            }
        )
    }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpeedSliderRow(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = playbackSpeed,
        onValueChange = { 
            onSpeedChange(Math.round(it * 10.0f) / 10.0f)
        },
        valueRange = 0.1f..4.0f,
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFF2196F3), CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF2196F3),
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                ),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                modifier = Modifier.height(4.dp)
            )
        },
        modifier = modifier
    )
}


