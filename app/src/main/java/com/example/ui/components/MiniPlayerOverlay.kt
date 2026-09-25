package com.example.ui.components
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun MiniPlayerOverlay(
    player: Player?,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    isMinimizedExternal: Boolean = false,
    onMinimizeChange: (Boolean) -> Unit = {},
    onSwitchToVideo: () -> Unit = {}
) {
    val playerSnapshot by com.example.service.PlayerManager.playbackState.collectAsState()

    var isPlaying by remember { mutableStateOf(playerSnapshot.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(playerSnapshot.currentPosition) }
    var duration by remember { mutableLongStateOf(playerSnapshot.duration) }
    var title by remember { mutableStateOf(playerSnapshot.currentTitle.ifEmpty { player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown" }) }
    var playlist by remember { mutableStateOf(playerSnapshot.playlist) }
    var isReversed by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(playerSnapshot.currentIndex) }

    var loopMode by remember { mutableIntStateOf(playerSnapshot.repeatMode) }
    var shuffleMode by remember { mutableStateOf(playerSnapshot.shuffleModeEnabled) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaRepo = remember { com.example.data.MediaRepository(context.applicationContext as android.app.Application) }
    var folders by remember { mutableStateOf<List<com.example.data.MediaFolder>>(emptyList()) }
    val playlistDao = remember { com.example.data.AppDatabase.getDatabase(context).playlistDao() }
    var playlistsList by remember { mutableStateOf<List<com.example.data.Playlist>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        com.example.service.PlayerManager.detachVideoSurface()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            folders = mediaRepo.getMediaFolders()
            playlistDao.getAllPlaylists().collect {
                playlistsList = it
            }
        }
    }

    LaunchedEffect(playerSnapshot) {
        isPlaying = playerSnapshot.isPlaying
        currentPosition = playerSnapshot.currentPosition
        duration = playerSnapshot.duration
        if (playerSnapshot.currentTitle.isNotEmpty()) {
            title = playerSnapshot.currentTitle
        }
        if (playerSnapshot.playlist.isNotEmpty()) {
            playlist = playerSnapshot.playlist
        }
        currentIndex = playerSnapshot.currentIndex
        loopMode = playerSnapshot.repeatMode
        shuffleMode = playerSnapshot.shuffleModeEnabled
    }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                currentIndex = player.currentMediaItemIndex
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val newPlaylist = mutableListOf<MediaItem>()
                for (i in 0 until timeline.windowCount) {
                    newPlaylist.add(player.getMediaItemAt(i))
                }
                playlist = newPlaylist
                currentIndex = player.currentMediaItemIndex
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                loopMode = repeatMode
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                shuffleMode = shuffleModeEnabled
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    if (isMinimizedExternal) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFF2196F3))
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape)
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
                contentDescription = "Expand Mini Player",
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Title bar for moving, PIP and Main Player buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(Icons.Filled.DragHandle, contentDescription = "Drag to move", tint = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                Row {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        com.example.service.PlayerManager.flushProgressToStorage()
                        onSwitchToVideo()
                    }) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = "Switch to Floating Player", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = {
                        com.example.service.PlayerManager.flushProgressToStorage()
                        val currentMedia = com.example.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                            ?: com.example.service.PlayerManager.playbackState.value.playlist.getOrNull(
                                com.example.service.PlayerManager.playbackState.value.currentIndex
                            )?.mediaId
                        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                            action = "com.example.ACTION_OPEN_PLAYER"
                            if (!currentMedia.isNullOrEmpty()) {
                                putExtra("uri", currentMedia)
                            }
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        onMinimize()
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = "Main Player", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), thickness = 1.dp)
            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Progress bar
                val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                Slider(
                    value = progress,
                    onValueChange = { newVal ->
                        val newPos = (newVal * duration).toLong()
                        player?.seekTo(newPos)
                        currentPosition = newPos
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFF2196F3), androidx.compose.foundation.shape.CircleShape)
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.LightGray
                            ),
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            modifier = Modifier.height(4.dp)
                        )
                    }
                )
                
                // Playback controls row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player?.seekToPreviousMediaItem() }) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { player?.seekTo(player.currentPosition - 5000) }) {
                        Icon(Icons.Filled.FastRewind, "-5s", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = {
                        if (isPlaying) player?.pause() else player?.play()
                    }) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { player?.seekTo(player.currentPosition + 5000) }) {
                        Icon(Icons.Filled.FastForward, "+5s", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { player?.seekToNextMediaItem() }) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { player?.stop(); player?.clearMediaItems(); onClose() }) {
                        Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Second row: Shuffle, Loop, Refresh, Toggle Playlist
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        player?.let {
                            it.shuffleModeEnabled = !it.shuffleModeEnabled
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { 
                        player?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (loopMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Loop",
                            tint = if (loopMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* Refresh playlist logic */ }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }

                }
            }
            
            if (true) {
                var explorerMode by remember { mutableStateOf("current") }
                
                // The playlist view header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                    if (explorerMode != "root") {
                        IconButton(onClick = { explorerMode = "root" }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Text(
                        text = when (explorerMode) {
                            "root" -> "Library"
                            "current" -> "Now Playing"
                            "folders" -> "Folders"
                            "playlists" -> "Saved Playlists"
                            else -> "Library"
                        }, 
                        style = MaterialTheme.typography.titleSmall, 
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                ) {
                    if (explorerMode == "root") {
                        val options = listOf("Current", "Folder List", "Playlists")
                        val icons = listOf(Icons.Filled.PlayCircle, Icons.Filled.Folder, Icons.Filled.PlaylistPlay)
                        items(options.size) { index ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        explorerMode = when(index) {
                                            0 -> "current"
                                            1 -> "folders"
                                            else -> "playlists"
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icons[index], contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 16.dp))
                                Text(
                                    text = options[index],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else if (explorerMode == "current") {
                        val displayList = if (isReversed) {
                            playlist.mapIndexed { index, item -> Pair(index, item) }.reversed()
                        } else {
                            playlist.mapIndexed { index, item -> Pair(index, item) }
                        }
                        items(displayList) { pair ->
                            val originalIndex = pair.first
                            val item = pair.second
                            val isSelected = originalIndex == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { player?.seekToDefaultPosition(originalIndex) }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.mediaMetadata.title?.toString() ?: item.mediaId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else if (explorerMode == "folders") {
                        items(folders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val items = folder.mediaItems
                                            val mediaItems = items.map { item ->
                                                val meta = androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(item.name)
                                                    .setDisplayTitle(item.name)
                                                    .setArtworkUri(item.uri)
                                                    .build()
                                                androidx.media3.common.MediaItem.Builder()
                                                    .setUri(item.uri.toString())
                                                    .setMediaId(item.uri.toString())
                                                    .setMediaMetadata(meta)
                                                    .build()
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.example.service.PlayerManager.exoPlayer?.let { player ->
                                                    player.setMediaItems(mediaItems, 0, 0)
                                                    player.prepare()
                                                    player.play()
                                                }
                                                explorerMode = "current"
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 16.dp))
                                Column {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${folder.mediaItems.size} items",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (explorerMode == "playlists") {
                        items(playlistsList) { playlistItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val dbItems = playlistDao.getItemsForPlaylist(playlistItem.id).first()
                                            val mediaItems = dbItems.map { item ->
                                                val meta = androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(android.net.Uri.parse(item.mediaUri).lastPathSegment ?: "Unknown")
                                                    .setDisplayTitle(android.net.Uri.parse(item.mediaUri).lastPathSegment ?: "Unknown")
                                                    .setArtworkUri(android.net.Uri.parse(item.mediaUri))
                                                    .build()
                                                androidx.media3.common.MediaItem.Builder()
                                                    .setUri(item.mediaUri)
                                                    .setMediaId(item.mediaUri)
                                                    .setMediaMetadata(meta)
                                                    .build()
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                if (mediaItems.isNotEmpty()) {
                                                    com.example.service.PlayerManager.exoPlayer?.let { player ->
                                                        player.setMediaItems(mediaItems, 0, 0)
                                                        player.prepare()
                                                        player.play()
                                                    }
                                                    explorerMode = "current"
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 16.dp))
                                Text(
                                    text = playlistItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Floating Close, Minimize, and Resize buttons at bottom right
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Close completely", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onMinimizeChange(true) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, "Minimize", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                            change.consume()
                            onResize(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ZoomOutMap, "Resize", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}
