package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract

import com.example.LogKeeper

enum class PlaybackTag {
    NEW, UNSEEN, SEEN, PLAYING
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long, // in milliseconds
    val dateAdded: Long,
    val mediaType: MediaType,
    val hasSubtitle: Boolean = false,
    val tag: PlaybackTag = PlaybackTag.NEW,
    val size: Long = 0L
)

data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val dateModified: Long,
    val totalSize: Long,
    val mediaItems: List<MediaItem>
) {
    val videoCount: Int get() = mediaItems.size
    val totalDuration: Long get() = mediaItems.sumOf { it.duration }
}

enum class MediaType {
    AUDIO, VIDEO, IMAGE
}

class MediaRepository(private val context: Context) {
    
    fun getMediaFolder(bucketId: String): MediaFolder? {
        val foldersMap = getMediaFolders()
        return foldersMap.find { it.id == bucketId }
    }

    fun getMediaFolders(): List<MediaFolder> {
        val foldersMap = mutableMapOf<String, MutableList<MediaItem>>()
        val folderNames = mutableMapOf<String, String>()
        val folderPaths = mutableMapOf<String, String>()
        val folderDates = mutableMapOf<String, Long>()
        val folderSizes = mutableMapOf<String, Long>()

        val settings = SettingsManager.getInstance(context)
        val excludedFolders = settings.excludedFolders.value
        val exts = settings.extensions.value

        val outputFolderUriVal = settings.outputFolderUri.value
        var customOutputSegment: String? = null
        if (!outputFolderUriVal.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(outputFolderUriVal)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val segment = docId.substringAfter(':').trim('/')
                if (segment.isNotEmpty()) {
                    customOutputSegment = segment
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        try {
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DURATION,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.MediaColumns.BUCKET_ID,
                android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.MediaColumns.MIME_TYPE
            )

            context.contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                projection, null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DURATION)
                val dateCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val bucketIdCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                
                val currentTime = System.currentTimeMillis()
                val fifteenDaysMs = 15L * 24 * 60 * 60 * 1000

                val ignoreNoMedia = settings.ignoreNoMediaSubfolders.value
                val noMediaDirCache = if (ignoreNoMedia) mutableMapOf<String, Boolean>() else null

                fun isUnderNoMediaFolder(path: String): Boolean {
                    if (noMediaDirCache == null || path.isEmpty()) return false
                    var parentFile: java.io.File? = java.io.File(path).parentFile
                    while (parentFile != null && parentFile.path != "/" && parentFile.parent != null) {
                        val parentPath = parentFile.absolutePath
                        val cached = noMediaDirCache[parentPath]
                        if (cached != null) {
                            if (cached) return true
                        } else {
                            val hasNoMedia = try {
                                java.io.File(parentFile, ".nomedia").exists()
                            } catch (e: Exception) {
                                false
                            }
                            noMediaDirCache[parentPath] = hasNoMedia
                            if (hasNoMedia) return true
                        }
                        parentFile = parentFile.parentFile
                    }
                    return false
                }

                while (cursor.moveToNext()) {
                    val bucketId = if (bucketIdCol != -1) cursor.getString(bucketIdCol) ?: "" else ""
                    if (bucketId.isEmpty() || excludedFolders.contains(bucketId)) continue

                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val isDefaultOutput = dataPath.lowercase().contains("/download/compressed")
                    val isCustomOutput = !customOutputSegment.isNullOrEmpty() && dataPath.lowercase().contains(customOutputSegment!!.lowercase())
                    if (isDefaultOutput || isCustomOutput) {
                        continue
                    }

                    if (ignoreNoMedia && dataPath.isNotEmpty() && isUnderNoMediaFolder(dataPath)) {
                        continue
                    }

                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: continue else continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!exts.contains(ext) && !ext.isEmpty()) continue

                    val id = if (idCol != -1) cursor.getLong(idCol) else continue
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateMs = if (dateCol != -1) cursor.getLong(dateCol) * 1000 else System.currentTimeMillis()
                    val bucketName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "Unknown Folder" else "Unknown Folder"
                    val itemSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""

                    val mediaType = when {
                        mimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "m4v", "m4s", "m3u8", "ts") -> MediaType.VIDEO
                        mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "heic") -> MediaType.IMAGE
                        else -> MediaType.AUDIO
                    }

                    val baseUri = when (mediaType) {
                        MediaType.VIDEO -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        MediaType.IMAGE -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        MediaType.AUDIO -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val uri = android.content.ContentUris.withAppendedId(baseUri, id)
                    val uriStr = uri.toString()
                    
                    val isFinished = settings.isFinished(uriStr, name)
                    val playbackPos = settings.getPlaybackPosition(uriStr, name)
                    val lastPlayedTime = settings.getLastPlayedTime(uriStr, name)
                    
                    val tag = if (isFinished) {
                        PlaybackTag.SEEN
                    } else if (playbackPos > 0L) {
                        PlaybackTag.PLAYING
                    } else if (lastPlayedTime > 0L) {
                        PlaybackTag.UNSEEN
                    } else {
                        if (currentTime - dateMs < fifteenDaysMs) PlaybackTag.NEW else PlaybackTag.UNSEEN
                    }

                    val item = MediaItem(
                        id = id,
                        uri = uri,
                        name = name,
                        duration = dur,
                        dateAdded = dateMs,
                        mediaType = mediaType,
                        hasSubtitle = false, // Subtitles not easily extracted this way without checking filesystem
                        tag = tag,
                        size = itemSize
                    )

                    foldersMap.getOrPut(bucketId) { mutableListOf() }.add(item)
                    folderNames[bucketId] = bucketName
                    if (folderPaths[bucketId].isNullOrEmpty() && dataPath.isNotEmpty()) {
                        folderPaths[bucketId] = java.io.File(dataPath).parent ?: ""
                    }
                    val existingDate = folderDates[bucketId] ?: 0L
                    if (dateMs > existingDate) {
                        folderDates[bucketId] = dateMs
                    }
                    folderSizes[bucketId] = (folderSizes[bucketId] ?: 0L) + itemSize
                }
            }
        } catch (e: Exception) {
            LogKeeper.logError("MediaRepository", "Error fetching from MediaStore: ${e.message}", e)
        }

        return foldersMap.map { (bucketId, items) ->
            MediaFolder(
                id = bucketId,
                name = folderNames[bucketId] ?: "Unknown",
                path = folderPaths[bucketId] ?: "",
                dateModified = folderDates[bucketId] ?: 0L,
                totalSize = folderSizes[bucketId] ?: 0L,
                mediaItems = items.sortedByDescending { it.dateAdded }
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun scanDirectoryForFolders(
        treeUri: Uri,
        documentId: String,
        folderName: String,
        folderPath: String,
        extensions: List<String>,
        folders: MutableList<MediaFolder>,
        scannedDocIds: MutableSet<String>,
        durationMap: Map<String, Long>
    ) {
        if (!scannedDocIds.add(documentId)) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val mediaItems = mutableListOf<MediaItem>()
        val subtitleFiles = mutableSetOf<String>()
        val subDirs = mutableListOf<Pair<String, String>>()
        var latestDate = 0L
        var hasNoMedia = false

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = if (idCol != -1) cursor.getString(idCol) ?: continue else continue
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    if (name.equals(".nomedia", ignoreCase = true)) {
                        hasNoMedia = true
                    }

                    if (date > latestDate) latestDate = date

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subDirs.add(Pair(docId, name))
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (extensions.contains(ext) || (ext.isEmpty() && mimeType.startsWith("video/"))) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val mediaType = when {
                                mimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "m4v", "m4s", "m3u8", "ts") -> MediaType.VIDEO
                                mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "heic") -> MediaType.IMAGE
                                else -> MediaType.AUDIO
                            }
                            mediaItems.add(MediaItem(id = docId.hashCode().toLong(), uri = uri, name = name, duration = 0L, dateAdded = date, mediaType = mediaType, hasSubtitle = false, size = size))
                        } else if (ext in listOf("srt", "vtt", "ass", "sub")) {
                            subtitleFiles.add(name.substringBeforeLast('.').lowercase())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogKeeper.logError("MediaRepository", "Error scanning dir: ${documentId}, ${e.message}", e)
        }

        val settingsManager = SettingsManager.getInstance(context)
        if (hasNoMedia && settingsManager.ignoreNoMediaSubfolders.value) {
            return
        }

        if (mediaItems.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val fifteenDaysMs = 15L * 24 * 60 * 60 * 1000
            val settingsManager = SettingsManager.getInstance(context)
            
            val updatedItems = mediaItems.map { item ->
                val baseName = item.name.substringBeforeLast('.').lowercase()
                val hasSub = subtitleFiles.contains(baseName)
                
                // Extract duration safely from map
                val duration = durationMap[item.name] ?: 0L
                
                val uriStr = item.uri.toString()
                val isFinished = settingsManager.isFinished(uriStr, item.name)
                val playbackPos = settingsManager.getPlaybackPosition(uriStr, item.name)
                val lastPlayedTime = settingsManager.getLastPlayedTime(uriStr, item.name)
                
                val tag = if (isFinished) {
                    PlaybackTag.SEEN
                } else if (playbackPos > 0L) {
                    PlaybackTag.PLAYING
                } else if (lastPlayedTime > 0L) {
                    PlaybackTag.UNSEEN
                } else {
                    if (currentTime - item.dateAdded < fifteenDaysMs) PlaybackTag.NEW else PlaybackTag.UNSEEN
                }

                item.copy(hasSubtitle = hasSub, duration = duration, tag = tag)
            }
            folders.add(MediaFolder(documentId, folderName, folderPath, latestDate, updatedItems.sumOf { 0L }, updatedItems.sortedByDescending { it.dateAdded }))
        }

        // Non-recursive: do not scan sub-directories
        // Removed subDirs loop per Alternative A
    }
}
