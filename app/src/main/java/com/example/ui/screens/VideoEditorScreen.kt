package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.LogKeeper

data class SpeedPoint(
    val timeFraction: Float = 0f,
    val speed: Float = 1.0f
)

fun getSpeedAtTime(points: List<SpeedPoint>, fraction: Float): Float {
    if (points.isEmpty()) return 1.0f
    val clamped = fraction.coerceIn(0f, 1f)
    val sorted = points.sortedBy { it.timeFraction }
    if (clamped <= sorted.first().timeFraction) return sorted.first().speed
    if (clamped >= sorted.last().timeFraction) return sorted.last().speed

    for (i in 0 until sorted.size - 1) {
        val p1 = sorted[i]
        val p2 = sorted[i + 1]
        if (clamped in p1.timeFraction..p2.timeFraction) {
            val range = p2.timeFraction - p1.timeFraction
            if (range <= 0.0001f) return p1.speed
            val t = (clamped - p1.timeFraction) / range
            val tSmooth = t * t * (3f - 2f * t)
            return (p1.speed + tSmooth * (p2.speed - p1.speed)).coerceIn(0.2f, 5.0f)
        }
    }
    return 1.0f
}

fun buildAtempoFilter(speed: Float): String {
    val s = speed.coerceIn(0.2f, 16.0f)
    var current = s
    val filters = mutableListOf<String>()
    while (current > 2.0f) {
        filters.add("atempo=2.0")
        current /= 2.0f
    }
    while (current < 0.5f) {
        filters.add("atempo=0.5")
        current /= 0.5f
    }
    filters.add(String.format(java.util.Locale.US, "atempo=%.4f", current))
    return filters.joinToString(",")
}

fun speedToY(speed: Float, top: Float, bottom: Float): Float {
    val mid = (top + bottom) / 2f
    val clamped = speed.coerceIn(0.2f, 5.0f)
    return if (clamped >= 1.0f) {
        mid - ((clamped - 1.0f) / 4.0f) * (mid - top)
    } else {
        mid + ((1.0f - clamped) / 0.8f) * (bottom - mid)
    }
}

fun yToSpeed(y: Float, top: Float, bottom: Float): Float {
    val mid = (top + bottom) / 2f
    val clampedY = y.coerceIn(top, bottom)
    return if (clampedY <= mid) {
        1.0f + ((mid - clampedY) / (mid - top)) * 4.0f
    } else {
        1.0f - ((clampedY - mid) / (bottom - mid)) * 0.8f
    }
}

fun timeToX(tFrac: Float, left: Float, right: Float): Float {
    return left + tFrac.coerceIn(0f, 1f) * (right - left)
}

fun xToTime(x: Float, left: Float, right: Float): Float {
    val w = right - left
    if (w <= 0f) return 0f
    return ((x - left) / w).coerceIn(0f, 1f)
}

data class VideoEditState(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val isCutMode: Boolean = false,
    val isDoubleTrim: Boolean = false,
    val doubleTrimStart1Ms: Long = 0L,
    val doubleTrimEnd1Ms: Long = 0L,
    val doubleTrimStart2Ms: Long = 0L,
    val doubleTrimEnd2Ms: Long = 0L,
    val cutStartMs: Long = 0L,
    val cutEndMs: Long = 0L,
    val speed: Float = 1.0f,
    val speedMode: String = "Standard", // "Standard" or "Curve"
    val speedCurvePreset: String = "Custom",
    val speedCurvePoints: List<SpeedPoint> = listOf(
        SpeedPoint(0.0f, 1.0f),
        SpeedPoint(0.25f, 1.0f),
        SpeedPoint(0.5f, 1.0f),
        SpeedPoint(0.75f, 1.0f),
        SpeedPoint(1.0f, 1.0f)
    ),
    val volume: Float = 1.0f,
    val cropRect: String = "",
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val aspectRatio: String = "Original",
    val rotateConfig: Int = 0,
    val hasCaptions: Boolean = false,
    val captionText: String = "Sample Text",
    val joinVideoUri: String? = null,
    val joinAtEnd: Boolean = true
)

enum class VideoEditorTool {
    NONE, TRIM, SPEED, CROP, AUDIO, ASPECT_RATIO, ROTATE, CAPTIONS, JOIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    uriString: String,
    onNavigateBack: () -> Unit
) {
    var editState by remember { mutableStateOf(VideoEditState()) }
    var currentTool by remember { mutableStateOf(VideoEditorTool.NONE) }
    var backupEditState by remember { mutableStateOf<VideoEditState?>(null) }
    var showTimeInputDialog by remember { mutableStateOf<String?>(null) }
    var timeInputText by remember { mutableStateOf("") }
    var showExportPanel by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var joinDurationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val joinVideoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                // Persist permission
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {}
                editState = editState.copy(joinVideoUri = uri.toString())
            }
        }
    )
    val initialUri = Uri.parse(uriString)
    val mimeType = remember { context.contentResolver.getType(initialUri) }

    var convertedUri by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var exportedPreviewUri by remember { mutableStateOf<String?>(null) }
    var wasExporting by remember { mutableStateOf(false) }
    
    LaunchedEffect(com.example.service.FFmpegStatus.isRunning) {
        if (com.example.service.FFmpegStatus.isRunning) {
            wasExporting = true
        } else if (wasExporting) {
            wasExporting = false
            if (com.example.service.FFmpegStatus.lastOutputUri != null) {
                exportedPreviewUri = com.example.service.FFmpegStatus.lastOutputUri
            }
        }
    }

    val effectiveUri = convertedUri ?: uriString
    val effectiveMimeType = if (convertedUri != null) "video/mp4" else mimeType

    // Zero-Memory: Track session temporary files and delete them on screen exit
    val sessionTempFiles = remember { java.util.concurrent.CopyOnWriteArraySet<java.io.File>() }
    DisposableEffect(Unit) {
        // Resource Exclusivity: Pause background player to prevent decoder/CPU contention
        try {
            com.example.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {}
        onDispose {
            sessionTempFiles.forEach { file ->
                try {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                } catch (e: Exception) {}
            }
            sessionTempFiles.clear()
        }
    }

    LaunchedEffect(editState.joinVideoUri) {
        if (editState.joinVideoUri != null) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, android.net.Uri.parse(editState.joinVideoUri))
                val timeString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (timeString != null) {
                    joinDurationMs = timeString.toLong()
                }
                retriever.release()
            } catch (e: Exception) {}
        } else {
            joinDurationMs = 0L
        }
    }
    val uri = Uri.parse(effectiveUri)

    LaunchedEffect(uriString) {
        val extRaw = uriString.substringAfterLast('.', "").substringBefore('?').lowercase()
        val isM4s = extRaw == "m4s" || (mimeType == "video/mp4" && uriString.endsWith(".m4s", true))
        LogKeeper.log("Starting pre-conversion for mimeType: $mimeType uri: $uriString ext: $extRaw", "VideoEditor")
        
        if (mimeType == "image/gif" || mimeType == "image/webp" || isM4s) {
            isConverting = true
            try {
                // Step 1: Copy URI to cache file on IO thread
                val ext = if (mimeType == "image/gif") "gif" else if (mimeType == "image/webp") "webp" else "m4s"
                val inputFile = withContext(Dispatchers.IO) {
                    val f = java.io.File(context.cacheDir, "editor_in_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    f
                }
                val outputFile = java.io.File(context.cacheDir, "editor_converted_${System.currentTimeMillis()}.mp4")
                sessionTempFiles.add(outputFile)
                
                if (mimeType == "image/webp") {
                    val framesDir = java.io.File(context.cacheDir, "editor_frames_${System.currentTimeMillis()}")
                    framesDir.mkdirs()
                    var frameCount = 0

                    var calculatedFps = 30
                    withContext(Dispatchers.IO) {
                        try {
                            val bytes = inputFile.readBytes()
                            try {
                                val clazz = Class.forName("com.facebook.soloader.nativeloader.NativeLoader")
                                val isInitializedMethod = clazz.getMethod("isInitialized")
                                val isInit = isInitializedMethod.invoke(null) as Boolean
                                if (!isInit) {
                                    val delegateClazz = Class.forName("com.facebook.soloader.nativeloader.SystemDelegate")
                                    val delegate = delegateClazz.newInstance()
                                    val delegateInterface = Class.forName("com.facebook.soloader.nativeloader.NativeLoaderDelegate")
                                    val initMethod = clazz.getMethod("init", delegateInterface)
                                    initMethod.invoke(null, delegate)
                                }
                            } catch (e: Exception) {}
                            val webpImage = com.facebook.animated.webp.WebPImage.createFromByteArray(bytes, com.facebook.imagepipeline.common.ImageDecodeOptions.defaults())
                            frameCount = webpImage.frameCount
                            val durations = webpImage.frameDurations
                            val averageDurationMs = if (frameCount > 0) durations.sum() / frameCount else 33
                            calculatedFps = if (averageDurationMs > 0) 1000 / averageDurationMs else 30
                            
                            
                            var lastCachedFrame: android.graphics.Bitmap? = null
                            var lastCachedFrameIndex = -1

                            val result = com.facebook.imagepipeline.animated.base.AnimatedImageResult.forAnimatedImage(webpImage)
                            val backend = com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl(
                                com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil(), 
                                result, 
                                android.graphics.Rect(0, 0, webpImage.width, webpImage.height), 
                                false
                            )
                            val compositor = com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor(
                                backend, 
                                false, 
                                object : com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor.Callback {
                                    override fun onIntermediateResult(frameNumber: Int, bitmap: android.graphics.Bitmap) {}
                                    override fun getCachedBitmap(frameNumber: Int): com.facebook.common.references.CloseableReference<android.graphics.Bitmap>? {
                                        return if (frameNumber == lastCachedFrameIndex && lastCachedFrame != null) {
                                            com.facebook.common.references.CloseableReference.of(lastCachedFrame!!, com.facebook.common.references.ResourceReleaser { })
                                        } else null
                                    }
                                }
                            )

                            var previousFrameToRecycle: android.graphics.Bitmap? = null
                            for (i in 0 until frameCount) {
                                val w = webpImage.width
                                val h = webpImage.height
                                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                compositor.renderFrame(i, bmp)
                                java.io.File(framesDir, "frame_%04d.png".format(i))
                                    .outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                
                                previousFrameToRecycle?.recycle()
                                lastCachedFrame = bmp
                                lastCachedFrameIndex = i
                                previousFrameToRecycle = bmp
                            }
                            previousFrameToRecycle?.recycle()
                            webpImage.dispose()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LogKeeper.logError("VideoEditor", "Frame extraction failed: ${e.message}", e)
                        }
                    }

                    if (frameCount > 0) {
                        withContext(Dispatchers.IO) {
                            val cmd = "-y -framerate $calculatedFps -i '${framesDir.absolutePath}/frame_%04d.png' -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -vcodec libx264 -crf 23 -preset ultrafast -pix_fmt yuv420p -metadata:s:v:0 rotate=0 '${outputFile.absolutePath}'"
                            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                                convertedUri = outputFile.toURI().toString()
                                LogKeeper.log("Pre-conversion complete. convertedUri: $convertedUri", "VideoEditor")
                            } else {
                                LogKeeper.logError("VideoEditor", "FFmpeg PNG→MP4 failed: ${session.returnCode}\nLogs: ${session.allLogsAsString}", Exception())
                            }
                            framesDir.deleteRecursively()
                        }
                    } 
                } else if (mimeType == "image/gif") {
                    withContext(Dispatchers.IO) {
                        val cmd = "-y -i '${inputFile.absolutePath}' -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -vcodec libx264 -crf 23 -preset ultrafast -pix_fmt yuv420p -metadata:s:v:0 rotate=0 '${outputFile.absolutePath}'"
                        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                        if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                            convertedUri = outputFile.toURI().toString()
                            LogKeeper.log("Pre-conversion complete. convertedUri: $convertedUri", "VideoEditor")
                        } else {
                            LogKeeper.logError("VideoEditor", "FFmpeg GIF/WEBP→MP4 failed: ${session.returnCode}\nLogs: ${session.allLogsAsString}", Exception())
                        }
                    }
                } else if (ext == "m4s") {
                    withContext(Dispatchers.IO) {
                        val cmd = "-y -i '${inputFile.absolutePath}' -vcodec libx264 -preset ultrafast -crf 23 -acodec aac -metadata:s:v:0 rotate=0 '${outputFile.absolutePath}'"
                        val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                        if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                            convertedUri = outputFile.toURI().toString()
                            LogKeeper.log("Pre-conversion complete (m4s). convertedUri: $convertedUri", "VideoEditor")
                        } else {
                            LogKeeper.logError("VideoEditor", "FFmpeg m4s→MP4 failed: ${session.returnCode}\nLogs: ${session.allLogsAsString}", Exception())
                        }
                    }
                }
                if (inputFile.exists()) {
                    inputFile.delete()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogKeeper.logError("VideoEditor", "Pre-conversion failed: ${e.message}", e)
            }
            isConverting = false
        }
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun repairVideo() {
        scope.launch {
            playerError = null
            isConverting = true
            try {
                val inputFile = withContext(Dispatchers.IO) {
                    val f = java.io.File(context.cacheDir, "editor_in_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                    f
                }
                val outputFile = java.io.File(context.cacheDir, "editor_converted_${System.currentTimeMillis()}.mp4")
                sessionTempFiles.add(outputFile)
                withContext(Dispatchers.IO) {
                    val cmd = "-y -i '${inputFile.absolutePath}' -vcodec libx264 -preset ultrafast -crf 23 -acodec aac -metadata:s:v:0 rotate=0 '${outputFile.absolutePath}'"
                    val session = com.arthenica.ffmpegkit.FFmpegKit.execute(cmd)
                    if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && outputFile.exists()) {
                        convertedUri = outputFile.toURI().toString()
                        LogKeeper.log("Repair complete. convertedUri: $convertedUri", "VideoEditor")
                    } else {
                        LogKeeper.logError("VideoEditor", "FFmpeg repair failed: ${session.returnCode}\nLogs: ${session.allLogsAsString}", Exception())
                    }
                    inputFile.delete()
                }
            } catch (e: Exception) {
                LogKeeper.logError("VideoEditor", "Repair failed: ${e.message}", e)
            } finally {
                isConverting = false
            }
        }
    }

    // ExoPlayer for Live Preview
    var videoWidth by remember { mutableIntStateOf(1) }
    var videoHeight by remember { mutableIntStateOf(1) }
    var videoHasAudio by remember { mutableStateOf(true) }
    var currentVideoUri by remember { mutableStateOf<String?>(null) }
    
    if (currentVideoUri != effectiveUri.toString()) {
        currentVideoUri = effectiveUri.toString()
        videoWidth = 1
        videoHeight = 1
    }
    
    
    LaunchedEffect(effectiveUri) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, android.net.Uri.parse(effectiveUri.toString()))
            val timeString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (timeString != null) {
                val dur = timeString.toLong()
                if (dur > 0) {
                    durationMs = dur
                    if (editState.trimEndMs == 0L) {
                        editState = editState.copy(trimEndMs = dur)
                    }
                }
            }
            val rotStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotStr?.toIntOrNull() ?: 0
            val wStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rawW = wStr?.toIntOrNull() ?: 0
            val rawH = hStr?.toIntOrNull() ?: 0
            if (rawW > 0 && rawH > 0) {
                if (rotation == 90 || rotation == 270) {
                    videoWidth = rawH
                    videoHeight = rawW
                } else {
                    videoWidth = rawW
                    videoHeight = rawH
                }
            }
            val audioStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            videoHasAudio = (audioStr != "no")
            retriever.release()
        } catch (e: Exception) {}
    }

    val exoPlayer = remember(effectiveUri, editState.joinVideoUri, editState.joinAtEnd) {
        val uriToUse = if (mimeType == "image/gif" || mimeType == "image/webp") convertedUri else effectiveUri?.toString()
        if (uriToUse == null) null
        else androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val items = mutableListOf<androidx.media3.common.MediaItem>()
            val mainItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(uriToUse))
            val joinItem = editState.joinVideoUri?.let { androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(it)) }
            
            if (joinItem != null && !editState.joinAtEnd) {
                items.add(joinItem)
            }
            items.add(mainItem)
            if (joinItem != null && editState.joinAtEnd) {
                items.add(joinItem)
            }
            
            setMediaItems(items)
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        @Suppress("DEPRECATION")
                        val rot = videoSize.unappliedRotationDegrees
                        if (rot == 90 || rot == 270) {
                            videoWidth = videoSize.height
                            videoHeight = videoSize.width
                        } else if (videoWidth <= 1 || videoHeight <= 1) {
                            videoWidth = videoSize.width
                            videoHeight = videoSize.height
                        }
                    }
                }
            })
        }
    }
    if (exoPlayer != null) {
        DisposableEffect(exoPlayer) {
            LogKeeper.log("ExoPlayer initialized for video editor", "VideoEditor")
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    LogKeeper.logError("VideoEditor", "Player error: ${error.message}", error)
                    playerError = "Playback error. The format might not be fully supported by the player.\nWould you like to repair/convert it with FFmpeg?"
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        // Duration is now fetched via MediaMetadataRetriever to avoid playlist issues
                        LogKeeper.log("ExoPlayer is READY.", "VideoEditor")
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                LogKeeper.log("ExoPlayer released from video editor", "VideoEditor")
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    val cropLeftKey = if (currentTool == VideoEditorTool.CROP) 0f else editState.cropLeft
    val cropRightKey = if (currentTool == VideoEditorTool.CROP) 0f else editState.cropRight
    // Live preview updates based on edit state
    LaunchedEffect(editState.speed, editState.speedMode) {
        if (editState.speedMode == "Standard") {
            LogKeeper.log("Video playback speed adjusted to: ${editState.speed}x", "VideoEditor")
            exoPlayer?.setPlaybackSpeed(editState.speed)
        }
    }
    LaunchedEffect(editState.volume) {
        LogKeeper.log("Video playback volume adjusted to: ${editState.volume * 100}%", "VideoEditor")
        exoPlayer?.volume = editState.volume
    }

    // MediaItem is already set during ExoPlayer creation. No need to reset it on trim edits.

    if (playerError != null && !isConverting) {
        AlertDialog(
            onDismissRequest = { playerError = null },
            title = { Text("Unsupported Format") },
            text = { Text(playerError!!) },
            confirmButton = {
                TextButton(onClick = { repairVideo() }) {
                    Text("Repair Format")
                }
            },
            dismissButton = {
                TextButton(onClick = { playerError = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isConverting) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color = Color.White)
                Text("Converting for editing...", color = Color.White)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Undo */ }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { /* Redo */ }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    Button(onClick = { showExportPanel = true }) {
                        Text("SAVE")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Main UI: Player Preview
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                val baseRatio = if (videoWidth > 0 && videoHeight > 0) {
                    if (editState.rotateConfig % 180 != 0) videoHeight.toFloat() / videoWidth.toFloat()
                    else videoWidth.toFloat() / videoHeight.toFloat()
                } else 16f/9f
                val ratio = baseRatio
                val effectiveRatio = when {
                    editState.aspectRatio != "Original" -> {
                        when (editState.aspectRatio) {
                            "16:9" -> 16f / 9f
                            "9:16" -> 9f / 16f
                            "1:1" -> 1f
                            "4:3" -> 4f / 3f
                            "21:9" -> 21f / 9f
                            else -> ratio
                        }
                    }
                    editState.cropRect != "None" && editState.cropRect != "Custom" && currentTool != VideoEditorTool.CROP -> {
                        when (editState.cropRect) {
                            "16:9", "Fill 16:9" -> 16f / 9f
                            "9:16" -> 9f / 16f
                            "1:1" -> 1f
                            "4:3" -> 4f / 3f
                            "21:9" -> 21f / 9f
                            else -> ratio
                        }
                    }
                    editState.cropRect == "Custom" && currentTool != VideoEditorTool.CROP -> {
                        val cw = editState.cropRight - editState.cropLeft
                        val ch = editState.cropBottom - editState.cropTop
                        if (cw > 0 && ch > 0) {
                            val effW = if (editState.rotateConfig % 180 != 0) videoHeight.toFloat() else videoWidth.toFloat()
                            val effH = if (editState.rotateConfig % 180 != 0) videoWidth.toFloat() else videoHeight.toFloat()
                            (cw * effW) / (ch * effH)
                        } else ratio
                    }
                    else -> ratio
                }
                
                var visualCropLeft = 0f
                var visualCropTop = 0f
                var visualCropWidth = 1f
                var visualCropHeight = 1f
                
                if (editState.cropRect != "None" && currentTool != VideoEditorTool.CROP) {
                    if (editState.cropRect == "Custom") {
                        visualCropLeft = editState.cropLeft
                        visualCropTop = editState.cropTop
                        visualCropWidth = editState.cropRight - editState.cropLeft
                        visualCropHeight = editState.cropBottom - editState.cropTop
                    } else {
                        val targetRatio = when (editState.cropRect) {
                            "16:9", "Fill 16:9" -> 16f / 9f
                            "9:16" -> 9f / 16f
                            "1:1" -> 1f
                            "4:3" -> 4f / 3f
                            "21:9" -> 21f / 9f
                            else -> baseRatio
                        }
                        if (baseRatio > targetRatio) {
                            visualCropHeight = 1f
                            visualCropWidth = targetRatio / baseRatio
                            visualCropLeft = (1f - visualCropWidth) / 2f
                            visualCropTop = 0f
                        } else {
                            visualCropWidth = 1f
                            visualCropHeight = baseRatio / targetRatio
                            visualCropTop = (1f - visualCropHeight) / 2f
                            visualCropLeft = 0f
                        }
                    }
                }
                visualCropWidth = visualCropWidth.coerceAtLeast(0.01f)
                visualCropHeight = visualCropHeight.coerceAtLeast(0.01f)

                val containerWidthPx = constraints.maxWidth.toFloat()
                val containerHeightPx = constraints.maxHeight.toFloat()
                val containerRatio = if (containerHeightPx > 0f) containerWidthPx / containerHeightPx else 1f

                val (boxWidthDp, boxHeightDp) = with(androidx.compose.ui.platform.LocalDensity.current) {
                    if (effectiveRatio > containerRatio) {
                        val w = containerWidthPx
                        val h = containerWidthPx / effectiveRatio
                        w.toDp() to h.toDp()
                    } else {
                        val h = containerHeightPx
                        val w = containerHeightPx * effectiveRatio
                        w.toDp() to h.toDp()
                    }
                }

                val visualModifier = Modifier
                    .size(boxWidthDp, boxHeightDp)
                    .background(Color.DarkGray)
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (currentTool != VideoEditorTool.CROP) {
                            if (exoPlayer?.isPlaying == true) {
                                exoPlayer?.pause()
                            } else {
                                exoPlayer?.play()
                            }
                        }
                    }

                Box(modifier = visualModifier, contentAlignment = Alignment.Center) {
                    if (exoPlayer != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layout { measurable, constraints ->
                                    val childW = (constraints.maxWidth / visualCropWidth).toInt()
                                    val childH = (constraints.maxHeight / visualCropHeight).toInt()
                                    
                                    val isRotated = editState.rotateConfig % 180 != 0
                                    val measureW = if (isRotated) childH else childW
                                    val measureH = if (isRotated) childW else childH
                                    
                                    val placeable = measurable.measure(
                                        androidx.compose.ui.unit.Constraints.fixed(measureW, measureH)
                                    )
                                    
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        val x = (constraints.maxWidth - placeable.width) / 2
                                        val y = (constraints.maxHeight - placeable.height) / 2
                                        placeable.placeRelative(x, y)
                                    }
                                }
                                .graphicsLayer {
                                    rotationZ = editState.rotateConfig.toFloat()
                                    
                                    val postW = if (editState.rotateConfig % 180 != 0) size.height else size.width
                                    val postH = if (editState.rotateConfig % 180 != 0) size.width else size.height
                                    
                                    val cropCenterX = (visualCropLeft + visualCropWidth / 2f) * postW
                                    val cropCenterY = (visualCropTop + visualCropHeight / 2f) * postH
                                    
                                    val offsetX = cropCenterX - postW / 2f
                                    val offsetY = cropCenterY - postH / 2f
                                    
                                    translationX = -offsetX
                                    translationY = -offsetY
                                    },
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                            factory = { ctx ->
                                val view = android.view.LayoutInflater.from(ctx).inflate(com.example.R.layout.player_view_texture, null) as PlayerView
                                view.apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = if (editState.aspectRatio != "Original") androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            update = { view ->
                                if (view.player != exoPlayer) {
                                    view.player = exoPlayer
                                }
                                view.useController = false
                                view.resizeMode = if (editState.aspectRatio != "Original") androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }

                        if (!isPlaying && currentTool != VideoEditorTool.CROP) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }

                    val showCropOverlay = (currentTool == VideoEditorTool.CROP && editState.cropRect != "None")
                    
                    if (showCropOverlay) {
                        var resizeCorner by remember { mutableIntStateOf(0) }
                        
                        val isCustom = currentTool == VideoEditorTool.CROP && editState.cropRect == "Custom"
                        
                        var displayCropLeft = editState.cropLeft
                        var displayCropTop = editState.cropTop
                        var displayCropRight = editState.cropRight
                        var displayCropBottom = editState.cropBottom
                        
                        if (!isCustom) {
                            val videoAspect = effectiveRatio
                            val targetRatio = when (editState.cropRect) {
                                "16:9", "Fill 16:9" -> 16f / 9f
                                "9:16" -> 9f / 16f
                                "1:1" -> 1f
                                "4:3" -> 4f / 3f
                                "21:9" -> 21f / 9f
                                else -> videoAspect
                            }
                            
                            if (videoAspect > targetRatio) {
                                val cropWidth = targetRatio / videoAspect
                                displayCropLeft = (1f - cropWidth) / 2f
                                displayCropRight = 1f - displayCropLeft
                                displayCropTop = 0f
                                displayCropBottom = 1f
                            } else {
                                val cropHeight = videoAspect / targetRatio
                                displayCropTop = (1f - cropHeight) / 2f
                                displayCropBottom = 1f - displayCropTop
                                displayCropLeft = 0f
                                displayCropRight = 1f
                            }
                        }

                        val pointerInputModifier = if (isCustom) {
                            Modifier.pointerInput(isCustom) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val drawWidth = size.width.toFloat()
                                        val drawHeight = size.height.toFloat()
                                        if (drawWidth <= 0f || drawHeight <= 0f) return@detectDragGestures
                                        
                                        val cL = editState.cropLeft * drawWidth
                                        val cT = editState.cropTop * drawHeight
                                        val cR = editState.cropRight * drawWidth
                                        val cB = editState.cropBottom * drawHeight
                                        
                                        val touchRadius = 60f
                                        if (abs(offset.x - cL) < touchRadius && abs(offset.y - cT) < touchRadius) resizeCorner = 1
                                        else if (abs(offset.x - cR) < touchRadius && abs(offset.y - cT) < touchRadius) resizeCorner = 2
                                        else if (abs(offset.x - cL) < touchRadius && abs(offset.y - cB) < touchRadius) resizeCorner = 3
                                        else if (abs(offset.x - cR) < touchRadius && abs(offset.y - cB) < touchRadius) resizeCorner = 4
                                        else if (offset.x > cL && offset.x < cR && offset.y > cT && offset.y < cB) resizeCorner = 5
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val drawWidth = size.width.toFloat()
                                        val drawHeight = size.height.toFloat()
                                        if (drawWidth <= 0f || drawHeight <= 0f) return@detectDragGestures
                                        
                                        val dx = dragAmount.x / drawWidth
                                        val dy = dragAmount.y / drawHeight
                                        
                                        var nL = editState.cropLeft
                                        var nT = editState.cropTop
                                        var nR = editState.cropRight
                                        var nB = editState.cropBottom
                                        
                                        when (resizeCorner) {
                                            5 -> {
                                                val boxW = nR - nL
                                                val boxH = nB - nT
                                                nL = (nL + dx).coerceIn(0f, 1f - boxW)
                                                nR = nL + boxW
                                                nT = (nT + dy).coerceIn(0f, 1f - boxH)
                                                nB = nT + boxH
                                            }
                                            1 -> {
                                                nL = (nL + dx).coerceIn(0f, nR - 0.05f)
                                                nT = (nT + dy).coerceIn(0f, nB - 0.05f)
                                            }
                                            2 -> {
                                                nR = (nR + dx).coerceIn(nL + 0.05f, 1f)
                                                nT = (nT + dy).coerceIn(0f, nB - 0.05f)
                                            }
                                            3 -> {
                                                nL = (nL + dx).coerceIn(0f, nR - 0.05f)
                                                nB = (nB + dy).coerceIn(nT + 0.05f, 1f)
                                            }
                                            4 -> {
                                                nR = (nR + dx).coerceIn(nL + 0.05f, 1f)
                                                nB = (nB + dy).coerceIn(nT + 0.05f, 1f)
                                            }
                                        }
                                        editState = editState.copy(cropLeft = nL, cropTop = nT, cropRight = nR, cropBottom = nB)
                                    },
                                    onDragEnd = { resizeCorner = 0 },
                                    onDragCancel = { resizeCorner = 0 }
                                )
                            }
                        } else {
                            Modifier
                        }

                        Canvas(modifier = Modifier.fillMaxSize().then(pointerInputModifier)) {
                            val drawWidth = size.width
                            val drawHeight = size.height
                            if (drawWidth <= 0f || drawHeight <= 0f) return@Canvas
                            
                            val cL = displayCropLeft * drawWidth
                            val cT = displayCropTop * drawHeight
                            val cR = displayCropRight * drawWidth
                            val cB = displayCropBottom * drawHeight
                            
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f), size = Size(drawWidth, cT))
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, cB), size = Size(drawWidth, drawHeight - cB))
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, cT), size = Size(cL, cB - cT))
                            drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(cR, cT), size = Size(drawWidth - cR, cB - cT))
                            
                            drawRect(color = Color.White, topLeft = Offset(cL, cT), size = Size(cR - cL, cB - cT), style = Stroke(width = 5f))
                            
                            if (isCustom) {
                                val cornerLen = 40f
                                drawLine(Color.Green, Offset(cL, cT), Offset(cL + cornerLen, cT), 12f)
                                drawLine(Color.Green, Offset(cL, cT), Offset(cL, cT + cornerLen), 12f)
                                
                                drawLine(Color.Green, Offset(cR, cT), Offset(cR - cornerLen, cT), 12f)
                                drawLine(Color.Green, Offset(cR, cT), Offset(cR, cT + cornerLen), 12f)
                                
                                drawLine(Color.Green, Offset(cL, cB), Offset(cL + cornerLen, cB), 12f)
                                drawLine(Color.Green, Offset(cL, cB), Offset(cL, cB - cornerLen), 12f)
                                
                                drawLine(Color.Green, Offset(cR, cB), Offset(cR - cornerLen, cB), 12f)
                                drawLine(Color.Green, Offset(cR, cB), Offset(cR, cB - cornerLen), 12f)
                            }
                        }
                    }
                }

                if (editState.hasCaptions && editState.captionText.isNotBlank()) {
                    androidx.compose.material3.Text(
                        text = editState.captionText,
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = androidx.compose.ui.graphics.Color.Black,
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 8f
                            )
                        ),
                        modifier = androidx.compose.ui.Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    )
                }
            }

            // Timeline / Progress Bar Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp)
            ) {
                var currentPositionMs by remember { mutableLongStateOf(0L) }
                var currentIndex by remember { mutableIntStateOf(0) }
                var isDragging by remember { mutableStateOf(false) }

                val currentEditState by rememberUpdatedState(editState)
                val currentToolState by rememberUpdatedState(currentTool)
                val mainVideoIndex = if (currentEditState.joinVideoUri != null && !currentEditState.joinAtEnd) 1 else 0
                val totalItems = if (currentEditState.joinVideoUri != null) 2 else 1
                LaunchedEffect(exoPlayer) {
                    while (true) {
                        if (!isDragging) {
                            val currentIndexRaw = exoPlayer?.currentMediaItemIndex ?: 0
                            currentIndex = currentIndexRaw
                            currentPositionMs = exoPlayer?.currentPosition ?: 0L
                            
                            if (currentIndexRaw == mainVideoIndex) {
                                if (currentEditState.isDoubleTrim) {
                                    val ds1 = currentEditState.doubleTrimStart1Ms.coerceIn(0L, durationMs)
                                    val de1 = currentEditState.doubleTrimEnd1Ms.coerceIn(ds1, durationMs).takeIf { it > 0 } ?: (durationMs / 2)
                                    val ds2 = currentEditState.doubleTrimStart2Ms.coerceIn(de1, durationMs)
                                    val de2 = currentEditState.doubleTrimEnd2Ms.coerceIn(ds2, durationMs).takeIf { it > 0 } ?: durationMs
                                    
                                    if (currentPositionMs >= de1 && currentPositionMs < ds2) {
                                        exoPlayer?.seekTo(mainVideoIndex, ds2)
                                        currentPositionMs = ds2
                                    } else if (currentPositionMs >= de2) {
                                        val nextIndex = (mainVideoIndex + 1) % totalItems
                                        val nextPos = if (nextIndex == mainVideoIndex) ds1 else 0L
                                        exoPlayer?.seekTo(nextIndex, nextPos)
                                        currentPositionMs = nextPos
                                    } else if (currentPositionMs < ds1) {
                                        exoPlayer?.seekTo(mainVideoIndex, ds1)
                                        currentPositionMs = ds1
                                    }
                                } else if (!currentEditState.isCutMode) {
                                    val start = currentEditState.trimStartMs.coerceIn(0L, durationMs)
                                    val end = currentEditState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                                    if (end > 0 && currentPositionMs >= end) {
                                        val nextIndex = (mainVideoIndex + 1) % totalItems
                                        val nextPos = if (nextIndex == mainVideoIndex) start else 0L
                                        exoPlayer?.seekTo(nextIndex, nextPos)
                                        currentPositionMs = nextPos
                                    } else if (currentPositionMs < start) {
                                        exoPlayer?.seekTo(mainVideoIndex, start)
                                        currentPositionMs = start
                                    }
                                } else {
                                    // In cut mode, we skip the middle
                                    val start = currentEditState.trimStartMs.coerceIn(0L, durationMs)
                                    val end = currentEditState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                                    if (currentPositionMs >= start && currentPositionMs < end) {
                                        exoPlayer?.seekTo(mainVideoIndex, end)
                                        currentPositionMs = end
                                    }
                                    if (currentPositionMs >= durationMs) {
                                        val nextIndex = (mainVideoIndex + 1) % totalItems
                                        val nextPos = if (nextIndex == mainVideoIndex) 0L else 0L
                                        exoPlayer?.seekTo(nextIndex, nextPos)
                                        currentPositionMs = nextPos
                                    }
                                }
                            }
                        }
                        if (currentEditState.speedMode == "Curve" && durationMs > 0L && exoPlayer?.isPlaying == true) {
                            val tFrac = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val targetSpeed = getSpeedAtTime(currentEditState.speedCurvePoints, tFrac).coerceIn(0.2f, 5.0f)
                            if (Math.abs((exoPlayer?.playbackParameters?.speed ?: 1f) - targetSpeed) > 0.05f) {
                                exoPlayer?.setPlaybackSpeed(targetSpeed)
                            }
                        }
                        kotlinx.coroutines.delay(if (exoPlayer?.isPlaying == true) 50L else 250L)
                    }
                }

                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                val isTrimMode = currentTool == VideoEditorTool.TRIM
                
                var virtualDurationMs = durationMs
                var virtualPositionMs = currentPositionMs
                
                
                if (true) { 
                    if (editState.isDoubleTrim) {
                        val ds1 = editState.doubleTrimStart1Ms.coerceIn(0L, durationMs)
                        val de1 = editState.doubleTrimEnd1Ms.coerceIn(ds1, durationMs).takeIf { it > 0 } ?: (durationMs / 2)
                        val ds2 = editState.doubleTrimStart2Ms.coerceIn(de1, durationMs)
                        val de2 = editState.doubleTrimEnd2Ms.coerceIn(ds2, durationMs).takeIf { it > 0 } ?: durationMs
                           
                        val dur1 = (de1 - ds1).coerceAtLeast(0)
                        val dur2 = (de2 - ds2).coerceAtLeast(0)
                        virtualDurationMs = dur1 + dur2
                           
                        virtualPositionMs = when {
                            currentPositionMs < ds1 -> 0L
                            currentPositionMs <= de1 -> currentPositionMs - ds1
                            currentPositionMs < ds2 -> dur1
                            currentPositionMs <= de2 -> dur1 + (currentPositionMs - ds2)
                            else -> dur1 + dur2
                        }
                    } else if (editState.isCutMode) {
                        val start = editState.trimStartMs.coerceIn(0L, durationMs)
                        val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                           
                        val dur1 = start
                        val dur2 = (durationMs - end).coerceAtLeast(0)
                        virtualDurationMs = dur1 + dur2
                           
                        virtualPositionMs = when {
                            currentPositionMs <= start -> currentPositionMs
                            currentPositionMs < end -> dur1
                            else -> dur1 + (currentPositionMs - end)
                        }
                    } else {
                        val start = editState.trimStartMs.coerceIn(0L, durationMs)
                        val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                           
                        virtualDurationMs = (end - start).coerceAtLeast(0)
                           
                        virtualPositionMs = when {
                            currentPositionMs < start -> 0L
                            currentPositionMs <= end -> currentPositionMs - start
                            else -> virtualDurationMs
                        }
                    }
                }
                
                if (editState.joinVideoUri != null) {
                    val mainDur = virtualDurationMs
                    virtualDurationMs += joinDurationMs
                    
                    if (currentIndex == mainVideoIndex) {
                        if (!editState.joinAtEnd) {
                            virtualPositionMs += joinDurationMs
                        }
                    } else {
                        virtualPositionMs = currentPositionMs
                        if (editState.joinAtEnd) {
                            virtualPositionMs += mainDur
                        }
                    }
                }
                   
                Slider(
                    value = if (virtualDurationMs > 0) (virtualPositionMs.toFloat() / virtualDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { value ->
                        isDragging = true
                        val newVirtualPos = (value * virtualDurationMs).toLong()
                        
                        val mainVideoIndexState = if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0
                        val mainDur = if (editState.joinVideoUri != null) virtualDurationMs - joinDurationMs else virtualDurationMs
                        
                        val isJoinPlay = if (editState.joinVideoUri != null) {
                            if (editState.joinAtEnd) newVirtualPos > mainDur else newVirtualPos < joinDurationMs
                        } else false
                        
                        if (isJoinPlay) {
                            val targetIndex = if (editState.joinAtEnd) 1 else 0
                            val targetPos = if (editState.joinAtEnd) newVirtualPos - mainDur else newVirtualPos
                            currentPositionMs = targetPos
                            currentIndex = targetIndex
                            exoPlayer?.seekTo(targetIndex, targetPos)
                        } else {
                            val mainVirtualPos = if (editState.joinVideoUri != null && !editState.joinAtEnd) newVirtualPos - joinDurationMs else newVirtualPos
                            var newRealPos = mainVirtualPos
                            
                            if (true) {
                                if (editState.isDoubleTrim) {
                                    val ds1 = editState.doubleTrimStart1Ms.coerceIn(0L, durationMs)
                                    val de1 = editState.doubleTrimEnd1Ms.coerceIn(ds1, durationMs).takeIf { it > 0 } ?: (durationMs / 2)
                                    val ds2 = editState.doubleTrimStart2Ms.coerceIn(de1, durationMs)
                                    val dur1 = (de1 - ds1).coerceAtLeast(0)
                                       
                                    newRealPos = if (mainVirtualPos <= dur1) {
                                        ds1 + mainVirtualPos
                                    } else {
                                        ds2 + (mainVirtualPos - dur1)
                                    }
                                } else if (editState.isCutMode) {
                                    val start = editState.trimStartMs.coerceIn(0L, durationMs)
                                    val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                                       
                                    newRealPos = if (mainVirtualPos <= start) {
                                        mainVirtualPos
                                    } else {
                                        end + (mainVirtualPos - start)
                                    }
                                } else {
                                    val start = editState.trimStartMs.coerceIn(0L, durationMs)
                                    newRealPos = start + mainVirtualPos
                                }
                            }
                               
                            currentPositionMs = newRealPos.coerceIn(0, durationMs)
                            currentIndex = mainVideoIndexState
                            exoPlayer?.seekTo(mainVideoIndexState, currentPositionMs)
                        }
                    },
                    onValueChangeFinished = {
                        isDragging = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer?.pause()
                                } else {
                                    exoPlayer?.play()
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color(0xFF2196F3), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(formatMsScaled(virtualPositionMs, editState.speed), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Total: ${formatMsScaled(virtualDurationMs, editState.speed)}", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Tools Bottom Bar / Partial UI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (currentTool == VideoEditorTool.NONE) {
                    // Main Tools Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        ToolIcon(Icons.Filled.ContentCut, "Trim") { backupEditState = editState.copy(); currentTool = VideoEditorTool.TRIM }
                        ToolIcon(Icons.Filled.Speed, "Speed") { backupEditState = editState.copy(); currentTool = VideoEditorTool.SPEED }
                        ToolIcon(Icons.Filled.Crop, "Crop") {
                            backupEditState = editState.copy()
                            if (editState.cropRect.isEmpty() || editState.cropRect == "None") {
                                editState = editState.copy(
                                    cropRect = "Custom",
                                    cropLeft = 0f,
                                    cropTop = 0f,
                                    cropRight = 1f,
                                    cropBottom = 1f
                                )
                            }
                            currentTool = VideoEditorTool.CROP
                        }
                        ToolIcon(Icons.Filled.VolumeUp, "Audio") { backupEditState = editState.copy(); currentTool = VideoEditorTool.AUDIO }
                        ToolIcon(Icons.Filled.AspectRatio, "Aspect Ratio") { backupEditState = editState.copy(); currentTool = VideoEditorTool.ASPECT_RATIO }
                        ToolIcon(Icons.Filled.RotateRight, "Rotate") { backupEditState = editState.copy(); currentTool = VideoEditorTool.ROTATE }
                        ToolIcon(Icons.Filled.ClosedCaption, "Captions") { backupEditState = editState.copy(); currentTool = VideoEditorTool.CAPTIONS }
                        ToolIcon(Icons.Filled.Add, "Join") { backupEditState = editState.copy(); currentTool = VideoEditorTool.JOIN }
                    }
                } else {
                    // Partial Tool UI Panel
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(currentTool.name, style = MaterialTheme.typography.titleSmall)
                            Row {
                                IconButton(onClick = { 
                                    backupEditState?.let { editState = it }
                                    currentTool = VideoEditorTool.NONE 
                                    backupEditState = null
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                                }
                                IconButton(onClick = { 
                                    currentTool = VideoEditorTool.NONE 
                                    backupEditState = null
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = "Done")
                                }
                            }
                        }
                        
                        // Tool specific sliders/buttons placeholder
                        when (currentTool) {
                            VideoEditorTool.TRIM -> {
                                val start = editState.trimStartMs.toFloat().coerceIn(0f, durationMs.toFloat())
                                val end = editState.trimEndMs.toFloat().coerceIn(start, durationMs.toFloat()).takeIf { it > 0 } ?: durationMs.toFloat()
                                val ds1 = editState.doubleTrimStart1Ms.toFloat().coerceIn(0f, durationMs.toFloat())
                                val de1 = editState.doubleTrimEnd1Ms.toFloat().coerceIn(ds1, durationMs.toFloat()).takeIf { it > 0 } ?: (durationMs.toFloat() / 2f)
                                val ds2 = editState.doubleTrimStart2Ms.toFloat().coerceIn(de1, durationMs.toFloat())
                                val de2 = editState.doubleTrimEnd2Ms.toFloat().coerceIn(ds2, durationMs.toFloat()).takeIf { it > 0 } ?: durationMs.toFloat()
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (editState.isDoubleTrim) {
                                        // Double Trim UI (Two parts to keep)
                                        // Slider 1
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = formatMsScaled(ds1.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(ds1.toLong()); showTimeInputDialog = "ds1" }.padding(8.dp))
                                            Text(text = "Cut 1: ${formatMsScaled((de1 - ds1).toLong(), editState.speed)}", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
                                            Text(text = formatMsScaled(de1.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(de1.toLong()); showTimeInputDialog = "de1" }.padding(8.dp))
                                        }
                                        RangeSlider(
                                            value = ds1..de1,
                                            onValueChange = { range ->
                                                editState = editState.copy(doubleTrimStart1Ms = range.start.toLong(), doubleTrimEnd1Ms = range.endInclusive.toLong())
                                                exoPlayer?.seekTo(if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0, if (Math.abs(range.start - ds1) > 100) range.start.toLong() else range.endInclusive.toLong())
                                            },
                                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        
                                        // Slider 2
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = formatMsScaled(ds2.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(ds2.toLong()); showTimeInputDialog = "ds2" }.padding(8.dp))
                                            Text(text = "Cut 2: ${formatMsScaled((de2 - ds2).toLong(), editState.speed)}", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
                                            Text(text = formatMsScaled(de2.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(de2.toLong()); showTimeInputDialog = "de2" }.padding(8.dp))
                                        }
                                        RangeSlider(
                                            value = ds2..de2,
                                            onValueChange = { range ->
                                                editState = editState.copy(doubleTrimStart2Ms = range.start.toLong(), doubleTrimEnd2Ms = range.endInclusive.toLong())
                                                exoPlayer?.seekTo(if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0, if (Math.abs(range.start - ds2) > 100) range.start.toLong() else range.endInclusive.toLong())
                                            },
                                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    } else {
                                        // Single Trim/Cut UI
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = formatMsScaled(start.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(start.toLong()); showTimeInputDialog = "start" }.padding(8.dp))
                                            Text(text = "Cut: ${formatMsScaled((end - start).toLong(), editState.speed)}", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary))
                                            Text(text = formatMsScaled(end.toLong(), editState.speed), style = MaterialTheme.typography.bodyMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).clickable { timeInputText = formatTimeInput(end.toLong()); showTimeInputDialog = "end" }.padding(8.dp))
                                        }
                                                                                
                                        val activeTrackColor = if (editState.isCutMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                        val inactiveTrackColor = if (editState.isCutMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        
                                        RangeSlider(
                                            value = start..end,
                                            onValueChange = { range ->
                                                val oldStart = editState.trimStartMs
                                                editState = editState.copy(
                                                    trimStartMs = range.start.toLong(),
                                                    trimEndMs = range.endInclusive.toLong()
                                                )
                                                if (Math.abs(range.start.toLong() - oldStart) > 100) {
                                                    exoPlayer?.seekTo(if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0, range.start.toLong())
                                                } else {
                                                    exoPlayer?.seekTo(if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0, range.endInclusive.toLong())
                                                }
                                            },
                                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = androidx.compose.material3.SliderDefaults.colors(
                                                activeTrackColor = activeTrackColor,
                                                inactiveTrackColor = inactiveTrackColor
                                            )
                                        )
                                    }
                                    
                                    Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                                        FilterChip(selected = !editState.isCutMode && !editState.isDoubleTrim, onClick = { editState = editState.copy(isCutMode = false, isDoubleTrim = false) }, label = { Text("Keep Middle") })
                                        Spacer(Modifier.width(8.dp))
                                        FilterChip(selected = editState.isCutMode && !editState.isDoubleTrim, onClick = { editState = editState.copy(isCutMode = true, isDoubleTrim = false) }, label = { Text("Remove Middle") })
                                        Spacer(Modifier.width(8.dp))
                                        FilterChip(selected = editState.isDoubleTrim, onClick = { editState = editState.copy(isDoubleTrim = true) }, label = { Text("Keep 2 Parts") })
                                    }
                                }
                            }
                            VideoEditorTool.SPEED -> {
                                var selectedPointIndex by remember { mutableIntStateOf(-1) }
                                val primaryColor = MaterialTheme.colorScheme.primary

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Mode Switcher (Standard vs. Curve)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = editState.speedMode == "Standard",
                                            onClick = {
                                                editState = editState.copy(speedMode = "Standard")
                                            },
                                            label = { Text("Standard") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Speed,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        FilterChip(
                                            selected = editState.speedMode == "Curve",
                                            onClick = {
                                                editState = editState.copy(speedMode = "Curve")
                                            },
                                            label = { Text("Speed Curve (Wave)") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.GraphicEq,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }

                                    if (editState.speedMode == "Standard") {
                                        // Standard Mode UI
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Playback Speed", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = String.format(java.util.Locale.US, "%.2fx", editState.speed),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Slider(
                                            value = editState.speed,
                                            onValueChange = { editState = editState.copy(speed = it) },
                                            valueRange = 0.25f..16f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f, 12f, 16f).forEach { preset ->
                                                FilterChip(
                                                    selected = Math.abs(editState.speed - preset) < 0.05f,
                                                    onClick = { editState = editState.copy(speed = preset) },
                                                    label = { Text(if (preset == 1f) "1x (Normal)" else "${preset}x") }
                                                )
                                            }
                                        }
                                    } else {
                                        // Speed Curve (Wave) Mode UI
                                        val curPlayFrac = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                        val curPlaySpeed = getSpeedAtTime(editState.speedCurvePoints, curPlayFrac)
                                        val selectedPoint = editState.speedCurvePoints.getOrNull(selectedPointIndex)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Speed: ${String.format(java.util.Locale.US, "%.2fx", curPlaySpeed)}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "At: ${formatMs(currentPositionMs)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (selectedPoint != null) {
                                                Text(
                                                    text = "Node: ${String.format(java.util.Locale.US, "%.2fx", selectedPoint.speed)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Action buttons: Add Point, Delete Point, Reset
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    val points = editState.speedCurvePoints.toMutableList()
                                                    val alreadyNear = points.any { Math.abs(it.timeFraction - curPlayFrac) < 0.03f }
                                                    if (!alreadyNear && curPlayFrac > 0.02f && curPlayFrac < 0.98f) {
                                                        points.add(SpeedPoint(curPlayFrac, curPlaySpeed))
                                                        val sorted = points.sortedBy { it.timeFraction }
                                                        editState = editState.copy(speedCurvePoints = sorted, speedCurvePreset = "Custom")
                                                        selectedPointIndex = sorted.indexOfFirst { Math.abs(it.timeFraction - curPlayFrac) < 0.001f }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add Point", style = MaterialTheme.typography.labelMedium)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    if (selectedPointIndex > 0 && selectedPointIndex < editState.speedCurvePoints.size - 1) {
                                                        val points = editState.speedCurvePoints.toMutableList()
                                                        points.removeAt(selectedPointIndex)
                                                        editState = editState.copy(speedCurvePoints = points, speedCurvePreset = "Custom")
                                                        selectedPointIndex = -1
                                                    }
                                                },
                                                enabled = selectedPointIndex > 0 && selectedPointIndex < editState.speedCurvePoints.size - 1,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete Point", style = MaterialTheme.typography.labelMedium)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    editState = editState.copy(
                                                        speedCurvePoints = listOf(
                                                            SpeedPoint(0.0f, 1.0f),
                                                            SpeedPoint(0.25f, 1.0f),
                                                            SpeedPoint(0.5f, 1.0f),
                                                            SpeedPoint(0.75f, 1.0f),
                                                            SpeedPoint(1.0f, 1.0f)
                                                        ),
                                                        speedCurvePreset = "Reset"
                                                    )
                                                    selectedPointIndex = -1
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Reset", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Interactive Wave Canvas
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        ) {
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .pointerInput(editState.speedCurvePoints, durationMs) {
                                                        detectTapGestures(
                                                            onTap = { offset ->
                                                                val padX = 24.dp.toPx()
                                                                val padY = 16.dp.toPx()
                                                                val graphW = size.width - 2 * padX
                                                                val graphH = size.height - 2 * padY
                                                                val hitThresholdSq = 32.dp.toPx() * 32.dp.toPx()
                                                                val hitIndex = editState.speedCurvePoints.indexOfFirst { pt ->
                                                                    val px = timeToX(pt.timeFraction, padX, padX + graphW)
                                                                    val py = speedToY(pt.speed, padY, padY + graphH)
                                                                    val distSq = (offset.x - px) * (offset.x - px) + (offset.y - py) * (offset.y - py)
                                                                    distSq <= hitThresholdSq
                                                                }
                                                                if (hitIndex != -1) {
                                                                    selectedPointIndex = hitIndex
                                                                    val targetMs = (editState.speedCurvePoints[hitIndex].timeFraction * durationMs).toLong()
                                                                    exoPlayer?.seekTo(targetMs)
                                                                } else {
                                                                    val tFrac = xToTime(offset.x, padX, padX + graphW)
                                                                    val targetMs = (tFrac * durationMs).toLong()
                                                                    exoPlayer?.seekTo(targetMs)
                                                                }
                                                            }
                                                        )
                                                    }
                                                    .pointerInput(editState.speedCurvePoints, durationMs) {
                                                        detectDragGestures(
                                                            onDragStart = { offset ->
                                                                val padX = 24.dp.toPx()
                                                                val padY = 16.dp.toPx()
                                                                val graphW = size.width - 2 * padX
                                                                val graphH = size.height - 2 * padY
                                                                val hitThresholdSq = 36.dp.toPx() * 36.dp.toPx()
                                                                val hitIndex = editState.speedCurvePoints.indexOfFirst { pt ->
                                                                    val px = timeToX(pt.timeFraction, padX, padX + graphW)
                                                                    val py = speedToY(pt.speed, padY, padY + graphH)
                                                                    val distSq = (offset.x - px) * (offset.x - px) + (offset.y - py) * (offset.y - py)
                                                                    distSq <= hitThresholdSq
                                                                }
                                                                if (hitIndex != -1) {
                                                                    selectedPointIndex = hitIndex
                                                                }
                                                            },
                                                            onDrag = { change, _ ->
                                                                if (selectedPointIndex in editState.speedCurvePoints.indices) {
                                                                    change.consume()
                                                                    val padX = 24.dp.toPx()
                                                                    val padY = 16.dp.toPx()
                                                                    val graphW = size.width - 2 * padX
                                                                    val graphH = size.height - 2 * padY
                                                                    val currentPoints = editState.speedCurvePoints.toMutableList()
                                                                    val newSpeed = yToSpeed(change.position.y, padY, padY + graphH).coerceIn(0.2f, 5.0f)
                                                                    val newT = when (selectedPointIndex) {
                                                                        0 -> 0.0f
                                                                        currentPoints.size - 1 -> 1.0f
                                                                        else -> {
                                                                            val minT = currentPoints[selectedPointIndex - 1].timeFraction + 0.02f
                                                                            val maxT = currentPoints[selectedPointIndex + 1].timeFraction - 0.02f
                                                                            xToTime(change.position.x, padX, padX + graphW).coerceIn(minT, maxT)
                                                                        }
                                                                    }
                                                                    currentPoints[selectedPointIndex] = SpeedPoint(newT, newSpeed)
                                                                    editState = editState.copy(speedCurvePoints = currentPoints, speedCurvePreset = "Custom")
                                                                    val targetMs = (newT * durationMs).toLong()
                                                                    exoPlayer?.seekTo(targetMs)
                                                                }
                                                            },
                                                            onDragEnd = {},
                                                            onDragCancel = {}
                                                        )
                                                    }
                                            ) {
                                                val padX = 24.dp.toPx()
                                                val padY = 16.dp.toPx()
                                                val graphW = size.width - 2 * padX
                                                val graphH = size.height - 2 * padY
                                                val midY = (padY + padY + graphH) / 2f
                                                val topY = padY
                                                val bottomY = padY + graphH

                                                // Background reference guide lines
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    start = Offset(padX, topY),
                                                    end = Offset(padX + graphW, topY),
                                                    strokeWidth = 1.dp.toPx()
                                                )
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    start = Offset(padX, midY),
                                                    end = Offset(padX + graphW, midY),
                                                    strokeWidth = 1.5.dp.toPx()
                                                )
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    start = Offset(padX, bottomY),
                                                    end = Offset(padX + graphW, bottomY),
                                                    strokeWidth = 1.dp.toPx()
                                                )

                                                // Speed wave path & filled gradient area
                                                val path = Path()
                                                val fillPath = Path()
                                                fillPath.moveTo(padX, bottomY)

                                                val sampleSteps = 80
                                                for (s in 0..sampleSteps) {
                                                    val tFrac = s.toFloat() / sampleSteps.toFloat()
                                                    val spd = getSpeedAtTime(editState.speedCurvePoints, tFrac)
                                                    val x = timeToX(tFrac, padX, padX + graphW)
                                                    val y = speedToY(spd, padY, padY + graphH)
                                                    if (s == 0) {
                                                        path.moveTo(x, y)
                                                        fillPath.lineTo(x, y)
                                                    } else {
                                                        path.lineTo(x, y)
                                                        fillPath.lineTo(x, y)
                                                    }
                                                }
                                                fillPath.lineTo(padX + graphW, bottomY)
                                                fillPath.close()

                                                drawPath(
                                                    path = fillPath,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            primaryColor.copy(alpha = 0.35f),
                                                            primaryColor.copy(alpha = 0.05f)
                                                        ),
                                                        startY = topY,
                                                        endY = bottomY
                                                    )
                                                )
                                                drawPath(
                                                    path = path,
                                                    color = primaryColor,
                                                    style = Stroke(width = 3.dp.toPx())
                                                )

                                                // Live Playhead vertical line
                                                if (durationMs > 0) {
                                                    val playheadFrac = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                                    val playheadX = timeToX(playheadFrac, padX, padX + graphW)
                                                    drawLine(
                                                        color = Color.White,
                                                        start = Offset(playheadX, topY),
                                                        end = Offset(playheadX, bottomY),
                                                        strokeWidth = 2.dp.toPx()
                                                    )
                                                    val curSpd = getSpeedAtTime(editState.speedCurvePoints, playheadFrac)
                                                    val playheadY = speedToY(curSpd, padY, padY + graphH)
                                                    drawCircle(
                                                        color = Color.White,
                                                        radius = 4.dp.toPx(),
                                                        center = Offset(playheadX, playheadY)
                                                    )
                                                }

                                                // Interactive control nodes
                                                editState.speedCurvePoints.forEachIndexed { index, pt ->
                                                    val x = timeToX(pt.timeFraction, padX, padX + graphW)
                                                    val y = speedToY(pt.speed, padY, padY + graphH)
                                                    val isSelected = index == selectedPointIndex
                                                    if (isSelected) {
                                                        drawCircle(
                                                            color = primaryColor.copy(alpha = 0.35f),
                                                            radius = 14.dp.toPx(),
                                                            center = Offset(x, y)
                                                        )
                                                    }
                                                    drawCircle(
                                                        color = if (isSelected) primaryColor else Color.White,
                                                        radius = 7.dp.toPx(),
                                                        center = Offset(x, y)
                                                    )
                                                    drawCircle(
                                                        color = if (isSelected) Color.White else primaryColor,
                                                        radius = 3.5.dp.toPx(),
                                                        center = Offset(x, y)
                                                    )
                                                }
                                            }

                                            // Reference text badges (5.0x Fast, 1.0x Normal, 0.2x Slow)
                                            Text(
                                                text = "5.0x Fast",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(start = 6.dp, top = 2.dp)
                                            )
                                            Text(
                                                text = "1.0x Normal",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .padding(start = 6.dp)
                                            )
                                            Text(
                                                text = "0.2x Slow",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(start = 6.dp, bottom = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Preset Curve Pills
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val presets = listOf(
                                                "Custom" to editState.speedCurvePoints,
                                                "Montage" to listOf(
                                                    SpeedPoint(0.0f, 0.6f),
                                                    SpeedPoint(0.25f, 2.5f),
                                                    SpeedPoint(0.5f, 3.2f),
                                                    SpeedPoint(0.75f, 2.5f),
                                                    SpeedPoint(1.0f, 0.6f)
                                                ),
                                                "Hero" to listOf(
                                                    SpeedPoint(0.0f, 3.5f),
                                                    SpeedPoint(0.28f, 2.0f),
                                                    SpeedPoint(0.5f, 0.3f),
                                                    SpeedPoint(0.72f, 2.0f),
                                                    SpeedPoint(1.0f, 3.5f)
                                                ),
                                                "Bullet" to listOf(
                                                    SpeedPoint(0.0f, 1.0f),
                                                    SpeedPoint(0.35f, 1.0f),
                                                    SpeedPoint(0.5f, 0.2f),
                                                    SpeedPoint(0.65f, 1.0f),
                                                    SpeedPoint(1.0f, 1.0f)
                                                ),
                                                "Flash In" to listOf(
                                                    SpeedPoint(0.0f, 4.0f),
                                                    SpeedPoint(0.2f, 2.0f),
                                                    SpeedPoint(0.45f, 1.0f),
                                                    SpeedPoint(1.0f, 1.0f)
                                                ),
                                                "Flash Out" to listOf(
                                                    SpeedPoint(0.0f, 1.0f),
                                                    SpeedPoint(0.55f, 1.0f),
                                                    SpeedPoint(0.8f, 2.0f),
                                                    SpeedPoint(1.0f, 4.0f)
                                                ),
                                                "Reset" to listOf(
                                                    SpeedPoint(0.0f, 1.0f),
                                                    SpeedPoint(0.25f, 1.0f),
                                                    SpeedPoint(0.5f, 1.0f),
                                                    SpeedPoint(0.75f, 1.0f),
                                                    SpeedPoint(1.0f, 1.0f)
                                                )
                                            )

                                            presets.forEach { (name, pts) ->
                                                FilterChip(
                                                    selected = editState.speedCurvePreset == name,
                                                    onClick = {
                                                        if (name != "Custom") {
                                                            editState = editState.copy(
                                                                speedCurvePreset = name,
                                                                speedCurvePoints = pts
                                                            )
                                                            selectedPointIndex = -1
                                                        }
                                                    },
                                                    label = { Text(name) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            VideoEditorTool.AUDIO -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Volume", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${(editState.volume * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Slider(
                                        value = editState.volume,
                                        onValueChange = { editState = editState.copy(volume = it) },
                                        valueRange = 0f..3f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = editState.volume == 0f,
                                            onClick = { editState = editState.copy(volume = 0f) },
                                            label = { Text("Mute") }
                                        )
                                        FilterChip(
                                            selected = editState.volume == 1f,
                                            onClick = { editState = editState.copy(volume = 1f) },
                                            label = { Text("Normal") }
                                        )
                                        FilterChip(
                                            selected = editState.volume == 2f,
                                            onClick = { editState = editState.copy(volume = 2f) },
                                            label = { Text("Boost (200%)") }
                                        )
                                        FilterChip(
                                            selected = editState.volume == 3f,
                                            onClick = { editState = editState.copy(volume = 3f) },
                                            label = { Text("Max (300%)") }
                                        )
                                    }
                                }
                            }
                            VideoEditorTool.ASPECT_RATIO -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = editState.aspectRatio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val ratios = listOf("Original", "16:9", "9:16", "1:1", "4:3", "21:9")
                                        ratios.forEach { ratio ->
                                            FilterChip(
                                                selected = editState.aspectRatio == ratio,
                                                onClick = { editState = editState.copy(aspectRatio = ratio) },
                                                label = { Text(ratio) }
                                            )
                                        }
                                    }
                                }
                            }
                            VideoEditorTool.ROTATE -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Rotate Video", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val angles = listOf(0, 90, 180, 270)
                                        angles.forEach { angle ->
                                            FilterChip(
                                                selected = editState.rotateConfig == angle,
                                                onClick = { editState = editState.copy(rotateConfig = angle) },
                                                label = { Text(if (angle == 0) "Normal" else "${angle}°") }
                                            )
                                        }
                                    }
                                }
                            }
                            VideoEditorTool.CROP -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Crop Video Presets", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Custom", "None", "16:9", "Fill 16:9", "9:16", "1:1", "4:3", "21:9").forEach { crop ->
                                            FilterChip(
                                                selected = editState.cropRect == crop,
                                                onClick = { 
                                                    if (crop == "Custom") {
                                                        if (editState.cropRight <= editState.cropLeft || editState.cropBottom <= editState.cropTop) {
                                                            editState = editState.copy(
                                                                cropRect = "Custom",
                                                                cropLeft = 0f,
                                                                cropTop = 0f,
                                                                cropRight = 1f,
                                                                cropBottom = 1f
                                                            )
                                                        } else {
                                                            editState = editState.copy(cropRect = "Custom")
                                                        }
                                                    } else {
                                                        editState = editState.copy(cropRect = crop)
                                                    }
                                                },
                                                label = { Text(crop) }
                                            )
                                        }
                                    }
                                }
                            }
                            VideoEditorTool.CAPTIONS -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Burn-in Custom Text", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                        Switch(
                                            checked = editState.hasCaptions,
                                            onCheckedChange = { editState = editState.copy(hasCaptions = it) }
                                        )
                                    }
                                    if (editState.hasCaptions) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = editState.captionText,
                                            onValueChange = { editState = editState.copy(captionText = it) },
                                            label = { Text("Caption Text") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                            VideoEditorTool.JOIN -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Join Videos", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Button(onClick = { joinVideoPickerLauncher.launch(arrayOf("video/*")) }) {
                                            Text(if (editState.joinVideoUri != null) "Change Video" else "Select Video")
                                        }
                                        if (editState.joinVideoUri != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Video selected", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (editState.joinVideoUri != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.RadioButton(selected = !editState.joinAtEnd, onClick = { editState = editState.copy(joinAtEnd = false) })
                                            Text("Add to Beginning")
                                            Spacer(modifier = Modifier.width(16.dp))
                                            androidx.compose.material3.RadioButton(selected = editState.joinAtEnd, onClick = { editState = editState.copy(joinAtEnd = true) })
                                            Text("Add to End")
                                        }
                                    }
                                }
                            }
                            else -> Text("${currentTool.name} options here")
                        }
                    }
                }
            }
        }

        // Export Panel Overlay
        var format by remember { mutableStateOf("mp4") }
        var exportOrientation by remember { mutableStateOf("Auto") }
        var resolutionIndex by remember { mutableIntStateOf(4) } // 0 -> Original, 1 -> 144p, 2 -> 240p, 3 -> 360p, 4 -> 480p, 5 -> 720p, 6 -> 1080p
        var fpsIndex by remember { mutableIntStateOf(1) } // 0 -> 24fps, 1 -> 30fps, 2 -> 60fps
        var quality by remember { mutableFloatStateOf(0.5f) }
        var fastExport by remember { mutableStateOf(false) }

        // Calculate estimated size
        val baseKbps = when (resolutionIndex) {
            0 -> 5000f
            1 -> 100f
            2 -> 250f
            3 -> 500f
            4 -> 1000f
            5 -> 2500f
            else -> 5000f
        }
        val fpsMult = when(fpsIndex) {
            0 -> 0.8f
            1 -> 1.0f
            else -> 1.5f
        }
        val qualityMult = 0.5f + (quality * 1.0f)
        val estimatedKbps = baseKbps * fpsMult * qualityMult
        
        val effectiveStartMs = editState.trimStartMs.coerceAtLeast(0L)
        val effectiveEndMs = if (editState.trimEndMs > 0L) editState.trimEndMs else durationMs
        val trimmedDurationMs = if (editState.isCutMode) {
            durationMs - (effectiveEndMs - effectiveStartMs).coerceAtLeast(0L)
        } else {
            (effectiveEndMs - effectiveStartMs).coerceAtLeast(0L)
        }
        val durationSec = trimmedDurationMs / 1000f
        val estimatedSizeMb = (estimatedKbps * durationSec) / 8192f
        val estimatedSizeStr = String.format(java.util.Locale.US, "%.1f", estimatedSizeMb)

        if (showTimeInputDialog != null) {
            AlertDialog(
                onDismissRequest = { showTimeInputDialog = null },
                title = { Text("Set Time") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = timeInputText,
                        onValueChange = { 
                            if (it.isEmpty() || it.all { char -> char.isDigit() || char == ':' }) {
                                timeInputText = it 
                            }
                        },
                        label = { Text("HH:MM:SS") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parsed = parseTimeInput(timeInputText)
                        if (parsed != null) {
                            val p = parsed.coerceIn(0L, durationMs)
                            editState = when (showTimeInputDialog) {
                                "ds1" -> editState.copy(doubleTrimStart1Ms = p, doubleTrimEnd1Ms = editState.doubleTrimEnd1Ms.coerceAtLeast(p))
                                "de1" -> editState.copy(doubleTrimEnd1Ms = p.coerceAtLeast(editState.doubleTrimStart1Ms))
                                "ds2" -> editState.copy(doubleTrimStart2Ms = p.coerceAtLeast(editState.doubleTrimEnd1Ms), doubleTrimEnd2Ms = editState.doubleTrimEnd2Ms.coerceAtLeast(p.coerceAtLeast(editState.doubleTrimEnd1Ms)))
                                "de2" -> editState.copy(doubleTrimEnd2Ms = p.coerceAtLeast(editState.doubleTrimStart2Ms))
                                "start" -> editState.copy(trimStartMs = p, trimEndMs = editState.trimEndMs.coerceAtLeast(p).takeIf { it > 0 } ?: durationMs)
                                "end" -> editState.copy(trimEndMs = p.coerceAtLeast(editState.trimStartMs))
                                else -> editState
                            }
                            exoPlayer?.seekTo(if (editState.joinVideoUri != null && !editState.joinAtEnd) 1 else 0, p)
                        }
                        showTimeInputDialog = null
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimeInputDialog = null }) { Text("Cancel") }
                }
            )
        }
        
        if (showExportPanel) {
            AlertDialog(
                onDismissRequest = { showExportPanel = false },
                title = { Text("Export & Quality Control") },
                text = {
                    Column {
                        Text("Estimated file size: ~$estimatedSizeStr MB", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Resolution")
                        Slider(
                            value = resolutionIndex.toFloat(),
                            onValueChange = { resolutionIndex = it.toInt() },
                            valueRange = 0f..6f,
                            steps = 5
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Og", style = if (resolutionIndex == 0) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("144p", style = if (resolutionIndex == 1) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("240p", style = if (resolutionIndex == 2) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("360p", style = if (resolutionIndex == 3) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("480p", style = if (resolutionIndex == 4) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("720p", style = if (resolutionIndex == 5) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("1080p", style = if (resolutionIndex == 6) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Frame Rate")
                        Slider(
                            value = fpsIndex.toFloat(),
                            onValueChange = { fpsIndex = it.toInt() },
                            valueRange = 0f..2f,
                            steps = 1
                        )
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("24 fps", style = if (fpsIndex == 0) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("30 fps", style = if (fpsIndex == 1) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                            Text("60 fps", style = if (fpsIndex == 2) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Quality")
                        Slider(value = quality, onValueChange = { quality = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fastExport, onCheckedChange = { fastExport = it })
                            Text("Fast Export (ultrafast preset)")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Export Orientation")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilterChip(selected = exportOrientation == "Auto", onClick = { exportOrientation = "Auto" }, label= { Text("Auto")})
                            FilterChip(selected = exportOrientation == "Portrait", onClick = { exportOrientation = "Portrait" }, label= { Text("Portrait")})
                            FilterChip(selected = exportOrientation == "Landscape", onClick = { exportOrientation = "Landscape" }, label= { Text("Landscape")})
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Converter Format")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            FilterChip(selected = format == "mp4", onClick = { format = "mp4" }, label= { Text("mp4")})
                            FilterChip(selected = format == "mp3", onClick = { format = "mp3" }, label= { Text("mp3")})
                            FilterChip(selected = format == "gif", onClick = { format = "gif" }, label= { Text("gif")})
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showExportPanel = false
                        
                        // 1. Determine parameters
                        val res = when (resolutionIndex) {
                            0 -> "Original"
                            1 -> "256x144"
                            2 -> "426x240"
                            3 -> "640x360"
                            4 -> "854x480"
                            5 -> "1280x720"
                            else -> "1920x1080"
                        }
                        val fps = when (fpsIndex) {
                            0 -> "24"
                            1 -> "30"
                            else -> "60"
                        }
                        val crf = (35 - (quality * 17)).toInt()
                        
                        var joinPath: String? = null
                        if (editState.joinVideoUri != null) {
                            try {
                                val u = android.net.Uri.parse(editState.joinVideoUri!!)
                                val tempFile = java.io.File(context.cacheDir, "join_${System.currentTimeMillis()}.mp4")
                                sessionTempFiles.add(tempFile)
                                context.contentResolver.openInputStream(u)?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                joinPath = tempFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        val originalW = videoWidth
                        val originalH = videoHeight
                        val rotatedW = if (editState.rotateConfig == 90 || editState.rotateConfig == 270) originalH else originalW
                        val rotatedH = if (editState.rotateConfig == 90 || editState.rotateConfig == 270) originalW else originalH
                        
                        val isPortraitFinal = when (exportOrientation) {
                            "Portrait" -> true
                            "Landscape" -> false
                            else -> when {
                                editState.aspectRatio == "9:16" -> true
                                editState.aspectRatio == "16:9" -> false
                                editState.aspectRatio == "4:3" -> false
                                editState.aspectRatio == "21:9" -> false
                                editState.aspectRatio == "1:1" -> false
                                editState.cropRect == "9:16" -> true
                                editState.cropRect == "16:9" -> false
                                editState.cropRect == "Fill 16:9" -> false
                                editState.cropRect == "4:3" -> false
                                editState.cropRect == "21:9" -> false
                                editState.cropRect == "1:1" -> false
                                editState.cropRect == "Custom" -> {
                                    val cw = rotatedW * (editState.cropRight - editState.cropLeft)
                                    val ch = rotatedH * (editState.cropBottom - editState.cropTop)
                                    ch > cw
                                }
                                editState.cropRect == "Center Crop" -> false
                                else -> rotatedH > rotatedW
                            }
                        }
                        
                        var globalTargetW = 1280
                        var globalTargetH = 720
                        if (res != "Original") {
                            val parts = res.split("x")
                            globalTargetW = parts[0].toInt()
                            globalTargetH = parts[1].toInt()
                        } else {
                            globalTargetW = if (isPortraitFinal) 720 else 1280
                            globalTargetH = if (isPortraitFinal) 1280 else 720
                        }
                        if (isPortraitFinal && res != "Original") {
                            val temp = globalTargetW
                            globalTargetW = globalTargetH
                            globalTargetH = temp
                        }

                        // 2. Build FFmpeg command template based on edits
                        val filterList = mutableListOf<String>()
                        val audioFilterList = mutableListOf<String>()
                        var trimArgs = ""

                        if (editState.isDoubleTrim) {
                            // Double Trim (Keep 2 Parts) mode
                            val ds1 = editState.doubleTrimStart1Ms.coerceIn(0L, durationMs)
                            val de1 = editState.doubleTrimEnd1Ms.coerceIn(ds1, durationMs).takeIf { it > 0 } ?: (durationMs / 2)
                            val ds2 = editState.doubleTrimStart2Ms.coerceIn(de1, durationMs)
                            val de2 = editState.doubleTrimEnd2Ms.coerceIn(ds2, durationMs).takeIf { it > 0 } ?: durationMs
                            
                            val start1S = ds1 / 1000f
                            val end1S = de1 / 1000f
                            val start2S = ds2 / 1000f
                            val end2S = de2 / 1000f
                            filterList.add("select='between(t,$start1S,$end1S)+between(t,$start2S,$end2S)'")
                            filterList.add("setpts=N/FRAME_RATE/TB")
                            audioFilterList.add("aselect='between(t,$start1S,$end1S)+between(t,$start2S,$end2S)'")
                            audioFilterList.add("asetpts=N/SR/TB")
                        } else if (editState.isCutMode) {
                            // Cut (Remove Middle) mode
                            val start = editState.trimStartMs.coerceIn(0L, durationMs)
                            val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                            val startS = start / 1000f
                            val endS = end / 1000f
                            filterList.add("select='not(between(t,$startS,$endS))'")
                            filterList.add("setpts=N/FRAME_RATE/TB")
                            audioFilterList.add("aselect='not(between(t,$startS,$endS))'")
                            audioFilterList.add("asetpts=N/SR/TB")
                        } else {
                            // Trim (Keep Middle) mode
                            val start = editState.trimStartMs.coerceIn(0L, durationMs)
                            val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                            trimArgs = "-ss ${start / 1000f} -to ${end / 1000f}"
                        }

                        val isSpeedCurveActive = editState.speedMode == "Curve" && editState.speedCurvePoints.any { Math.abs(it.speed - 1.0f) > 0.05f }
                        if (!isSpeedCurveActive) {
                            if (editState.speedMode == "Standard" && editState.speed != 1.0f) {
                                filterList.add("setpts=PTS/${editState.speed}")
                                if (videoHasAudio) {
                                    audioFilterList.add(buildAtempoFilter(editState.speed))
                                }
                            }
                        }
                        if (editState.volume != 1.0f) {
                            audioFilterList.add("volume=${editState.volume}")
                        }

                        if (editState.rotateConfig != 0) {
                            val rotFilter = when (editState.rotateConfig) {
                                90 -> "transpose=1"
                                180 -> "transpose=2,transpose=2"
                                270 -> "transpose=2"
                                else -> ""
                            }
                            if (rotFilter.isNotEmpty()) filterList.add(rotFilter)
                        }
                        if (editState.cropRect != "None" && editState.cropRect.isNotBlank()) {
                            if (editState.cropRect == "Center Crop") {
                                filterList.add("crop='min(iw,ih)':'min(iw,ih)'")
                            } else if (editState.cropRect == "Custom") {
                                val cw = "iw*${editState.cropRight - editState.cropLeft}"
                                val ch = "ih*${editState.cropBottom - editState.cropTop}"
                                val cx = "iw*${editState.cropLeft}"
                                val cy = "ih*${editState.cropTop}"
                                filterList.add("crop=$cw:$ch:$cx:$cy")
                            } else {
                                val cropFilter = when (editState.cropRect) {
                                    "16:9", "Fill 16:9" -> "crop=w='min(iw,ih*16/9)':h='min(ih,iw*9/16)'"
                                    "9:16" -> "crop=w='min(iw,ih*9/16)':h='min(ih,iw*16/9)'"
                                    "1:1" -> "crop=w='min(iw,ih)':h='min(ih,iw)'"
                                    "4:3" -> "crop=w='min(iw,ih*4/3)':h='min(ih,iw*3/4)'"
                                    "21:9" -> "crop=w='min(iw,ih*21/9)':h='min(ih,iw*9/21)'"
                                    else -> ""
                                }
                                if (cropFilter.isNotEmpty()) filterList.add(cropFilter)
                            }
                        }
                        if (editState.aspectRatio != "Original") {
                            val scaleFilter = when (editState.aspectRatio) {
                                "16:9" -> "scale=w=max(iw\\,ih*16/9):h=max(ih\\,iw*9/16):force_original_aspect_ratio=increase,crop=w='min(iw,ih*16/9)':h='min(ih,iw*9/16)'"
                                "9:16" -> "scale=w=max(iw\\,ih*9/16):h=max(ih\\,iw*16/9):force_original_aspect_ratio=increase,crop=w='min(iw,ih*9/16)':h='min(ih,iw*16/9)'"
                                "1:1" -> "scale=w=max(iw\\,ih):h=max(ih\\,iw):force_original_aspect_ratio=increase,crop=w='min(iw,ih)':h='min(ih,iw)'"
                                "4:3" -> "scale=w=max(iw\\,ih*4/3):h=max(ih\\,iw*3/4):force_original_aspect_ratio=increase,crop=w='min(iw,ih*4/3)':h='min(ih,iw*3/4)'"
                                "21:9" -> "scale=w=max(iw\\,ih*21/9):h=max(ih\\,iw*9/21):force_original_aspect_ratio=increase,crop=w='min(iw,ih*21/9)':h='min(ih,iw*9/21)'"
                                else -> ""
                            }
                            // Actually, if we just want to stretch/squeeze, we can use setdar or scale. 
                            // `scale=w=xxx:h=yyy` forces stretch without crop. But we need a specific output ratio.
                            // Let's do `scale=iw:iw*9/16` for 16:9? No, it should just be setdar=16/9 or scale with setsar
                            val stretchFilter = when (editState.aspectRatio) {
                                "16:9" -> "scale=w=max(iw\\,ih*16/9):h=max(ih\\,iw*9/16),setsar=1"
                                "9:16" -> "scale=w=max(iw\\,ih*9/16):h=max(ih\\,iw*16/9),setsar=1"
                                "1:1" -> "scale=w=max(iw\\,ih):h=max(iw\\,ih),setsar=1"
                                "4:3" -> "scale=w=max(iw\\,ih*4/3):h=max(ih\\,iw*3/4),setsar=1"
                                "21:9" -> "scale=w=max(iw\\,ih*21/9):h=max(ih\\,iw*9/21),setsar=1"
                                else -> ""
                            }
                            if (stretchFilter.isNotEmpty()) filterList.add(stretchFilter)
                        }
                        if (editState.hasCaptions && editState.captionText.isNotBlank()) {
                            val safeText = editState.captionText.replace("'", "")
                            filterList.add("drawtext=text='$safeText':fontcolor=white:fontsize=48:x=(w-text_w)/2:y=h-th-50:fontfile=/system/fonts/Roboto-Regular.ttf")
                        }
                        
                        if (res != "Original") {
                            filterList.add("scale=w=$globalTargetW:h=$globalTargetH:force_original_aspect_ratio=decrease:flags=lanczos")
                        }
                        
                        // Always ensure even dimensions for libx264 compatibility
                        filterList.add("scale=trunc(iw/2)*2:trunc(ih/2)*2")
                        
                        val videoFilterArgs = if (filterList.isNotEmpty()) {
                            "-vf \"${filterList.joinToString(",")}\""
                        } else {
                            ""
                        }
                        
                        val audioFilterArgs = if (audioFilterList.isNotEmpty()) {
                            "-af \"${audioFilterList.joinToString(",")}\""
                        } else {
                            ""
                        }
                        
                        // GIF Filters
                        val gifFilters = mutableListOf<String>()
                        if (filterList.isNotEmpty()) {
                            gifFilters.addAll(filterList)
                        }
                        if (res == "Original") {
                            gifFilters.add("fps=$fps,scale=-2:480:flags=lanczos")
                        } else {
                            gifFilters.add("fps=$fps")
                        }
                        val gifFilterArgs = "-vf \"${gifFilters.joinToString(",")}\""

                        val presetArg = if (fastExport) "ultrafast" else "medium"

                        var cmd = ""
                        if (isSpeedCurveActive && joinPath == null) {
                            val activeDurationSec = if (!editState.isDoubleTrim && !editState.isCutMode) {
                                val start = editState.trimStartMs.coerceIn(0L, durationMs)
                                val end = editState.trimEndMs.coerceIn(start, durationMs).takeIf { it > 0 } ?: durationMs
                                (end - start).coerceAtLeast(100L) / 1000f
                            } else {
                                (if (durationMs > 0L) durationMs else 1000L) / 1000f
                            }

                            val numSegments = 10
                            val sb = StringBuilder()
                            val hasAudio = videoHasAudio && format != "gif"

                            for (k in 0 until numSegments) {
                                val fStart = k.toFloat() / numSegments.toFloat()
                                val fEnd = (k + 1).toFloat() / numSegments.toFloat()
                                val sStart = fStart * activeDurationSec
                                val sEnd = fEnd * activeDurationSec
                                val fMid = (fStart + fEnd) / 2.0f
                                val spd = getSpeedAtTime(editState.speedCurvePoints, fMid).coerceIn(0.2f, 5.0f)

                                sb.append(String.format(java.util.Locale.US, "[0:v]trim=start=%.3f:end=%.3f,setpts=PTS-STARTPTS,setpts=PTS/%.3f[v_seg%d];", sStart, sEnd, spd, k))
                                if (hasAudio) {
                                    sb.append(String.format(java.util.Locale.US, "[0:a]atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS,%s[a_seg%d];", sStart, sEnd, buildAtempoFilter(spd), k))
                                }
                            }

                            if (hasAudio) {
                                val concatInputs = (0 until numSegments).joinToString("") { "[v_seg$it][a_seg$it]" }
                                sb.append("${concatInputs}concat=n=$numSegments:v=1:a=1[v_ramped][a_ramped];")
                            } else {
                                val concatInputs = (0 until numSegments).joinToString("") { "[v_seg$it]" }
                                sb.append("${concatInputs}concat=n=$numSegments:v=1:a=0[v_ramped];")
                            }

                            val vFilters = if (format == "gif") gifFilters else filterList
                            if (vFilters.isNotEmpty()) {
                                sb.append("[v_ramped]${vFilters.joinToString(",")}[v_out];")
                            } else {
                                sb.append("[v_ramped]null[v_out];")
                            }

                            if (hasAudio) {
                                if (audioFilterList.isNotEmpty()) {
                                    sb.append("[a_ramped]${audioFilterList.joinToString(",")}[a_out]")
                                } else {
                                    sb.append("[a_ramped]anull[a_out]")
                                }
                            }

                            val curveFilterComplex = sb.toString().trimEnd(';')

                            cmd = when (format) {
                                "mp4" -> {
                                    if (hasAudio) {
                                        "-y $trimArgs -i %INPUT% -filter_complex \"$curveFilterComplex\" -map \"[v_out]\" -map \"[a_out]\" -r $fps -vcodec libx264 -crf $crf -preset $presetArg -metadata:s:v:0 rotate=0 %OUTPUT%"
                                    } else {
                                        "-y $trimArgs -i %INPUT% -filter_complex \"$curveFilterComplex\" -map \"[v_out]\" -r $fps -vcodec libx264 -crf $crf -preset $presetArg -metadata:s:v:0 rotate=0 %OUTPUT%"
                                    }
                                }
                                "mp3" -> {
                                    if (hasAudio) {
                                        "-y $trimArgs -i %INPUT% -filter_complex \"$curveFilterComplex\" -vn -map \"[a_out]\" -acodec libmp3lame -q:a 2 %OUTPUT%"
                                    } else {
                                        "-y $trimArgs -i %INPUT% -vn -acodec libmp3lame -q:a 2 %OUTPUT%"
                                    }
                                }
                                "gif" -> "-y $trimArgs -i %INPUT% -filter_complex \"$curveFilterComplex\" -map \"[v_out]\" -loop 0 %OUTPUT%"
                                else -> "-y -i %INPUT% %OUTPUT%"
                            }
                        } else if (joinPath != null && format == "mp4") {
                            // Complex filter for joining
                            val v0 = if (filterList.isNotEmpty()) "[0:v]${filterList.joinToString(",")}[v0];" else "[0:v]copy[v0];"
                            val a0 = if (audioFilterList.isNotEmpty()) "[0:a]${audioFilterList.joinToString(",")}[a0];" else "[0:a]anull[a0];"
                            
                            // For join video, we scale it to match the target
                            val fw = globalTargetW
                            val fh = globalTargetH
                            
                            val v1 = "[1:v]scale=w=$fw:h=$fh:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1[v1];"
                            val a1 = "[1:a]anull[a1];"
                            
                            val concat = if (editState.joinAtEnd) {
                                "[v0][a0][v1][a1]concat=n=2:v=1:a=1[v][a]"
                            } else {
                                "[v1][a1][v0][a0]concat=n=2:v=1:a=1[v][a]"
                            }
                            
                            // If res is original we still need a common scale to avoid concat errors
                            val v0Safe = if (filterList.isNotEmpty()) {
                                "[0:v]${filterList.joinToString(",")},scale=w=$fw:h=$fh:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1[v0];"
                            } else {
                                "[0:v]scale=w=$fw:h=$fh:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1[v0];"
                            }
                            val safeFilterComplex = "$v0Safe$a0$v1$a1$concat"
                            
                            cmd = "-y $trimArgs -i %INPUT% -i '$joinPath' -filter_complex \"$safeFilterComplex\" -map \"[v]\" -map \"[a]\" -r $fps -vcodec libx264 -crf $crf -preset $presetArg -metadata:s:v:0 rotate=0 %OUTPUT%"
                        } else {
                            cmd = when (format) {
                                "mp4" -> "-y $trimArgs -i %INPUT% $videoFilterArgs $audioFilterArgs -r $fps -vcodec libx264 -crf $crf -preset $presetArg -metadata:s:v:0 rotate=0 %OUTPUT%"
                                "mp3" -> "-y $trimArgs -i %INPUT% -vn $audioFilterArgs -acodec libmp3lame -q:a 2 %OUTPUT%"
                                "gif" -> "-y $trimArgs -i %INPUT% $gifFilterArgs -loop 0 %OUTPUT%"
                                else -> "-y -i %INPUT% %OUTPUT%"
                            }
                        }
                        
                        LogKeeper.log("Starting Render job for video file. Output Format: $format, Resolution: $res, FPS: $fps, Preset: $presetArg, Quality level: $quality (CRF $crf)", "VideoEditor")
                        LogKeeper.log("Constructed FFmpeg Command: $cmd", "VideoEditor")
                        
                        // Resource Exclusivity: Pause editor preview player while FFmpeg is encoding
                        try {
                            exoPlayer?.pause()
                        } catch (e: Exception) {}

                        // 3. Start FFmpegService
                        val intent = android.content.Intent(context, com.example.service.FFmpegService::class.java).apply {
                            putStringArrayListExtra("uris", arrayListOf(effectiveUri))
                            putStringArrayListExtra("original_names", arrayListOf(com.example.ui.screens.getDisplayNameFromUri(context, initialUri)))
                            putExtra("commandTemplate", cmd)
                            putExtra("outputExt", format)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }) {
                        Text("SAVE (Render)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportPanel = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (exportedPreviewUri != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { exportedPreviewUri = null }) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Export Complete!", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        // Mini player for portrait
                        Box(modifier = Modifier.width(200.dp).height(300.dp).background(androidx.compose.ui.graphics.Color.Black)) {
                            var previewPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
                            DisposableEffect(exportedPreviewUri) {
                                val p = androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
                                p.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(exportedPreviewUri!!)))
                                p.prepare()
                                p.playWhenReady = true
                                p.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                                previewPlayer = p
                                onDispose { p.release() }
                            }
                            if (previewPlayer != null) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        androidx.media3.ui.PlayerView(ctx).apply {
                                            player = previewPlayer
                                            useController = true
                                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        val outputFileSizeStr = remember(exportedPreviewUri) {
                            if (exportedPreviewUri == null) ""
                            else {
                                try {
                                    val u = Uri.parse(exportedPreviewUri)
                                    var sizeBytes = 0L
                                    if (u.scheme == "file") {
                                        val f = java.io.File(u.path ?: "")
                                        if (f.exists()) sizeBytes = f.length()
                                    } else {
                                        context.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                                            sizeBytes = pfd.statSize
                                        }
                                        if (sizeBytes <= 0L) {
                                            val cursor = context.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                                            cursor?.use {
                                                if (it.moveToFirst()) {
                                                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                                    if (sizeIndex != -1) sizeBytes = it.getLong(sizeIndex)
                                                }
                                            }
                                        }
                                    }
                                    if (sizeBytes > 0L) {
                                        android.text.format.Formatter.formatFileSize(context, sizeBytes)
                                    } else ""
                                } catch (e: Exception) {
                                    ""
                                }
                            }
                        }

                        if (outputFileSizeStr.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Size: $outputFileSizeStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val newUri = exportedPreviewUri
                            exportedPreviewUri = null
                            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                                action = "edit"
                                setDataAndType(Uri.parse(newUri), "video/*")
                            }
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Edit Finished File")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { exportedPreviewUri = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMsScaled(ms: Long, speed: Float): String {
    val scaledMs = if (speed > 0f) (ms / speed).toLong() else ms
    return formatMs(scaledMs)
}

private fun formatMs(ms: Long): String {
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

private fun formatTimeInput(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

private fun parseTimeInput(text: String): Long? {
    try {
        val parts = text.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val s = parts[2].toLong()
                (h * 3600 + m * 60 + s) * 1000
            }
            2 -> {
                val m = parts[0].toLong()
                val s = parts[1].toLong()
                (m * 60 + s) * 1000
            }
            1 -> {
                val s = parts[0].toLong()
                s * 1000
            }
            else -> null
        }
    } catch (e: Exception) {
        return null
    }
}
