package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import com.example.data.AppDatabase
import com.example.data.Playlist
import com.example.data.PlaylistItem
import com.example.data.PlaylistRepository
import kotlinx.coroutines.launch
import com.example.data.MediaFolder
import com.example.data.MediaItem
import com.example.data.PlaybackTag
import com.example.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPhotoEditor: (String) -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToPlaylistDetail: (Int) -> Unit = {},
    onNavigateToAudioTrimmer: (String) -> Unit = {},
    onNavigateToVideoEditor: (String) -> Unit = {},
    initialSearchActive: Boolean = false
) {
    val viewModel: MediaViewModel = viewModel()
    val mediaFolders by viewModel.mediaFolders.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showNetworkStreamDialog by rememberSaveable { mutableStateOf(false) }

    val selectedFolder = mediaFolders.find { it.id == selectedFolderId }
    val folderListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.foldersScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.foldersScrollOffset
    )
    val videoListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.videosScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.videosScrollOffset
    )
    val mediaListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.folderDetailScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.folderDetailScrollOffset
    )
    val playlistListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.playlistsScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.playlistsScrollOffset
    )

    LaunchedEffect(folderListState.firstVisibleItemIndex, folderListState.firstVisibleItemScrollOffset) {
        viewModel.foldersScrollIndex = folderListState.firstVisibleItemIndex
        viewModel.foldersScrollOffset = folderListState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(videoListState.firstVisibleItemIndex, videoListState.firstVisibleItemScrollOffset) {
        viewModel.videosScrollIndex = videoListState.firstVisibleItemIndex
        viewModel.videosScrollOffset = videoListState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(mediaListState.firstVisibleItemIndex, mediaListState.firstVisibleItemScrollOffset) {
        viewModel.folderDetailScrollIndex = mediaListState.firstVisibleItemIndex
        viewModel.folderDetailScrollOffset = mediaListState.firstVisibleItemScrollOffset
    }
    LaunchedEffect(playlistListState.firstVisibleItemIndex, playlistListState.firstVisibleItemScrollOffset) {
        viewModel.playlistsScrollIndex = playlistListState.firstVisibleItemIndex
        viewModel.playlistsScrollOffset = playlistListState.firstVisibleItemScrollOffset
    }

    val selectedMediaItems = remember { mutableStateListOf<MediaItem>() }
    var lastSelectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val isMultiSelectMode = selectedMediaItems.isNotEmpty()

    LaunchedEffect(isMultiSelectMode) {
        if (!isMultiSelectMode) {
            lastSelectedIndex = null
        }
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val coroutineScope = rememberCoroutineScope()
    val repository = remember {
        val dao = AppDatabase.getDatabase(context).playlistDao()
        PlaylistRepository(dao)
    }

    var showAddToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var renameValue by rememberSaveable { mutableStateOf("") }
    
    var isSearchActive by rememberSaveable { mutableStateOf(initialSearchActive) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val imageLoader = context.imageLoader
            val settings = com.example.data.SettingsManager.getInstance(context)
            selectedMediaItems.forEach { media ->
                val uriStr = media.uri.toString()
                imageLoader.diskCache?.remove(uriStr)
                imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(uriStr))
                settings.removePlaybackState(uriStr)
            }
            selectedFolderId?.let { viewModel.scanFolder(it) } ?: viewModel.loadMedia()
            selectedMediaItems.clear()
            showDeleteConfirmDialog = false
        }
    }

    var pendingRenameUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRenameName by remember { mutableStateOf("") }
    
    val renameLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = pendingRenameUri
            val name = pendingRenameName
            if (uri != null && name.isNotEmpty()) {
                coroutineScope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                            }
                            context.contentResolver.update(uri, values, null, null)
                        } catch (e: Exception) {
                            com.example.LogKeeper.logError("MainScreen", "Error renaming file after permission", e)
                        }
                    }
                    selectedFolderId?.let { viewModel.scanFolder(it) } ?: viewModel.loadMedia()
                    selectedMediaItems.clear()
                    showRenameDialog = false
                    pendingRenameUri = null
                    pendingRenameName = ""
                }
            }
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }
    BackHandler(enabled = isMultiSelectMode && !isSearchActive) {
        selectedMediaItems.clear()
    }
    BackHandler(enabled = !isMultiSelectMode && !isSearchActive && selectedFolder != null) {
        viewModel.selectFolder(null)
    }
    BackHandler(enabled = !isMultiSelectMode && !isSearchActive && selectedFolder == null && selectedTab != LibraryTab.FOLDERS) {
        viewModel.selectTab(LibraryTab.FOLDERS)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.suspendOperations()
                }
                Lifecycle.Event.ON_START -> {
                    viewModel.resumeOperations()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = RoundedCornerShape(25.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    } else if (isMultiSelectMode) {
                        val totalInView = if (selectedFolder != null) selectedFolder.mediaItems.size else allVideos.size
                        Text("${selectedMediaItems.size}/$totalInView Selected")
                    } else if (selectedFolder != null) {
                        Text(selectedFolder.name)
                    } else {
                        when (selectedTab) {
                            LibraryTab.FOLDERS -> Text("Folders")
                            LibraryTab.VIDEOS -> Text("All Videos")
                            LibraryTab.PLAYLISTS -> Text("Playlists")
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    } else if (isMultiSelectMode) {
                        IconButton(onClick = { selectedMediaItems.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                        }
                    } else if (selectedFolder != null) {
                        IconButton(onClick = { viewModel.selectFolder(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear Search")
                            }
                        }
                    } else if (isMultiSelectMode) {
                        IconButton(onClick = { 
                            if (selectedMediaItems.isNotEmpty()) {
                                val firstMedia = selectedMediaItems.first()
                                if (firstMedia.mediaType == com.example.data.MediaType.IMAGE) {
                                    onNavigateToPhotoEditor(firstMedia.uri.toString())
                                } else {
                                    onNavigateToPlayer(firstMedia.uri.toString())
                                }
                                selectedMediaItems.clear()
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = { showAddToPlaylistDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist")
                        }
                    } else {
                        IconButton(onClick = { 
                            isSearchActive = true 
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        if (selectedFolder == null && selectedTab != LibraryTab.PLAYLISTS) {
                            IconButton(onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists")
                            }
                        }
                        Box {
                            var showOverflowMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                if (selectedFolder != null || selectedTab != LibraryTab.PLAYLISTS) {
                                    DropdownMenuItem(text = { Text("Sort by Name") }, onClick = { viewModel.setSortOrder(SortOrder.NAME); showOverflowMenu = false })
                                    DropdownMenuItem(text = { Text("Sort by Date") }, onClick = { viewModel.setSortOrder(SortOrder.DATE); showOverflowMenu = false })
                                }
                                DropdownMenuItem(text = { Text("Network Stream") }, onClick = { showNetworkStreamDialog = true; showOverflowMenu = false })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { showSettingsDialog = true; showOverflowMenu = false })
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isMultiSelectMode) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedMediaItems.size > 1) {
                        IconButton(onClick = {
                            val intent = android.content.Intent(context, com.example.BatchActionActivity::class.java).apply {
                                action = android.content.Intent.ACTION_SEND_MULTIPLE
                                type = "*/*"
                                putExtra("force_ffmpeg", true)
                                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(selectedMediaItems.map { it.uri }))
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Batch Compress")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (selectedMediaItems.size == 1) {
                        IconButton(onClick = { 
                            renameValue = selectedMediaItems.first().name.substringBeforeLast(".")
                            showRenameDialog = true 
                        }) {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val item = selectedMediaItems.first()
                            val mimeType = context.contentResolver.getType(item.uri) ?: ""
                            val isAnimated = mimeType == "image/gif" || mimeType == "image/webp"
                            
                            when {
                                item.mediaType == com.example.data.MediaType.AUDIO -> onNavigateToAudioTrimmer(item.uri.toString())
                                item.mediaType == com.example.data.MediaType.VIDEO -> onNavigateToVideoEditor(item.uri.toString())
                                item.mediaType == com.example.data.MediaType.IMAGE && !isAnimated -> onNavigateToPhotoEditor(item.uri.toString())
                                else -> Toast.makeText(context, "Editing this format is not supported", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    IconButton(onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(selectedMediaItems.map { it.uri }))
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Media"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                if (selectedFolder == null && selectedTab == LibraryTab.PLAYLISTS && !isSearchActive) {
                    FloatingActionButton(
                        onClick = { showCreatePlaylistDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Playlist")
                    }
                } else if (mediaFolders.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            val settings = com.example.data.SettingsManager.getInstance(context)
                            val itemsToConsider = if (selectedFolder != null) selectedFolder.mediaItems else allVideos
                            
                            // Find the one with the most recent Play time, or just the first if none
                            val sorted = itemsToConsider.sortedByDescending { settings.getLastPlayedTime(it.uri.toString(), it.name) }
                            val toPlay = sorted.firstOrNull()
                            if (toPlay != null) {
                                if (toPlay.mediaType == com.example.data.MediaType.IMAGE) {
                                    onNavigateToPhotoEditor(toPlay.uri.toString())
                                } else {
                                    onNavigateToPlayer(toPlay.uri.toString())
                                }
                            } else {
                                Toast.makeText(context, "No videos to play", Toast.LENGTH_SHORT).show()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play Last Played")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedFolder == null && !isSearchActive && !isMultiSelectMode) {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == LibraryTab.FOLDERS,
                        onClick = { viewModel.selectTab(LibraryTab.FOLDERS) },
                        text = { Text("Folders") },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = "Folders") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.VIDEOS,
                        onClick = { viewModel.selectTab(LibraryTab.VIDEOS) },
                        text = { Text("Videos") },
                        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Videos") }
                    )
                    Tab(
                        selected = selectedTab == LibraryTab.PLAYLISTS,
                        onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                        text = { Text("Playlists") },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists") }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isLoading && mediaFolders.isEmpty() && playlists.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.loadMedia() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (selectedFolder == null && !isSearchActive && selectedTab == LibraryTab.PLAYLISTS) {
                            if (playlists.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "No playlists yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = { showCreatePlaylistDialog = true }) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Create Playlist")
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = playlistListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item(key = "playlists_count_header") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Found ${playlists.size} Playlists",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            TextButton(onClick = { showCreatePlaylistDialog = true }) {
                                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("New")
                                            }
                                        }
                                    }
                                    items(playlists, key = { it.id }) { playlist ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onNavigateToPlaylistDetail(playlist.id)
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.PlaylistPlay,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Column {
                                                        Text(
                                                            text = playlist.name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (playlist.isTemporary || playlist.name.contains("Temp")) {
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                "Expires 24h",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier
                                                                    .background(
                                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { playlistToDelete = playlist }
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Delete Playlist",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (mediaFolders.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text(
                                    text = "Media library empty. Pull down to refresh.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        } else if (selectedFolder == null && !isSearchActive && selectedTab == LibraryTab.FOLDERS) {
                            LazyColumn(
                                state = folderListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                item(key = "folders_count_header") {
                                    Text(
                                        text = "Found ${mediaFolders.size} Folders",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val sortedFolders = when (sortOrder) {
                                    SortOrder.NAME -> mediaFolders.sortedBy { it.name.lowercase() }
                                    SortOrder.DATE -> mediaFolders.sortedByDescending { it.dateModified }
                                }
                                val settingsManager = SettingsManager.getInstance(context)
                                items(sortedFolders, key = { it.id }) { folder ->
                                    FolderCard(
                                        folder = folder,
                                        onClick = { viewModel.selectFolder(folder.id) },
                                        onExclude = {
                                            settingsManager.addExcludedFolder(folder.id)
                                            viewModel.loadMedia()
                                        }
                                    )
                                }
                            }
                        } else {
                            val itemsToDisplay = if (selectedFolder != null) {
                                selectedFolder.mediaItems
                            } else {
                                allVideos
                            }
                            
                            val filteredMedia = if (isSearchActive) {
                                itemsToDisplay.filter { it.name.contains(searchQuery, ignoreCase = true) }
                            } else {
                                itemsToDisplay
                            }

                            val activeListState = if (selectedFolder != null) {
                                mediaListState
                            } else {
                                videoListState
                            }

                            LazyColumn(
                                state = activeListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                item(key = "media_count_header") {
                                    Text(
                                        text = "Found ${filteredMedia.size} Media Files",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val sortedMediaItems = when (sortOrder) {
                                    SortOrder.NAME -> filteredMedia.sortedBy { it.name.lowercase() }
                                    SortOrder.DATE -> filteredMedia.sortedByDescending { it.dateAdded }
                                }
                                itemsIndexed(sortedMediaItems, key = { _, media -> media.id }) { index, media ->
                                    val isSelected = selectedMediaItems.contains(media)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (isMultiSelectMode) {
                                                        if (isSelected) selectedMediaItems.remove(media) else selectedMediaItems.add(media)
                                                        lastSelectedIndex = index
                                                    } else {
                                                        viewModel.markAsStarted(media.id)
                                                        if (media.mediaType == com.example.data.MediaType.IMAGE) {
                                                            onNavigateToPhotoEditor(media.uri.toString())
                                                        } else {
                                                            onNavigateToPlayer(media.uri.toString())
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isMultiSelectMode) {
                                                        selectedMediaItems.add(media)
                                                        lastSelectedIndex = index
                                                    } else {
                                                        val start = lastSelectedIndex ?: index
                                                        val end = index
                                                        val range = if (start < end) start..end else end..start
                                                        for (i in range) {
                                                            val item = sortedMediaItems[i]
                                                            if (!selectedMediaItems.contains(item)) {
                                                                selectedMediaItems.add(item)
                                                            }
                                                        }
                                                        lastSelectedIndex = index
                                                    }
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                    ) {
                                        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                            Box(
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .height(80.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(media.uri)
                                                        .size(200)
                                                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                                        .allowHardware(true)
                                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Thumbnail",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (media.tag == PlaybackTag.NEW) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopStart)
                                                            .padding(4.dp)
                                                            .background(Color.Red, RoundedCornerShape(2.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("NEW", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                                    }
                                                }
                                                val durationSeconds = media.duration / 1000
                                                val hours = durationSeconds / 3600
                                                val minutes = (durationSeconds % 3600) / 60
                                                val seconds = durationSeconds % 60
                                                val durationStr = if (hours > 0) {
                                                    String.format("%d:%02d:%02d", hours, minutes, seconds)
                                                } else {
                                                    String.format("%02d:%02d", minutes, seconds)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(durationStr, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = media.name, 
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else if (media.tag == PlaybackTag.SEEN || media.tag == PlaybackTag.PLAYING) Color(0xFF707070) else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (media.hasSubtitle) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFF2196F3), RoundedCornerShape(2.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("SUB", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val subText = if (selectedFolder != null) {
                                                    val sizeMb = media.size / (1024f * 1024f)
                                                    if (sizeMb >= 1024f) {
                                                        String.format("%.2f GB", sizeMb / 1024f)
                                                    } else {
                                                        String.format("%.1f MB", sizeMb)
                                                    }
                                                } else {
                                                    val parentFolder = mediaFolders.find { it.mediaItems.contains(media) }
                                                    parentFolder?.let {
                                                        it.path.replace("primary:", "/storage/emulated/0/") + "/" + it.name
                                                    } ?: "Unknown path"
                                                }
                                                Text(
                                                    text = subText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

    if (showAddToPlaylistDialog) {
        var createNew = playlists.isEmpty()
        var newPlaylistName by rememberSaveable { mutableStateOf("") }
        var selectedPlaylistId by rememberSaveable { mutableStateOf<Int?>(playlists.firstOrNull()?.id) }
        
        val hasActiveQueue = com.example.service.PlayerManager.exoPlayer != null && (com.example.service.PlayerManager.exoPlayer?.mediaItemCount ?: 0) > 0
        // 0: Existing, 1: New, 2: Temp (Current Queue)
        var targetType by remember { mutableStateOf(if (playlists.isEmpty()) 1 else 0) }

        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add to Playlist") },
            text = {
                Column {
                    if (playlists.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = targetType == 0, onClick = { targetType = 0 })
                            Text("Existing Playlist", modifier = Modifier.clickable { targetType = 0 })
                        }
                        if (targetType == 0) {
                            Box(modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp)) {
                                var expanded by remember { mutableStateOf(false) }
                                val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(selectedPlaylist?.name ?: "Select Playlist")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    playlists.forEach { pl ->
                                        DropdownMenuItem(
                                            text = { Text(pl.name) },
                                            onClick = {
                                                selectedPlaylistId = pl.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = targetType == 1, onClick = { targetType = 1 })
                        Text("New Playlist", modifier = Modifier.clickable { targetType = 1 })
                    }
                    if (targetType == 1) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp)
                        )
                    }
                    if (hasActiveQueue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = targetType == 2, onClick = { targetType = 2 })
                            Text("Current Queue (Temp)", modifier = Modifier.clickable { targetType = 2 })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (targetType == 2) {
                                val player = com.example.service.PlayerManager.exoPlayer
                                if (player != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        selectedMediaItems.forEach { item ->
                                            val meta = androidx.media3.common.MediaMetadata.Builder()
                                                .setTitle(item.name)
                                                .setDisplayTitle(item.name)
                                                .setArtworkUri(item.uri)
                                                .build()
                                            val mediaItem = androidx.media3.common.MediaItem.Builder()
                                                .setUri(item.uri)
                                                .setMediaId(item.uri.toString())
                                                .setMediaMetadata(meta)
                                                .build()
                                            player.addMediaItem(mediaItem)
                                        }
                                        Toast.makeText(context, "Added to Current Queue", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val playlistId = if (targetType == 1) {
                                    if (newPlaylistName.isNotBlank()) {
                                        repository.insertPlaylist(com.example.data.Playlist(name = newPlaylistName.trim())).toInt()
                                    } else {
                                        return@launch
                                    }
                                } else {
                                    selectedPlaylistId ?: return@launch
                                }
                                selectedMediaItems.forEach { item ->
                                    val playlistItem = com.example.data.PlaylistItem(
                                        playlistId = playlistId,
                                        mediaUri = item.uri.toString()
                                    )
                                    repository.insertPlaylistItem(playlistItem)
                                }
                                
                                launch {
                                    Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showAddToPlaylistDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var playlistTitle by rememberSaveable { mutableStateOf("") }
        var isTemporary by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isTemporary = !isTemporary }
                    ) {
                        Checkbox(
                            checked = isTemporary,
                            onCheckedChange = { isTemporary = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Temporary Playlist", style = MaterialTheme.typography.bodyMedium)
                            Text("Expires in 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = playlistTitle.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createPlaylist(trimmed, isTemporary)
                            Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = playlistTitle.trim().isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (playlistToDelete != null) {
        val targetPlaylist = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${targetPlaylist.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(targetPlaylist.id)
                        playlistToDelete = null
                        Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInfoDialog && selectedMediaItems.isNotEmpty()) {
        var totalSize by remember(selectedMediaItems) { mutableLongStateOf(0L) }
        var singleItemPath by remember(selectedMediaItems) { mutableStateOf("Unknown") }
        
        LaunchedEffect(selectedMediaItems) {
            var calcSize = 0L
            val contextResolver = context.contentResolver
            for (item in selectedMediaItems) {
                try {
                    contextResolver.query(item.uri, arrayOf(android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            calcSize += cursor.getLong(0)
                            if (selectedMediaItems.size == 1) {
                                singleItemPath = cursor.getString(1) ?: item.uri.toString()
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            totalSize = calcSize
            if (selectedMediaItems.size == 1 && singleItemPath == "Unknown") {
               singleItemPath = selectedMediaItems.first().uri.toString()
            }
        }
        val sizeInMB = totalSize / (1024 * 1024)
        
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Selection Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (selectedMediaItems.size == 1) {
                        val item = selectedMediaItems.first()
                        val sizeStr = if (totalSize > 1024 * 1024) "${totalSize / (1024 * 1024)} MB" else "${totalSize / 1024} KB"
                        val durationStr = if (item.duration > 0) String.format(java.util.Locale.US, "%02d:%02d", java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(item.duration), java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(item.duration) % 60) else "Unknown"
                        val dateFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.dateAdded))
                        Text("Name: ${item.name}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Size: $sizeStr", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Duration: $durationStr", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Modified: $dateFormatted", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Path: $singleItemPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Items selected: ${selectedMediaItems.size}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total size: $sizeInMB MB", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showDeleteConfirmDialog && selectedMediaItems.isNotEmpty()) {
        val totalSize = selectedMediaItems.sumOf { it.size }
        val formattedSize = android.text.format.Formatter.formatShortFileSize(context, totalSize)
        val isSingle = selectedMediaItems.size == 1
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (isSingle) "Delete File" else "Delete Files") },
            text = { 
                if (isSingle) {
                    val itemName = selectedMediaItems.first().name
                    Text("Are you sure you want to delete \"$itemName\"?\nSize: $formattedSize\nThis cannot be undone.")
                } else {
                    Text("Are you sure you want to delete ${selectedMediaItems.size} items?\nTotal size: $formattedSize\nThis cannot be undone.") 
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mediaStoreUris = selectedMediaItems.map { it.uri }.filter { it.authority == "media" }
                    val otherUris = selectedMediaItems.map { it.uri }.filter { it.authority != "media" }

                    coroutineScope.launch {
                        if (otherUris.isNotEmpty()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                selectedMediaItems.filter { it.uri.authority != "media" }.forEach { media ->
                                    try {
                                        val uri = media.uri
                                        if (uri.scheme == "file") {
                                            val file = java.io.File(uri.path!!)
                                            if (file.exists()) file.delete()
                                        } else if (uri.authority?.contains("documents") == true) {
                                            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                                        }
                                        val imageLoader = context.imageLoader
                                        imageLoader.diskCache?.remove(uri.toString())
                                        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(uri.toString()))
                                        com.example.data.SettingsManager.getInstance(context).removePlaybackState(uri.toString())
                                    } catch (e: Exception) {
                                        com.example.LogKeeper.logError("MainScreen", "Error deleting file", e)
                                    }
                                }
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && mediaStoreUris.isNotEmpty()) {
                            try {
                                val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                                deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                            } catch (e: Exception) {
                                com.example.LogKeeper.logError("MainScreen", "Error launching delete request", e)
                            }
                        } else if (mediaStoreUris.isNotEmpty()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                selectedMediaItems.filter { it.uri.authority == "media" }.forEach { media ->
                                    try {
                                        context.contentResolver.delete(media.uri, null, null)
                                        val imageLoader = context.imageLoader
                                        imageLoader.diskCache?.remove(media.uri.toString())
                                        imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(media.uri.toString()))
                                        com.example.data.SettingsManager.getInstance(context).removePlaybackState(media.uri.toString())
                                    } catch (se: SecurityException) {
                                        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                                            val recoverable = se as? android.app.RecoverableSecurityException
                                            recoverable?.userAction?.actionIntent?.intentSender?.let { sender ->
                                                deleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                                            }
                                        }
                                    } catch (e: Exception) {
                                        com.example.LogKeeper.logError("MainScreen", "Error deleting file", e)
                                    }
                                }
                            }
                            selectedFolderId?.let { viewModel.scanFolder(it) } ?: viewModel.loadMedia()
                            selectedMediaItems.clear()
                            showDeleteConfirmDialog = false
                        } else {
                            selectedFolderId?.let { viewModel.scanFolder(it) } ?: viewModel.loadMedia()
                            selectedMediaItems.clear()
                            showDeleteConfirmDialog = false
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameDialog && selectedMediaItems.size == 1) {
        val selectedItem = selectedMediaItems.first()
        val extension = selectedItem.name.substringAfterLast(".", "")
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val uri = selectedItem.uri
                                val hasExt = renameValue.contains(".") && renameValue.substringAfterLast(".") == extension
                                val newNameWithExt = if (extension.isNotEmpty() && !hasExt) "$renameValue.$extension" else renameValue
                                
                                if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
                                    android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, newNameWithExt)
                                } else if (uri.scheme == "file") {
                                    val file = java.io.File(uri.path!!)
                                    if (file.exists()) {
                                        val newFile = java.io.File(file.parent, newNameWithExt)
                                        file.renameTo(newFile)
                                    }
                                } else {
                                    try {
                                        val values = android.content.ContentValues().apply {
                                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newNameWithExt)
                                        }
                                        context.contentResolver.update(uri, values, null, null)
                                    } catch (se: SecurityException) {
                                        pendingRenameUri = uri
                                        pendingRenameName = newNameWithExt
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                            val pendingIntent = android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                                            renameLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                            return@withContext
                                        } else if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                                            val recoverable = se as? android.app.RecoverableSecurityException
                                            recoverable?.userAction?.actionIntent?.intentSender?.let { sender ->
                                                renameLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                                                return@withContext
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                com.example.LogKeeper.logError("MainScreen", "Error renaming file", e)
                            }
                        }
                        selectedFolderId?.let { viewModel.scanFolder(it) } ?: viewModel.loadMedia()
                        selectedMediaItems.clear()
                        showRenameDialog = false
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNetworkStreamDialog) {
        var streamUrl by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNetworkStreamDialog = false },
            title = { Text("Network Stream") },
            text = {
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream URL") },
                    placeholder = { Text("http://..., rtsp://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (streamUrl.isNotBlank()) {
                            onNavigateToPlayer(streamUrl)
                            showNetworkStreamDialog = false
                        }
                    }
                ) {
                    Text("Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNetworkStreamDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            SettingsScreen(onNavigateBack = { showSettingsDialog = false })
        }
    }
}

@Composable
fun FolderCard(folder: MediaFolder, onClick: () -> Unit, onExclude: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (folder.mediaItems.any { it.tag == com.example.data.PlaybackTag.NEW }) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xFFE53935), RoundedCornerShape(4.dp))) {
                        Text("NEW", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name, 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (folder.path.isNotEmpty()) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Row {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${folder.videoCount} Videos",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (folder.totalSize > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            val sizeMb = folder.totalSize / (1024 * 1024)
                            val sizeStr = if (sizeMb > 1024) String.format(java.util.Locale.US, "%.2f GB", sizeMb / 1024f) else "$sizeMb MB"
                            Text(
                                text = sizeStr,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Exclude Folder") },
                        onClick = {
                            expanded = false
                            onExclude()
                        }
                    )
                }
            }
        }
    }
}
