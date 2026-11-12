package shura.media.ui.components
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitFirstDown
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
    val playerSnapshot by shura.media.service.PlayerManager.playbackState.collectAsState()

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
    val mediaRepo = remember { shura.media.data.MediaRepository(context.applicationContext as android.app.Application) }
    var folders by remember { mutableStateOf<List<shura.media.data.MediaFolder>>(emptyList()) }
    val playlistDao = remember { shura.media.data.AppDatabase.getDatabase(context).playlistDao() }
    var playlistsList by remember { mutableStateOf<List<shura.media.data.Playlist>>(emptyList()) }
    var explorerMode by remember { mutableStateOf("current") }
    var selectedFolder by remember { mutableStateOf<shura.media.data.MediaFolder?>(null) }
    var selectedPlaylist by remember { mutableStateOf<shura.media.data.Playlist?>(null) }
    var selectedPlaylistItems by remember { mutableStateOf<List<shura.media.data.PlaylistItem>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        shura.media.service.PlayerManager.detachVideoSurface()
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
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(androidx.compose.ui.graphics.Color(0xFF2196F3))
                .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var isDrag = false
                            var totalDragX = 0f
                            var totalDragY = 0f
                            val touchSlop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDrag) {
                                        onMinimizeChange(false)
                                    }
                                    break
                                }
                                val dragAmount = change.position - change.previousPosition
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                                val dist = kotlin.math.hypot(totalDragX, totalDragY)
                                if (!isDrag && dist > touchSlop) {
                                    isDrag = true
                                }
                                if (isDrag) {
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = shura.media.R.drawable.ic_launcher_foreground),
                contentDescription = "Expand Mini Player",
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.fillMaxSize(0.9f)
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF03A9F4).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Title bar for moving, PIP and Main Player buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
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
                Icon(Icons.Filled.DragHandle, contentDescription = "Drag to move", tint = Color(0xFF37474F))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = Color(0xFF121212),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                Row {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        shura.media.service.PlayerManager.flushProgressToStorage()
                        onSwitchToVideo()
                    }) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = "Switch to Floating Player", tint = Color(0xFF03A9F4))
                    }

                    IconButton(onClick = {
                        shura.media.service.PlayerManager.flushProgressToStorage()
                        val currentMedia = shura.media.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                            ?: shura.media.service.PlayerManager.playbackState.value.playlist.getOrNull(
                                shura.media.service.PlayerManager.playbackState.value.currentIndex
                            )?.mediaId
                        val intent = android.content.Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                            action = "shura.media.ACTION_OPEN_PLAYER"
                            if (!currentMedia.isNullOrEmpty()) {
                                putExtra("uri", currentMedia)
                            }
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                        onMinimize()
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = "Main Player", tint = Color(0xFF37474F), modifier = Modifier.size(20.dp))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF03A9F4).copy(alpha = 0.25f), thickness = 1.dp)
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
                        .height(20.dp)
                        .padding(horizontal = 4.dp),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = 12.dp)
                                .background(Color(0xFF03A9F4), RoundedCornerShape(3.dp))
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF03A9F4),
                                inactiveTrackColor = Color(0xFFE0E0E0)
                            ),
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            modifier = Modifier.height(3.dp)
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
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color(0xFF121212))
                    }
                    IconButton(onClick = { player?.seekTo(player.currentPosition - 5000) }) {
                        Icon(Icons.Filled.FastRewind, "-5s", tint = Color(0xFF121212))
                    }
                    IconButton(onClick = {
                        player?.let { p ->
                            if (p.isPlaying) {
                                p.pause()
                            } else {
                                if (p.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                                    p.prepare()
                                } else if (p.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                    p.seekTo(0)
                                    p.prepare()
                                }
                                p.play()
                            }
                        }
                    }) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color(0xFF03A9F4), modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { player?.seekTo(player.currentPosition + 5000) }) {
                        Icon(Icons.Filled.FastForward, "+5s", tint = Color(0xFF121212))
                    }
                    IconButton(onClick = { player?.seekToNextMediaItem() }) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color(0xFF121212))
                    }
                    IconButton(onClick = { player?.stop(); player?.clearMediaItems(); onClose() }) {
                        Icon(Icons.Filled.Stop, "Stop", tint = Color(0xFF121212))
                    }
                }

                // Second row: Shuffle, Loop, Refresh, Volume (with reduced padding above and below)
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                player?.let {
                                    it.shuffleModeEnabled = !it.shuffleModeEnabled
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleMode) Color(0xFF03A9F4) else Color(0xFF546E7A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { 
                                player?.let {
                                    it.repeatMode = when (it.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    shura.media.service.PlayerManager.exoPlayer?.pauseAtEndOfMediaItems = (it.repeatMode == Player.REPEAT_MODE_OFF)
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (loopMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = "Loop",
                                tint = if (loopMode != Player.REPEAT_MODE_OFF) Color(0xFF03A9F4) else Color(0xFF546E7A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { /* Refresh playlist logic */ },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, "Refresh", tint = Color(0xFF546E7A), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                try {
                                    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                                    audioManager?.adjustStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC,
                                        android.media.AudioManager.ADJUST_SAME,
                                        android.media.AudioManager.FLAG_SHOW_UI
                                    )
                                } catch (e: Exception) {
                                    shura.media.LogKeeper.logError("MiniPlayerOverlay", "Failed to open volume slider", e)
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = Color(0xFF546E7A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            
            if (true) {
                // The playlist view header - full row clickable to go back one step, reduced padding above & below
                val canGoBack = explorerMode != "root"
                val handleBack: () -> Unit = {
                    explorerMode = when (explorerMode) {
                        "folder_contents" -> "folders"
                        "playlist_contents" -> "playlists"
                        else -> "root"
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (canGoBack) Modifier.clickable { handleBack() } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoBack) {
                        IconButton(onClick = handleBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        text = when (explorerMode) {
                            "root" -> "Library"
                            "current" -> "Now Playing"
                            "folders" -> "Folders"
                            "folder_contents" -> (selectedFolder?.name ?: "Folder Items")
                            "playlists" -> "Saved Playlists"
                            "playlist_contents" -> (selectedPlaylist?.name ?: "Playlist Items")
                            else -> "Library"
                        }, 
                        style = MaterialTheme.typography.titleSmall, 
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                                        selectedFolder = folder
                                        explorerMode = "folder_contents"
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    } else if (explorerMode == "folder_contents") {
                        val currentFolderItems = selectedFolder?.mediaItems ?: emptyList()
                        itemsIndexed(currentFolderItems) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val mediaItems = currentFolderItems.map { mi ->
                                                val meta = androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(mi.name)
                                                    .setDisplayTitle(mi.name)
                                                    .setArtworkUri(mi.uri)
                                                    .build()
                                                androidx.media3.common.MediaItem.Builder()
                                                    .setUri(mi.uri.toString())
                                                    .setMediaId(mi.uri.toString())
                                                    .setMediaMetadata(meta)
                                                    .build()
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val settings = shura.media.data.SettingsManager.getInstance(context)
                                                val targetItem = currentFolderItems[index]
                                                val lastPos = settings.getPlaybackPosition(targetItem.uri.toString(), targetItem.name)
                                                val startPos = if (lastPos > 0 && !settings.isFinished(targetItem.uri.toString(), targetItem.name)) lastPos else 0L
                                                shura.media.service.PlayerManager.exoPlayer?.let { p ->
                                                    p.setMediaItems(mediaItems, index, startPos)
                                                    p.pauseAtEndOfMediaItems = (p.repeatMode == Player.REPEAT_MODE_OFF)
                                                    p.prepare()
                                                    p.play()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.mediaType == shura.media.data.MediaType.VIDEO) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 12.dp).size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sec = (item.duration / 1000) % 60
                                    val min = (item.duration / 1000) / 60
                                    Text(
                                        text = String.format(java.util.Locale.US, "%02d:%02d", min, sec),
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
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                selectedPlaylist = playlistItem
                                                selectedPlaylistItems = dbItems
                                                explorerMode = "playlist_contents"
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    } else if (explorerMode == "playlist_contents") {
                        itemsIndexed(selectedPlaylistItems) { index, item ->
                            val titleName = remember(item.mediaUri) {
                                android.net.Uri.parse(item.mediaUri).lastPathSegment ?: "Unknown"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val mediaItems = selectedPlaylistItems.map { pi ->
                                                val uri = android.net.Uri.parse(pi.mediaUri)
                                                val name = uri.lastPathSegment ?: "Unknown"
                                                val meta = androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(name)
                                                    .setDisplayTitle(name)
                                                    .setArtworkUri(uri)
                                                    .build()
                                                androidx.media3.common.MediaItem.Builder()
                                                    .setUri(pi.mediaUri)
                                                    .setMediaId(pi.mediaUri)
                                                    .setMediaMetadata(meta)
                                                    .build()
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val settings = shura.media.data.SettingsManager.getInstance(context)
                                                val targetItem = selectedPlaylistItems[index]
                                                val targetName = android.net.Uri.parse(targetItem.mediaUri).lastPathSegment ?: "Unknown"
                                                val lastPos = settings.getPlaybackPosition(targetItem.mediaUri, targetName)
                                                val startPos = if (lastPos > 0 && !settings.isFinished(targetItem.mediaUri, targetName)) lastPos else 0L
                                                shura.media.service.PlayerManager.exoPlayer?.let { p ->
                                                    p.setMediaItems(mediaItems, index, startPos)
                                                    p.pauseAtEndOfMediaItems = (p.repeatMode == Player.REPEAT_MODE_OFF)
                                                    p.prepare()
                                                    p.play()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 12.dp).size(20.dp)
                                )
                                Text(
                                    text = titleName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Close completely", tint = Color(0xFF37474F), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onMinimizeChange(true) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, "Minimize", tint = Color(0xFF37474F), modifier = Modifier.size(20.dp))
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
                Icon(Icons.Filled.ZoomOutMap, "Resize", tint = Color(0xFF37474F), modifier = Modifier.size(20.dp))
            }
        }
    }
}
