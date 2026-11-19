package shura.media.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import shura.media.ui.icons.AppIcons
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import shura.media.data.AppDatabase
import shura.media.data.PlaylistItem
import shura.media.data.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val repository = remember {
        val dao = AppDatabase.getDatabase(context).playlistDao()
        PlaylistRepository(dao)
    }

    val playlist by repository.getPlaylistById(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val playlistItems by repository.getItemsForPlaylist(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var localPlaylistItems by remember { mutableStateOf(playlistItems) }

    LaunchedEffect(playlistItems) {
        if (draggedItemIndex == null) {
            localPlaylistItems = playlistItems
        }
    }

    var itemHeightPx by remember { mutableFloatStateOf(150f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = remember(density) { with(density) { 8.dp.toPx() } }

    val selectedItems = remember { mutableStateListOf<PlaylistItem>() }
    val isMultiSelectMode = selectedItems.isNotEmpty()
    var activeBgControllerFuture by remember {
        mutableStateOf<com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeBgControllerFuture?.let {
                try {
                    androidx.media3.session.MediaController.releaseFuture(it)
                } catch (_: Exception) {}
            }
            activeBgControllerFuture = null
        }
    }

    BackHandler(enabled = isMultiSelectMode) {
        selectedItems.clear()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isMultiSelectMode) {
                        Text("${selectedItems.size} Selected")
                    } else {
                        Text(playlist?.name ?: "Playlist")
                    }
                },
                navigationIcon = {
                    if (isMultiSelectMode) {
                        IconButton(onClick = { selectedItems.clear() }) {
                            Icon(AppIcons.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(AppIcons.AutoMirrored.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isMultiSelectMode && (playlist?.isTemporary == true || playlist?.name == "Temp Current" || playlist?.name == "Quick Play (Temporary)")) {
                        var showSaveDialog by remember { mutableStateOf(false) }
                        TextButton(onClick = { showSaveDialog = true }) {
                            Text("Save / Keep")
                        }
                        
                        if (showSaveDialog) {
                            var newName by remember { mutableStateOf(if (playlist?.name?.contains("Temp") == true) "My Playlist" else (playlist?.name ?: "")) }
                            AlertDialog(
                                onDismissRequest = { showSaveDialog = false },
                                title = { Text("Keep Playlist") },
                                text = {
                                    Column {
                                        Text(
                                            "Save this playlist permanently so it won't be automatically cleared tomorrow.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = newName,
                                            onValueChange = { newName = it },
                                            label = { Text("Playlist Name") },
                                            singleLine = true
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val trimmed = newName.trim()
                                            if (trimmed.isNotBlank() && trimmed != "Temp Current" && trimmed != "Quick Play (Temporary)") {
                                                coroutineScope.launch {
                                                    repository.makePlaylistPermanent(playlist!!.id, trimmed)
                                                }
                                                showSaveDialog = false
                                            }
                                        }
                                    ) { Text("Save") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isMultiSelectMode && localPlaylistItems.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .combinedClickable(
                            onClick = {
                                val snapshot = localPlaylistItems.toList()
                                if (snapshot.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val items = withContext(Dispatchers.IO) {
                                            buildPlaylistMediaItems(context, snapshot)
                                        }
                                        shura.media.service.PlayerManager.setPendingQueue(items, 0, isImplicit = false)
                                        onNavigateToPlayer(snapshot.first().mediaUri)
                                    }
                                }
                            },
                            onLongClick = {
                                val snapshot = localPlaylistItems.toList()
                                if (snapshot.isEmpty()) return@combinedClickable
                                android.widget.Toast.makeText(context, "Playing all in background...", android.widget.Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val items = withContext(Dispatchers.IO) {
                                        buildPlaylistMediaItems(context, snapshot)
                                    }
                                    activeBgControllerFuture?.let {
                                        try {
                                            androidx.media3.session.MediaController.releaseFuture(it)
                                        } catch (_: Exception) {}
                                    }
                                    val sessionToken = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, shura.media.service.PlaybackService::class.java))
                                    val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                                    activeBgControllerFuture = controllerFuture
                                    controllerFuture.addListener({
                                        try {
                                            val controller = controllerFuture.get()
                                            shura.media.service.PlayerManager.isImplicitFolderQueue = false
                                            controller.setMediaItems(items)
                                            controller.prepare()
                                            controller.play()
                                        } catch (_: Exception) {}
                                    }, androidx.core.content.ContextCompat.getMainExecutor(context))
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = "Play First / Long Press Play All",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        bottomBar = {
            if (isMultiSelectMode) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedItems.size == 1) {
                        val selected = selectedItems.first()
                        val index = localPlaylistItems.indexOf(selected)
                        if (index > 0) {
                            IconButton(onClick = {
                                val prev = localPlaylistItems[index - 1]
                                val tempTimestamp = selected.timestamp
                                coroutineScope.launch {
                                    repository.updatePlaylistItem(selected.copy(timestamp = prev.timestamp))
                                    repository.updatePlaylistItem(prev.copy(timestamp = tempTimestamp))
                                    selectedItems.clear()
                                }
                            }) {
                                Icon(AppIcons.ArrowUpward, contentDescription = "Move Up")
                            }
                        }
                        if (index < localPlaylistItems.size - 1) {
                            IconButton(onClick = {
                                val next = localPlaylistItems[index + 1]
                                val tempTimestamp = selected.timestamp
                                coroutineScope.launch {
                                    repository.updatePlaylistItem(selected.copy(timestamp = next.timestamp))
                                    repository.updatePlaylistItem(next.copy(timestamp = tempTimestamp))
                                    selectedItems.clear()
                                }
                            }) {
                                Icon(AppIcons.ArrowDownward, contentDescription = "Move Down")
                            }
                        }
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            selectedItems.forEach { repository.deletePlaylistItemById(it.id) }
                            selectedItems.clear()
                        }
                    }) {
                        Icon(AppIcons.Delete, contentDescription = "Delete")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    ) { innerPadding ->
        if (localPlaylistItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No items in this playlist", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(localPlaylistItems, key = { _, item -> item.id }) { index, item ->
                    val isSelected = selectedItems.contains(item)
                    val isDragging = draggedItemIndex == index
                    
                    val targetOffset = remember(draggedItemIndex, dragOffset, itemHeightPx, spacingPx, index, localPlaylistItems.size) {
                        if (isDragging) {
                            dragOffset
                        } else if (draggedItemIndex != null) {
                            val threshold = itemHeightPx + spacingPx
                            if (threshold > 0) {
                                val moveSlots = Math.round(dragOffset / threshold).toInt()
                                    .coerceIn(-draggedItemIndex!!, localPlaylistItems.size - 1 - draggedItemIndex!!)
                                
                                if (moveSlots > 0 && index in (draggedItemIndex!! + 1)..(draggedItemIndex!! + moveSlots)) {
                                    -threshold
                                } else if (moveSlots < 0 && index in (draggedItemIndex!! + moveSlots)..(draggedItemIndex!! - 1)) {
                                    threshold
                                } else 0f
                            } else 0f
                        } else 0f
                    }
                    
                    val animatedOffset by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = targetOffset,
                        label = "offset"
                    )
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                itemHeightPx = size.height.toFloat()
                            }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (draggedItemIndex != null) {
                                    if (isDragging) targetOffset else animatedOffset
                                } else {
                                    0f
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        if (isSelected) selectedItems.remove(item) else selectedItems.add(item)
                                    } else {
                                        val snapshot = localPlaylistItems.toList()
                                        val startIdx = snapshot.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                        coroutineScope.launch {
                                            val items = withContext(Dispatchers.IO) {
                                                buildPlaylistMediaItems(context, snapshot)
                                            }
                                            shura.media.service.PlayerManager.setPendingQueue(items, startIdx, isImplicit = false)
                                            onNavigateToPlayer(item.mediaUri)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        selectedItems.add(item)
                                    }
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val name = remember(item.mediaUri) { getDisplayNameFromUri(context, Uri.parse(item.mediaUri)) }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    maxLines = 2,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.isNotFound) {
                                    Text(
                                        "File not found",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            if (!isMultiSelectMode) {
                                Icon(
                                    imageVector = AppIcons.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(4.dp)
                                        .pointerInput(item.id, index) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedItemIndex = index
                                                    dragOffset = 0f
                                                },
                                                onDragEnd = {
                                                    if (draggedItemIndex != null) {
                                                        val threshold = itemHeightPx + spacingPx
                                                        val moveSlots = Math.round(dragOffset / threshold).toInt()
                                                            .coerceIn(-draggedItemIndex!!, localPlaylistItems.size - 1 - draggedItemIndex!!)
                                                        
                                                        if (moveSlots != 0) {
                                                            val list = localPlaylistItems.toMutableList()
                                                            val item = list.removeAt(draggedItemIndex!!)
                                                            list.add(draggedItemIndex!! + moveSlots, item)
                                                            localPlaylistItems = list
                                                            
                                                            val newTimestamps = playlistItems.map { it.timestamp }
                                                            val itemsToUpdate = mutableListOf<shura.media.data.PlaylistItem>()
                                                            localPlaylistItems.forEachIndexed { i, localItem ->
                                                                if (localItem.timestamp != newTimestamps[i]) {
                                                                    itemsToUpdate.add(localItem.copy(timestamp = newTimestamps[i]))
                                                                }
                                                            }
                                                            if (itemsToUpdate.isNotEmpty()) {
                                                                coroutineScope.launch {
                                                                    repository.updatePlaylistItems(itemsToUpdate)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    draggedItemIndex = null
                                                    dragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggedItemIndex = null
                                                    dragOffset = 0f
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.y
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildPlaylistMediaItems(
    context: android.content.Context,
    items: List<PlaylistItem>
): List<androidx.media3.common.MediaItem> {
    return items.map { pi ->
        val parsedUri = Uri.parse(pi.mediaUri)
        val name = getDisplayNameFromUri(context, parsedUri)
        val meta = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(name)
            .setDisplayTitle(name)
            .setArtworkUri(parsedUri)
            .build()
        androidx.media3.common.MediaItem.Builder()
            .setMediaId(pi.mediaUri)
            .setUri(parsedUri)
            .setMediaMetadata(meta)
            .build()
    }
}
