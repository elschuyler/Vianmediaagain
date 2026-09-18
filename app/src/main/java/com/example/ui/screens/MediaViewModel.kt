package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaFolder
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)

    private val _mediaFolders = MutableStateFlow<List<MediaFolder>>(emptyList())
    val mediaFolders: StateFlow<List<MediaFolder>> = _mediaFolders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null
    private var scanJob: kotlinx.coroutines.Job? = null
    private var isSuspended = false

    init {
        loadMedia()
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
}
