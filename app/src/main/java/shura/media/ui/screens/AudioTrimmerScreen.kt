package shura.media.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import shura.media.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerScreen(
    uriString: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uri = Uri.parse(uriString)
    val coroutineScope = rememberCoroutineScope()
    
    // We would extract the actual duration, for now we will assume 180 seconds or mock it.
    // In a real app we'd load the metadata.
    var durationMs by remember { mutableLongStateOf(0L) }
    var durationLoaded by remember { mutableStateOf(false) }
    
    LaunchedEffect(uri) {
        val extractedDuration = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                time?.toLongOrNull() ?: 180000L
            } catch (e: Exception) {
                180000L
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        durationMs = extractedDuration
        durationLoaded = true
    }
    
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(durationMs) }
    
    LaunchedEffect(durationLoaded) {
        if (durationLoaded) {
            if (endMs == 0L || endMs > durationMs) {
                endMs = durationMs
            }
        }
    }

    var isExporting by remember { mutableStateOf(false) }
    var activeExportSessionId by remember { mutableStateOf<Long?>(null) }
    var denoiseLevel by remember { mutableStateOf("OFF") }
    
    val player = remember(context) { 
        ExoPlayer.Builder(context).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        androidx.media3.common.Player.STATE_IDLE -> "STATE_IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "STATE_BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "STATE_READY"
                        androidx.media3.common.Player.STATE_ENDED -> "STATE_ENDED"
                        else -> "UNKNOWN"
                    }
                    shura.media.LogKeeper.log("AudioTrimmerPlayer state: $stateName", "AudioTrimmer")
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    shura.media.LogKeeper.logError("AudioTrimmer", "Player error: ${error.message}", error)
                }
            })
        }
    }
    DisposableEffect(player) {
        // True Single Engine: Fully stop background playback and release shared PlayerManager engine
        shura.media.service.PlaybackService.stopPlaybackAndRelease(context)
        onDispose {
            activeExportSessionId?.let { id ->
                try { FFmpegKit.cancel(id) } catch (_: Exception) {}
            }
            activeExportSessionId = null
            player.release()
        }
    }
    
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackMs by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(isPlaying, endMs) {
        if (isPlaying) {
            while (isActive) {
                currentPlaybackMs = player.currentPosition
                if (player.currentPosition >= endMs) {
                    player.pause()
                    isPlaying = false
                    currentPlaybackMs = startMs
                    break
                }
                kotlinx.coroutines.delay(50)
            }
        } else {
            currentPlaybackMs = startMs
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim Audio") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.AutoMirrored.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (endMs > startMs) {
                                isExporting = true
                                coroutineScope.launch {
                                    val result = exportTrimmedAudio(
                                        context = context,
                                        inputUri = uri,
                                        startMs = startMs,
                                        endMs = endMs,
                                        denoiseLevel = denoiseLevel,
                                        onSessionCreated = { sessionId ->
                                            activeExportSessionId = sessionId
                                        }
                                    )
                                    isExporting = false
                                    if (result.success) {
                                        val msg = if (result.fellBackToOutputFolder) {
                                            "Saved to Output Folder (original folder protected by Android)"
                                        } else {
                                            "Trimmed audio saved successfully"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    } else {
                                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Invalid range", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Icon(AppIcons.Save, contentDescription = "Save Trim")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (!durationLoaded) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Timeline", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Plain Clean Timeline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        // Drawing simple waveform or lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val startX = (startMs.toFloat() / durationMs) * w
                            val endX = (endMs.toFloat() / durationMs) * w
                            
                            // Draw base timeline
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = Offset(0f, h/2),
                                end = Offset(w, h/2),
                                strokeWidth = 4.dp.toPx()
                            )
                            
                            // Draw selected region
                            drawRect(
                                color = Color(0xFF2196F3).copy(alpha = 0.3f),
                                topLeft = Offset(startX, 0f),
                                size = androidx.compose.ui.geometry.Size(endX - startX, h)
                            )
                            
                            // Draw active timeline part
                            drawLine(
                                color = Color(0xFF2196F3),
                                start = Offset(startX, h/2),
                                end = Offset(endX, h/2),
                                strokeWidth = 6.dp.toPx()
                            )

                            // Start and End markers
                            drawLine(color = Color.White, start = Offset(startX, 0f), end = Offset(startX, h), strokeWidth = 2.dp.toPx())
                            drawLine(color = Color.White, start = Offset(endX, 0f), end = Offset(endX, h), strokeWidth = 2.dp.toPx())
                            
                            // Current Playback marker
                            if (isPlaying || currentPlaybackMs > startMs) {
                                val currentX = (currentPlaybackMs.toFloat() / durationMs.coerceAtLeast(1L)) * w
                                drawLine(color = Color.Red, start = Offset(currentX, 0f), end = Offset(currentX, h), strokeWidth = 2.dp.toPx())
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Selection: ${formatTrimmerTime(startMs)} - ${formatTrimmerTime(endMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Preview playback
                    IconButton(
                        onClick = {
                            shura.media.LogKeeper.log("Play button clicked. isPlaying -> ${!isPlaying}. startMs=$startMs, endMs=$endMs, currentPos=${player.currentPosition}", "AudioTrimmer")
                            if (isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.seekTo(startMs)
                                player.play()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = "Play/Pause preview",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Dials
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DialControl(
                            label = "Start",
                            value = startMs.toFloat(),
                            maxValue = durationMs.toFloat(),
                            onValueChange = { newVal -> 
                                if (newVal < endMs) startMs = newVal.toLong() 
                            }
                        )
                        DialControl(
                            label = "End",
                            value = endMs.toFloat(),
                            maxValue = durationMs.toFloat(),
                            onValueChange = { newVal -> 
                                if (newVal > startMs) endMs = newVal.toLong() 
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- Noise Reduction / Voice Clarity ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Reduce Noise (Voice Clarity)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (denoiseLevel) {
                                    "LIGHT" -> "Light (12dB)"
                                    "MEDIUM" -> "Medium (18dB)"
                                    "AGGRESSIVE" -> "Strong (24dB)"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (denoiseLevel != "OFF") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = denoiseLevel == "OFF",
                                onClick = { denoiseLevel = "OFF" },
                                label = { Text("Off") }
                            )
                            FilterChip(
                                selected = denoiseLevel == "LIGHT",
                                onClick = { denoiseLevel = "LIGHT" },
                                label = { Text("Light") }
                            )
                            FilterChip(
                                selected = denoiseLevel == "MEDIUM",
                                onClick = { denoiseLevel = "MEDIUM" },
                                label = { Text("Medium") }
                            )
                            FilterChip(
                                selected = denoiseLevel == "AGGRESSIVE",
                                onClick = { denoiseLevel = "AGGRESSIVE" },
                                label = { Text("Strong") }
                            )
                        }
                    }
                }
            }
            
            if (isExporting) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(modifier = Modifier.padding(32.dp)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Exporting...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DialControl(
    label: String,
    value: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit
) {
    // Current angle based on value (0 to 360)
    val angle = (value / maxValue.coerceAtLeast(1f)) * 360f
    
    var center by remember { mutableStateOf(Offset.Zero) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    var lastAngle = 0f
                    var tempValue = value
                    detectDragGestures(
                        onDragStart = { offset ->
                            center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            lastAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            tempValue = value
                        },
                        onDrag = { change, _ ->
                            val dragOffset = change.position
                            val dx = dragOffset.x - center.x
                            val dy = dragOffset.y - center.y
                            val currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            
                            var delta = currentAngle - lastAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            
                            lastAngle = currentAngle
                            
                            // Decrease sensitivity (e.g. 50x harder to turn)
                            val valueDelta = (delta * 0.02f / 360f) * maxValue
                            tempValue = (tempValue + valueDelta).coerceIn(0f, maxValue)
                            onValueChange(tempValue)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.width / 2f * 0.8f
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                
                // Draw Dial track
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.3f),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 8.dp.toPx())
                )
                
                // Draw Dial indicator
                val indicatorAngleRad = Math.toRadians((angle - 90f).toDouble())
                val endX = centerX + radius * cos(indicatorAngleRad).toFloat()
                val endY = centerY + radius * sin(indicatorAngleRad).toFloat()
                
                drawLine(
                    color = Color(0xFF2196F3),
                    start = Offset(centerX, centerY),
                    end = Offset(endX, endY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Center knob center hole
                drawCircle(
                    color = Color(0xFF2196F3),
                    radius = 8.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        
        var showEditDialog by remember { mutableStateOf(false) }
        Text(
            text = formatTrimmerTime(value.toLong()), 
            style = MaterialTheme.typography.bodyMedium, 
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { showEditDialog = true }
                .padding(8.dp)
        )
        
        if (showEditDialog) {
            var textValue by remember { mutableStateOf(formatTrimmerTime(value.toLong())) }
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit $label") },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        label = { Text("Time (HH:MM:SS.MMM)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val ms = parseTrimmerTime(textValue)
                        if (ms != null) {
                            onValueChange(ms.toFloat().coerceIn(0f, maxValue))
                        }
                        showEditDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

private fun parseTrimmerTime(timeStr: String): Long? {
    try {
        val parts = timeStr.trim().split(":", ".")
        if (parts.isEmpty()) return null
        var ms = 0L
        if (timeStr.contains(".")) {
            ms = parts.last().padEnd(3, '0').substring(0, 3).toLong()
        }
        
        val timeParts = timeStr.substringBefore(".").split(":")
        var seconds = 0L
        var minutes = 0L
        var hours = 0L
        
        if (timeParts.size == 1) {
            seconds = timeParts[0].toLong()
        } else if (timeParts.size == 2) {
            minutes = timeParts[0].toLong()
            seconds = timeParts[1].toLong()
        } else if (timeParts.size >= 3) {
            hours = timeParts[0].toLong()
            minutes = timeParts[1].toLong()
            seconds = timeParts[2].toLong()
        }
        
        return hours * 3600000 + minutes * 60000 + seconds * 1000 + ms
    } catch (e: Exception) {
        return null
    }
}

private fun formatTrimmerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return if (hours > 0) {
        String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    } else {
        String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
}

private suspend fun runCancellableFFmpeg(
    args: Array<String>,
    onSessionCreated: (Long?) -> Unit
): com.arthenica.ffmpegkit.FFmpegSession = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    val session = FFmpegKit.executeWithArgumentsAsync(args) { completedSession ->
        if (cont.isActive) {
            cont.resume(completedSession)
        }
    }
    onSessionCreated(session.sessionId)
    cont.invokeOnCancellation {
        try { FFmpegKit.cancel(session.sessionId) } catch (_: Exception) {}
    }
}

private data class AudioExportResult(
    val success: Boolean,
    val fellBackToOutputFolder: Boolean = false
)

// FFmpegKit Audio Trim + Optional afftdn Denoise Pipeline
private suspend fun exportTrimmedAudio(
    context: Context,
    inputUri: Uri,
    startMs: Long,
    endMs: Long,
    denoiseLevel: String,
    onSessionCreated: (Long?) -> Unit
): AudioExportResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val uriStr = inputUri.toString()
    val isFileUri = inputUri.scheme == "file" || uriStr.startsWith("file:") || uriStr.startsWith("/")
    val localCandidate = if (isFileUri && !shura.media.data.MediaUriResolver.isAppPrivatePath(context, uriStr, allowApprovedCacheTemp = true)) {
        val directPath = when {
            uriStr.startsWith("file://") -> uriStr.removePrefix("file://")
            uriStr.startsWith("file:/") -> uriStr.removePrefix("file:")
            uriStr.startsWith("file:") -> uriStr.removePrefix("file:")
            else -> inputUri.path ?: uriStr
        }
        listOfNotNull(
            java.io.File(directPath),
            java.io.File(Uri.decode(directPath)),
            inputUri.path?.let { java.io.File(it) },
            inputUri.path?.let { java.io.File(Uri.decode(it)) }
        ).firstOrNull {
            it.exists() && it.canRead() && !shura.media.data.MediaUriResolver.isAppPrivatePath(
                context,
                it.absolutePath,
                allowApprovedCacheTemp = true
            )
        }
    } else null

    val tempInputToDelete: java.io.File? = if (localCandidate == null) {
        java.io.File(context.cacheDir, "Temp_Input_${System.currentTimeMillis()}.tmp")
    } else null
    val tempOutput = java.io.File(context.cacheDir, "Temp_Trim_${System.currentTimeMillis()}.m4a")
    var success = false
    var fellBackToOutputFolder = false

    try {
        val actualInputFile = if (localCandidate != null) {
            localCandidate
        } else {
            val tmpIn = tempInputToDelete!!
            shura.media.data.MediaUriResolver.openInputStreamSafely(context, uriStr).use { input ->
                java.io.FileOutputStream(tmpIn).use { output ->
                    input.copyTo(output)
                }
            }
            tmpIn
        }

        if (actualInputFile.exists() && actualInputFile.length() > 0L) {
            val startS = String.format(java.util.Locale.US, "%.3f", startMs / 1000.0)
            val endS = String.format(java.util.Locale.US, "%.3f", endMs / 1000.0)

            var session: com.arthenica.ffmpegkit.FFmpegSession? = null
            if (denoiseLevel == "OFF") {
                val extLower = uriStr.substringAfterLast('.', "").substringBefore('?').lowercase()
                if (extLower in setOf("m4a", "aac", "mp4")) {
                    val copySession = runCancellableFFmpeg(
                        arrayOf(
                            "-y",
                            "-ss", startS,
                            "-to", endS,
                            "-i", actualInputFile.absolutePath,
                            "-vn",
                            "-c:a", "copy",
                            "-movflags", "+faststart",
                            tempOutput.absolutePath
                        ),
                        onSessionCreated
                    )
                    if (ReturnCode.isSuccess(copySession.returnCode) && tempOutput.exists() && tempOutput.length() > 0L) {
                        session = copySession
                    } else if (tempOutput.exists()) {
                        tempOutput.delete()
                    }
                }
                if (session == null) {
                    session = runCancellableFFmpeg(
                        arrayOf(
                            "-y",
                            "-ss", startS,
                            "-to", endS,
                            "-i", actualInputFile.absolutePath,
                            "-vn",
                            "-c:a", "aac",
                            "-b:a", "192k",
                            "-movflags", "+faststart",
                            tempOutput.absolutePath
                        ),
                        onSessionCreated
                    )
                }
            } else {
                val denoiseFilter = when (denoiseLevel) {
                    "LIGHT" -> "afftdn=nr=12:nf=-20"
                    "MEDIUM" -> "afftdn=nr=18:nf=-25"
                    "AGGRESSIVE" -> "afftdn=nr=24:nf=-30"
                    else -> "anull"
                }
                session = runCancellableFFmpeg(
                    arrayOf(
                        "-y",
                        "-ss", startS,
                        "-to", endS,
                        "-i", actualInputFile.absolutePath,
                        "-vn",
                        "-af", denoiseFilter,
                        "-c:a", "aac",
                        "-b:a", "192k",
                        "-movflags", "+faststart",
                        tempOutput.absolutePath
                    ),
                    onSessionCreated
                )
            }

            val finalSession = session!!
            val returnCode = finalSession.returnCode
            if (returnCode != null && ReturnCode.isSuccess(returnCode) && tempOutput.exists() && tempOutput.length() > 0L) {
                val settingsManager = shura.media.data.SettingsManager.getInstance(context)
                val outputUriStr = settingsManager.outputFolderUri.value
                val preferOriginal = settingsManager.saveSingleEditsToOriginalFolder.value
                val originalName = getDisplayNameFromUri(context, inputUri)
                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                val dateStr = sdf.format(java.util.Date())
                val suffix = if (denoiseLevel == "OFF") "trimmed" else "trimmed_denoised"
                val fileName = "${originalName}_${dateStr}_${suffix}.m4a"
                val defaultQCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val target = shura.media.data.MediaOutputRouter.openOutputTarget(
                    context = context,
                    sourceUriStr = uriStr,
                    fileName = fileName,
                    mimeType = "audio/mp4",
                    preferOriginalFolder = preferOriginal,
                    fallbackOutputTreeUri = outputUriStr,
                    defaultRelativePath = android.os.Environment.DIRECTORY_DOWNLOADS + "/Compressed",
                    defaultCollectionLegacy = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    defaultCollectionQ = defaultQCollection
                )
                val outStream = target.stream
                if (outStream != null) {
                    success = copyFileToStream(tempOutput, outStream)
                    if (success) {
                        shura.media.data.MediaOutputRouter.finalizeOutputTarget(context, target, "audio/mp4")
                        fellBackToOutputFolder = preferOriginal && !target.savedToOriginalFolder
                    } else {
                        shura.media.data.MediaOutputRouter.abortOutputTarget(context, target)
                    }
                } else {
                    shura.media.data.MediaOutputRouter.abortOutputTarget(context, target)
                }
            } else if (!ReturnCode.isCancel(returnCode)) {
                val tailLogs = shura.media.LogKeeper.sanitize(
                    finalSession.allLogsAsString.orEmpty().lines().takeLast(25).joinToString("\n")
                )
                shura.media.LogKeeper.logError(
                    "AudioTrimmerScreen",
                    "FFmpeg audio trim failed (${returnCode?.value ?: "null"}):\n$tailLogs",
                    null
                )
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        shura.media.LogKeeper.logError("AudioTrimmerScreen", "Failed to trim audio: ${e.message}", e)
    } finally {
        onSessionCreated(null)
        if (tempInputToDelete != null && tempInputToDelete.exists()) {
            try { tempInputToDelete.delete() } catch (_: Exception) {}
        }
        if (tempOutput.exists()) {
            try { tempOutput.delete() } catch (_: Exception) {}
        }
    }
    AudioExportResult(success = success, fellBackToOutputFolder = fellBackToOutputFolder)
}

private fun copyFileToStream(source: java.io.File, destination: java.io.OutputStream): Boolean {
    return try {
        source.inputStream().use { input ->
            destination.use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        shura.media.LogKeeper.logError("AudioTrimmerScreen", "Failed to copy trimmed file", e)
        false
    }
}
