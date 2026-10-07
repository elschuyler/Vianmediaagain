package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

import com.example.LogKeeper
import androidx.compose.runtime.Immutable

@Immutable
enum class PlaybackTag {
    NEW, UNSEEN, SEEN, PLAYING
}

@Immutable
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

@Immutable
data class MediaFolder(
    val id: String,
    val name: String,
    val path: String,
    val dateModified: Long,
    val totalSize: Long,
    val mediaItems: List<MediaItem>,
    val hasNew: Boolean = mediaItems.any { it.tag == PlaybackTag.NEW }
) {
    val videoCount: Int get() = mediaItems.size
    val totalDuration: Long get() = mediaItems.sumOf { it.duration }
}

@Immutable
enum class MediaType {
    AUDIO, VIDEO, IMAGE
}

val COMMON_VIDEO_EXTENSIONS: Set<String> = setOf(
    "mp4", "m4v", "mkv", "webm",
    "avi", "mov", "qt",
    "3gp", "3gpp", "3g2", "3gpp2",
    "flv", "f4v",
    "wmv", "asf",
    "ts", "m2ts", "mts", "tp", "trp",
    "vob", "ifo",
    "ogv", "ogg",
    "mpg", "mpeg", "m1v", "m2v", "mp2",
    "m4s", "m3u8", "m3u",
    "divx", "xvid",
    "rm", "rmvb",
    "wtv", "dvr-ms",
    "h264", "h265", "hevc", "264", "265", "avc",
    "av1", "ivf",
    "vp8", "vp9",
    "y4m", "dat", "mod", "tod", "dv"
)

val COMMON_IMAGE_EXTENSIONS: Set<String> = setOf(
    "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp"
)

val COMMON_AUDIO_EXTENSIONS: Set<String> = setOf(
    "mp3", "aac", "wav", "flac", "opus", "m4a", "ogg", "oga", "wma", "m4p", "m4b",
    "alac", "aiff", "aif", "ape", "mid", "midi", "amr", "awb", "dts", "ac3", "eac3", "ec3"
)

val COMMON_SUBTITLE_EXTENSIONS: Set<String> = setOf(
    "srt", "vtt", "ass", "ssa", "sub", "idx", "smi", "ttml"
)

class MediaRepository(private val context: Context) {
    
    companion object {
        private const val CACHE_FILE_NAME = "media_library_cache.json"
        private const val PREFS_NAME = "media_repo_prefs"
        private const val KEY_LAST_SCAN_TS = "last_scan_timestamp"
        const val DAILY_COOLDOWN_MS = 24L * 60 * 60 * 1000L
    }

    fun getLastScanTimestamp(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SCAN_TS, 0L)
    }

    fun setLastScanTimestamp(ts: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SCAN_TS, ts).apply()
    }

    fun isCacheStale(cooldownMs: Long = DAILY_COOLDOWN_MS): Boolean {
        val lastScan = getLastScanTimestamp()
        if (lastScan <= 0L) return true
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (!cacheFile.exists() || cacheFile.length() == 0L) return true
        return (System.currentTimeMillis() - lastScan) > cooldownMs
    }

    @Synchronized
    fun getCachedMediaFolders(): List<MediaFolder>? {
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists() || file.length() == 0L) return null
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val folders = ArrayList<MediaFolder>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val fObj = jsonArray.getJSONObject(i)
                val id = fObj.getString("id")
                val name = fObj.getString("name")
                val path = fObj.optString("path", "")
                val dateModified = fObj.optLong("dateModified", 0L)
                val totalSize = fObj.optLong("totalSize", 0L)
                val itemsArray = fObj.getJSONArray("items")
                val items = ArrayList<MediaItem>(itemsArray.length())
                for (j in 0 until itemsArray.length()) {
                    val iObj = itemsArray.getJSONObject(j)
                    val itemId = iObj.getLong("id")
                    val uriStr = iObj.getString("uri")
                    val itemName = iObj.getString("name")
                    val duration = iObj.optLong("duration", 0L)
                    val dateAdded = iObj.optLong("dateAdded", 0L)
                    val typeStr = iObj.optString("mediaType", "VIDEO")
                    val mediaType = try { MediaType.valueOf(typeStr) } catch (e: Exception) { MediaType.VIDEO }
                    val hasSubtitle = iObj.optBoolean("hasSubtitle", false)
                    val tagStr = iObj.optString("tag", "NEW")
                    val tag = try { PlaybackTag.valueOf(tagStr) } catch (e: Exception) { PlaybackTag.NEW }
                    val size = iObj.optLong("size", 0L)
                    items.add(
                        MediaItem(
                            id = itemId,
                            uri = Uri.parse(uriStr),
                            name = itemName,
                            duration = duration,
                            dateAdded = dateAdded,
                            mediaType = mediaType,
                            hasSubtitle = hasSubtitle,
                            tag = tag,
                            size = size
                        )
                    )
                }
                folders.add(
                    MediaFolder(
                        id = id,
                        name = name,
                        path = path,
                        dateModified = dateModified,
                        totalSize = totalSize,
                        mediaItems = items
                    )
                )
            }
            folders
        } catch (e: Exception) {
            LogKeeper.logError("MediaRepository", "Failed loading cached media folders", e)
            null
        }
    }

    @Synchronized
    fun saveCachedMediaFolders(folders: List<MediaFolder>) {
        try {
            val jsonArray = JSONArray()
            for (folder in folders) {
                val fObj = JSONObject()
                fObj.put("id", folder.id)
                fObj.put("name", folder.name)
                fObj.put("path", folder.path)
                fObj.put("dateModified", folder.dateModified)
                fObj.put("totalSize", folder.totalSize)
                val itemsArray = JSONArray()
                for (item in folder.mediaItems) {
                    val iObj = JSONObject()
                    iObj.put("id", item.id)
                    iObj.put("uri", item.uri.toString())
                    iObj.put("name", item.name)
                    iObj.put("duration", item.duration)
                    iObj.put("dateAdded", item.dateAdded)
                    iObj.put("mediaType", item.mediaType.name)
                    iObj.put("hasSubtitle", item.hasSubtitle)
                    iObj.put("tag", item.tag.name)
                    iObj.put("size", item.size)
                    itemsArray.put(iObj)
                }
                fObj.put("items", itemsArray)
                jsonArray.put(fObj)
            }
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            tempFile.writeText(jsonArray.toString(), Charsets.UTF_8)
            if (!tempFile.renameTo(file)) {
                file.writeText(jsonArray.toString(), Charsets.UTF_8)
                tempFile.delete()
            }
            setLastScanTimestamp(System.currentTimeMillis())
        } catch (e: Exception) {
            LogKeeper.logError("MediaRepository", "Failed saving media cache", e)
        }
    }

    fun getMediaFolder(bucketId: String): MediaFolder? {
        val cached = getCachedMediaFolders()?.find { it.id == bucketId }
        
        // Targeted single-folder query targeting strictly BUCKET_ID = ?
        try {
            val settings = SettingsManager.getInstance(context)
            val exts = settings.extensions.value
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
            val selection = "${android.provider.MediaStore.MediaColumns.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(bucketId)
            val items = mutableListOf<MediaItem>()
            var folderName = cached?.name ?: ""
            var folderPath = cached?.path ?: ""
            var latestDate = cached?.dateModified ?: 0L
            var totalSize = 0L

            context.contentResolver.query(
                android.provider.MediaStore.Files.getContentUri("external"),
                projection, selection, selectionArgs, "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DURATION)
                val dateCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val bucketNameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)

                val currentTime = System.currentTimeMillis()
                val fifteenDaysMs = 15L * 24 * 60 * 60 * 1000

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                    if (id == -1L) continue
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val dateMs = dateSec * 1000
                    val bName = if (bucketNameCol != -1) cursor.getString(bucketNameCol) ?: "" else ""
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val itemSize = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""

                    if (folderName.isEmpty() && bName.isNotEmpty()) folderName = bName
                    if (folderPath.isEmpty() && dataPath.isNotEmpty()) {
                        folderPath = java.io.File(dataPath).parent ?: ""
                    }
                    if (dateMs > latestDate) latestDate = dateMs
                    totalSize += itemSize

                    val ext = name.substringAfterLast('.', "").lowercase()
                    val isExcludedExt = exts.isNotEmpty() && !exts.contains(ext)
                    if (isExcludedExt) continue

                    val isVideo = mime.startsWith("video/") || ext in COMMON_VIDEO_EXTENSIONS
                    val isImage = mime.startsWith("image/") || ext in COMMON_IMAGE_EXTENSIONS
                    val mediaType = when {
                        isVideo -> MediaType.VIDEO
                        isImage -> MediaType.IMAGE
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

                    items.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            name = name,
                            duration = dur,
                            dateAdded = dateMs,
                            mediaType = mediaType,
                            hasSubtitle = false,
                            tag = tag,
                            size = itemSize
                        )
                    )
                }
            }
            if (items.isNotEmpty()) {
                return MediaFolder(
                    id = bucketId,
                    name = if (folderName.isNotEmpty()) folderName else (cached?.name ?: "Folder"),
                    path = folderPath,
                    dateModified = latestDate,
                    totalSize = totalSize,
                    mediaItems = items.sortedByDescending { it.dateAdded }
                )
            }
        } catch (e: Exception) {
            LogKeeper.logError("MediaRepository", "Error targeted query for bucket: $bucketId", e)
        }
        return cached
    }

    fun getMediaFolders(forceRefresh: Boolean = false): List<MediaFolder> {
        if (!forceRefresh) {
            val cached = getCachedMediaFolders()
            if (!cached.isNullOrEmpty()) {
                return cached
            }
        }
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
                        mimeType.startsWith("video/") || ext in COMMON_VIDEO_EXTENSIONS -> MediaType.VIDEO
                        mimeType.startsWith("image/") || ext in COMMON_IMAGE_EXTENSIONS -> MediaType.IMAGE
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

        val result = foldersMap.map { (bucketId, items) ->
            MediaFolder(
                id = bucketId,
                name = folderNames[bucketId] ?: "Unknown",
                path = folderPaths[bucketId] ?: "",
                dateModified = folderDates[bucketId] ?: 0L,
                totalSize = folderSizes[bucketId] ?: 0L,
                mediaItems = items.sortedByDescending { it.dateAdded }
            )
        }.sortedBy { it.name.lowercase() }
        saveCachedMediaFolders(result)
        return result
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
                                mimeType.startsWith("video/") || ext in COMMON_VIDEO_EXTENSIONS -> MediaType.VIDEO
                                mimeType.startsWith("image/") || ext in COMMON_IMAGE_EXTENSIONS -> MediaType.IMAGE
                                else -> MediaType.AUDIO
                            }
                            mediaItems.add(MediaItem(id = docId.hashCode().toLong(), uri = uri, name = name, duration = 0L, dateAdded = date, mediaType = mediaType, hasSubtitle = false, size = size))
                        } else if (ext in COMMON_SUBTITLE_EXTENSIONS) {
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
