package com.example.ui.components
import androidx.compose.foundation.border
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun FloatingVideoPlayerOverlay(
    player: Player?,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onOpenMainPlayer: () -> Unit,
    onSwitchToMiniPlayer: () -> Unit,
    onAspectRatioChanged: (Float) -> Unit = {},
    isMinimizedExternal: Boolean = false,
    onMinimizeChange: (Boolean) -> Unit = {}
) {
    androidx.compose.runtime.LaunchedEffect(isMinimizedExternal) {
        if (isMinimizedExternal) {
            com.example.service.PlayerManager.detachVideoSurface()
        }
    }

    if (isMinimizedExternal) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF2196F3))
                .border(1.dp, Color.White.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .clickable { onMinimizeChange(false) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                contentDescription = "Expand Floating Player",
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val playerSnapshot by com.example.service.PlayerManager.playbackState.collectAsState()

    var title by remember { mutableStateOf(playerSnapshot.currentTitle.ifEmpty { player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown" }) }
    var isPlaying by remember { mutableStateOf(playerSnapshot.isPlaying) }
    var repeatMode by remember { mutableIntStateOf(playerSnapshot.repeatMode) }
    var currentSpeed by remember { mutableFloatStateOf(playerSnapshot.playbackSpeed) }
    val playlist = remember { mutableStateListOf<MediaItem>() }
    var currentIndex by remember { mutableIntStateOf(playerSnapshot.currentIndex.coerceAtLeast(0)) }


    val refreshPlaylist: () -> Unit = {
        playlist.clear()
        player?.let { p ->
            for (i in 0 until p.mediaItemCount) {
                playlist.add(p.getMediaItemAt(i))
            }
            currentIndex = p.currentMediaItemIndex
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { com.example.data.SettingsManager.getInstance(context) }
    val keepScreenAwake by settingsManager.keepScreenAwake.collectAsState()

    LaunchedEffect(playerSnapshot) {
        isPlaying = playerSnapshot.isPlaying
        repeatMode = playerSnapshot.repeatMode
        currentSpeed = playerSnapshot.playbackSpeed
        if (playerSnapshot.currentTitle.isNotEmpty()) {
            title = playerSnapshot.currentTitle
        }
        if (playerSnapshot.currentIndex >= 0) {
            currentIndex = playerSnapshot.currentIndex
        }
        if (playerSnapshot.playlist.isNotEmpty() && playerSnapshot.playlist.size != playlist.size) {
            playlist.clear()
            playlist.addAll(playerSnapshot.playlist)
        }
    }

    LaunchedEffect(player) {
        refreshPlaylist()
        currentSpeed = player?.playbackParameters?.speed ?: 1.0f
        player?.videoSize?.let { vs ->
            if (vs.width > 0 && vs.height > 0) {
                onAspectRatioChanged(vs.width.toFloat() / vs.height.toFloat())
            }
        }
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                currentIndex = player.currentMediaItemIndex
                currentSpeed = player.playbackParameters.speed
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                refreshPlaylist()
            }
            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
            }
            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                currentSpeed = playbackParameters.speed
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val vs = player.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    onAspectRatioChanged(vs.width.toFloat() / vs.height.toFloat())
                }
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onAspectRatioChanged(videoSize.width.toFloat() / videoSize.height.toFloat())
                }
            }
        }
        player.addListener(listener)
        refreshPlaylist()
        onDispose {
            player.removeListener(listener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Topbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onOpenMainPlayer() }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DragIndicator, contentDescription = "Drag", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        com.example.service.PlayerManager.flushProgressToStorage()
                        onSwitchToMiniPlayer()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Switch to Mini Player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        com.example.service.PlayerManager.flushProgressToStorage()
                        onOpenMainPlayer()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Open Full-Screen Player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Video Player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                var showControls by remember { mutableStateOf(false) }
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            view.keepScreenOn = keepScreenAwake && isPlaying
                        },
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls }
                            )
                        }
                    )
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 0.dp)
                            ) {
                                com.example.ui.screens.PlaybackProgressRow(
                                    mediaController = player,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 0.dp)
                                )
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left/Center Playback controls
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { 
                                                player?.let { controller ->
                                                    if (controller.hasPreviousMediaItem()) {
                                                        controller.seekToPreviousMediaItem()
                                                    } else {
                                                        controller.seekTo(0)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SkipPrevious,
                                                contentDescription = "Previous",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { 
                                                player?.let { controller ->
                                                    if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                                        controller.seekTo(0)
                                                        controller.prepare()
                                                        controller.play()
                                                    } else if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                                        controller.prepare()
                                                        controller.play()
                                                    } else if (controller.isPlaying) {
                                                        controller.pause()
                                                    } else {
                                                        controller.play()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = "Play/Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { 
                                                player?.let { controller ->
                                                    if (controller.hasNextMediaItem()) {
                                                        controller.seekToNextMediaItem()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SkipNext,
                                                contentDescription = "Next",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }



                                        IconButton(
                                            onClick = {
                                                player?.let { controller ->
                                                    val nextMode = when (controller.repeatMode) {
                                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                                        else -> Player.REPEAT_MODE_OFF
                                                    }
                                                    controller.repeatMode = nextMode
                                                    repeatMode = nextMode
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            val loopIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                                            val loopTint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                            Icon(
                                                imageVector = loopIcon,
                                                contentDescription = "Loop Mode",
                                                tint = loopTint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Speed Toggle Pill (Option A - Compact Pill / Badge with Speed Text, Tap Only)
                                        val speedDisplay = if (kotlin.math.abs(currentSpeed - 0.25f) < 0.01f) "0.25"
                                            else if (kotlin.math.abs(currentSpeed - 0.5f) < 0.01f) "0.5"
                                            else if (kotlin.math.abs(currentSpeed - 1.0f) < 0.01f) "1"
                                            else if (kotlin.math.abs(currentSpeed - 1.2f) < 0.01f) "1.2"
                                            else if (kotlin.math.abs(currentSpeed - 2.0f) < 0.01f) "2"
                                            else String.format(java.util.Locale.US, "%.1f", currentSpeed)

                                        val isNonStandardSpeed = kotlin.math.abs(currentSpeed - 1.0f) >= 0.05f

                                        Box(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isNonStandardSpeed) Color(0xFF2196F3).copy(alpha = 0.25f)
                                                    else Color.White.copy(alpha = 0.15f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isNonStandardSpeed) Color(0xFF2196F3) else Color.White.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    val speedCycle = listOf(1.0f, 1.2f, 2.0f, 0.5f, 0.25f)
                                                    val currentIndex = speedCycle.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
                                                    val nextSpeed = if (currentIndex != -1) {
                                                        speedCycle[(currentIndex + 1) % speedCycle.size]
                                                    } else {
                                                        1.0f
                                                    }
                                                    currentSpeed = nextSpeed
                                                    player?.setPlaybackSpeed(nextSpeed)
                                                    player?.currentMediaItem?.mediaId?.let { uriStr ->
                                                        settingsManager.savePlaybackSpeed(uriStr, nextSpeed)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${speedDisplay}x",
                                                color = if (isNonStandardSpeed) Color(0xFF2196F3) else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Right Action buttons ordered from right to left: Resize, Minimize, Exit (Exit, Minimize, Resize from left to right)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)
                                    ) {
                                        // Exit (Close) button
                                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.Close, "Exit", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        // Minimize button
                                        IconButton(
                                            onClick = {
                                                onMinimizeChange(true)
                                                onMinimize()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Remove, "Minimize", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        // Resize button (with drag gesture)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .pointerInput(Unit) {
                                                    detectDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        onResize(dragAmount.x, dragAmount.y)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.ZoomOutMap,
                                                contentDescription = "Resize",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
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
}
