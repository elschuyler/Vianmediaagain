package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.MediaFolder
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.Playlist
import com.example.data.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab {
    FOLDERS,
    VIDEOS,
    PLAYLISTS
}

enum class SortOrder {
    NAME,
    DATE
}

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val playlistDao = AppDatabase.getDatabase(application).playlistDao()
    private val playlistRepository = PlaylistRepository(playlistDao)

    private val _mediaFolders = MutableStateFlow<List<MediaFolder>>(emptyList())
    val mediaFolders: StateFlow<List<MediaFolder>> = _mediaFolders.asStateFlow()

    val allVideos: StateFlow<List<MediaItem>> = _mediaFolders.map { folders ->
        folders.flatMap { it.mediaItems }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val playlists: StateFlow<List<Playlist>> = playlistRepository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTab = MutableStateFlow(LibraryTab.FOLDERS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Persistent scroll states across tabs and views
    var foldersScrollIndex = 0
    var foldersScrollOffset = 0

    var videosScrollIndex = 0
    var videosScrollOffset = 0

    var folderDetailScrollIndex = 0
    var folderDetailScrollOffset = 0

    var playlistsScrollIndex = 0
    var playlistsScrollOffset = 0

    private var loadJob: kotlinx.coroutines.Job? = null
    private var scanJob: kotlinx.coroutines.Job? = null
    private var isSuspended = false

    init {
        loadMedia()
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun suspendOperations() {
        isSuspended = true
        loadJob?.cancel()
        scanJob?.cancel()
        _isLoading.value = false
    }

    fun resumeOperations() {
        if (isSuspended) {
            isSuspended = false
            if (_mediaFolders.value.isEmpty()) {
                loadMedia()
            }
        }
    }

    fun loadMedia() {
        if (isSuspended) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val folders = repository.getMediaFolders()
            _mediaFolders.value = folders
            _isLoading.value = false
        }
    }

    fun markAsStarted(mediaId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentFolders = _mediaFolders.value.toMutableList()
            var updated = false
            for (i in currentFolders.indices) {
                val folder = currentFolders[i]
                val itemIndex = folder.mediaItems.indexOfFirst { it.id == mediaId }
                if (itemIndex != -1) {
                    val items = folder.mediaItems.toMutableList()
                    if (items[itemIndex].tag == com.example.data.PlaybackTag.NEW || items[itemIndex].tag == com.example.data.PlaybackTag.UNSEEN) {
                        items[itemIndex] = items[itemIndex].copy(tag = com.example.data.PlaybackTag.PLAYING)
                        currentFolders[i] = folder.copy(mediaItems = items)
                        updated = true
                    }
                    break
                }
            }
            if (updated) {
                _mediaFolders.value = currentFolders
            }
        }
    }

    fun scanFolder(folderId: String) {
        if (isSuspended) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val updatedFolder = repository.getMediaFolder(folderId)
            val currentFolders = _mediaFolders.value.toMutableList()
            val index = currentFolders.indexOfFirst { it.id == folderId }
            if (index != -1) {
                if (updatedFolder != null && updatedFolder.mediaItems.isNotEmpty()) {
                    currentFolders[index] = updatedFolder
                } else {
                    currentFolders.removeAt(index)
                }
                _mediaFolders.value = currentFolders
            }
        }
    }

    fun createPlaylist(name: String, isTemporary: Boolean = false, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = playlistRepository.insertPlaylist(Playlist(name = name.trim(), isTemporary = isTemporary))
            onComplete?.invoke(id)
        }
    }

    fun deletePlaylist(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepository.deletePlaylistById(id)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        deletePlaylist(playlist.id)
    }
}
