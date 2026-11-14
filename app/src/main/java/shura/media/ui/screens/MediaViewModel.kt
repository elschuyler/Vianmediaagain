package shura.media.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import shura.media.data.AppDatabase
import shura.media.data.MediaFolder
import shura.media.data.MediaItem
import shura.media.data.MediaRepository
import shura.media.data.Playlist
import shura.media.data.PlaylistRepository
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
    private var cacheSaveJob: kotlinx.coroutines.Job? = null
    private var isSuspended = false

    private fun scheduleCacheSave(folders: List<MediaFolder>) {
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1500L)
            repository.saveCachedMediaFolders(folders)
        }
    }

    init {
        val cached = repository.getCachedMediaFolders()
        if (!cached.isNullOrEmpty()) {
            _mediaFolders.value = cached
            _isLoading.value = false
        }
        // Auto-refresh once a day (24h cooldown), otherwise preserve cached library
        if (repository.isCacheStale()) {
            loadMedia(forceRefresh = true, silent = !cached.isNullOrEmpty())
        }
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
        if (folderId != null) {
            // Targeted scan: when entering folder then only
            scanFolder(folderId)
        }
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
                val cached = repository.getCachedMediaFolders()
                if (!cached.isNullOrEmpty()) {
                    _mediaFolders.value = cached
                } else {
                    loadMedia(forceRefresh = true)
                }
            }
        }
    }

    fun refreshMedia() {
        loadMedia(forceRefresh = true, silent = false)
    }

    fun loadMedia(forceRefresh: Boolean = false, silent: Boolean = false) {
        if (isSuspended) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            if (!silent) _isLoading.value = true
            val folders = repository.getMediaFolders(forceRefresh = forceRefresh)
            _mediaFolders.value = folders
            if (!silent) _isLoading.value = false
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
                    if (items[itemIndex].tag == shura.media.data.PlaybackTag.NEW || items[itemIndex].tag == shura.media.data.PlaybackTag.UNSEEN) {
                        items[itemIndex] = items[itemIndex].copy(tag = shura.media.data.PlaybackTag.PLAYING)
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

    fun syncPlaybackTags() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentFolders = _mediaFolders.value
            if (currentFolders.isEmpty()) return@launch
            val lookup = shura.media.data.SettingsManager.getInstance(getApplication()).createPlaybackLookup()
            val currentTime = System.currentTimeMillis()
            val fifteenDaysMs = 15L * 24 * 60 * 60 * 1000
            var anyChanged = false
            val updatedFolders = currentFolders.map { folder ->
                var folderChanged = false
                val updatedItems = folder.mediaItems.map { item ->
                    val uriStr = item.uri.toString()
                    val isFinished = lookup.isFinished(uriStr, item.name)
                    val playbackPos = lookup.getPlaybackPosition(uriStr, item.name)
                    val lastPlayedTime = lookup.getLastPlayedTime(uriStr, item.name)
                    val newTag = if (isFinished) {
                        shura.media.data.PlaybackTag.SEEN
                    } else if (playbackPos > 0L) {
                        shura.media.data.PlaybackTag.PLAYING
                    } else if (lastPlayedTime > 0L) {
                        shura.media.data.PlaybackTag.UNSEEN
                    } else {
                        if (currentTime - item.dateAdded < fifteenDaysMs) shura.media.data.PlaybackTag.NEW else shura.media.data.PlaybackTag.UNSEEN
                    }
                    if (newTag != item.tag) {
                        folderChanged = true
                        item.copy(tag = newTag)
                    } else {
                        item
                    }
                }
                if (folderChanged) {
                    anyChanged = true
                    folder.copy(
                        mediaItems = updatedItems,
                        hasNew = updatedItems.any { it.tag == shura.media.data.PlaybackTag.NEW }
                    )
                } else {
                    folder
                }
            }
            if (anyChanged) {
                _mediaFolders.value = updatedFolders
            }
        }
    }

    fun scanFolder(folderId: String) {
        if (isSuspended) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedFolder = _mediaFolders.value.find { it.id == folderId }
            val updatedFolder = repository.getMediaFolder(folderId, cachedFolder)
            val currentFolders = _mediaFolders.value.toMutableList()
            val index = currentFolders.indexOfFirst { it.id == folderId }
            if (index != -1) {
                if (updatedFolder != null && updatedFolder.mediaItems.isNotEmpty()) {
                    currentFolders[index] = updatedFolder
                } else {
                    currentFolders.removeAt(index)
                }
                _mediaFolders.value = currentFolders
                scheduleCacheSave(currentFolders)
            } else if (updatedFolder != null && updatedFolder.mediaItems.isNotEmpty()) {
                currentFolders.add(updatedFolder)
                _mediaFolders.value = currentFolders
                scheduleCacheSave(currentFolders)
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
