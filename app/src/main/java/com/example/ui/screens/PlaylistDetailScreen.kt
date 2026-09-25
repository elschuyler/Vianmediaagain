package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.PlaylistItem
import com.example.data.PlaylistRepository
import kotlinx.coroutines.launch

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
                            Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                val first = localPlaylistItems.first()
                                onNavigateToPlayer(first.mediaUri)
                            },
                            onLongClick = {
                                android.widget.Toast.makeText(context, "Playing all in background...", android.widget.Toast.LENGTH_SHORT).show()
                                val sessionToken = androidx.media3.session.SessionToken(context, android.content.ComponentName(context, com.example.service.PlaybackService::class.java))
                                val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
                                controllerFuture.addListener({
                                    val controller = controllerFuture.get()
                                    val items = localPlaylistItems.map { 
                                        val name = getDisplayNameFromUri(context, Uri.parse(it.mediaUri))
                                        val meta = androidx.media3.common.MediaMetadata.Builder().setTitle(name).build()
                                        androidx.media3.common.MediaItem.Builder()
                                            .setMediaId(it.mediaUri)
                                            .setUri(it.mediaUri)
                                            .setMediaMetadata(meta)
                                            .build() 
                                    }
                                    controller.setMediaItems(items)
                                    controller.prepare()
                                    controller.play()
                                }, androidx.core.content.ContextCompat.getMainExecutor(context))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
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
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
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
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                            }
                        }
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            selectedItems.forEach { repository.deletePlaylistItemById(it.id) }
                            selectedItems.clear()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
                                        onNavigateToPlayer(item.mediaUri)
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
                                    imageVector = Icons.Filled.DragHandle,
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
                                                            val itemsToUpdate = mutableListOf<com.example.data.PlaylistItem>()
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
