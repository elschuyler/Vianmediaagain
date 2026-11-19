package shura.media.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import shura.media.LogKeeper
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

object MediaUriResolver {

    private val SAF_VOLUME_PREFIX_REGEX = Regex(
        """^(?:primary|home|raw|document|documents|tree|msf|downloads|video|audio|image|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}):(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private val EXTRA_STRIP_EXTENSIONS: Set<String> = setOf(
        "m4r", "mpg4", "mpeg4", "mpe", "mpv", "nsv", "roq", "svi", "viv", "yuv",
        "mka", "mks", "tta", "wv", "mpc", "ofr", "ofs", "spx", "tak", "weba",
        "bin", "tmp", "part", "crdownload", "download"
    )

    fun cleanDisplayName(rawName: String?, fallback: String = "Media"): String {
        if (rawName.isNullOrBlank()) return fallback
        var cleaned = try {
            Uri.decode(rawName)
        } catch (_: Exception) {
            rawName
        }.trim()
        cleaned = cleaned.substringBefore('?').substringBefore('#').trim()

        val prefixMatch = SAF_VOLUME_PREFIX_REGEX.matchEntire(cleaned)
        if (prefixMatch != null) {
            cleaned = prefixMatch.groupValues[1]
        }
        cleaned = cleaned.substringAfterLast('/').substringAfterLast('\\').trim()

        val innerPrefixMatch = SAF_VOLUME_PREFIX_REGEX.matchEntire(cleaned)
        if (innerPrefixMatch != null) {
            cleaned = innerPrefixMatch.groupValues[1].trim()
        }
        if (cleaned.isBlank()) return fallback

        val lastDot = cleaned.lastIndexOf('.')
        if (lastDot > 0 && lastDot < cleaned.length - 1) {
            val extLower = cleaned.substring(lastDot + 1).lowercase(java.util.Locale.ROOT)
            val isKnownExt = extLower in COMMON_VIDEO_EXTENSIONS ||
                extLower in COMMON_AUDIO_EXTENSIONS ||
                extLower in COMMON_IMAGE_EXTENSIONS ||
                extLower in COMMON_SUBTITLE_EXTENSIONS ||
                extLower in EXTRA_STRIP_EXTENSIONS
            if (isKnownExt) {
                val base = cleaned.substring(0, lastDot).trim()
                if (base.isNotEmpty()) {
                    cleaned = base
                }
            }
        }
        return cleaned.ifBlank { fallback }
    }

    fun extractCleanTitleFromUri(uri: Uri?, fallback: String = "Media"): String {
        if (uri == null) return fallback
        val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
        if (scheme == "file" || scheme == null || uri.path?.startsWith("/") == true) {
            val path = try { Uri.decode(uri.path ?: "") } catch (_: Exception) { uri.path ?: "" }
            if (path.isNotBlank()) {
                return cleanDisplayName(path, fallback)
            }
        }
        val lastSeg = uri.lastPathSegment
        if (!lastSeg.isNullOrBlank()) {
            val candidate = cleanDisplayName(lastSeg, "")
            if (candidate.isNotBlank()) return candidate
        }
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val candidate = cleanDisplayName(path, "")
            if (candidate.isNotBlank()) return candidate
        }
        return uri.host?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun queryCleanDisplayName(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
        if (scheme == "file" || scheme == null || uri.path?.startsWith("/") == true) {
            val decodedPath = try { Uri.decode(uri.path ?: "") } catch (_: Exception) { uri.path ?: "" }
            val fileName = File(decodedPath).name
            if (fileName.isNotBlank()) {
                return cleanDisplayName(fileName)
            }
        }
        if (scheme == "content") {
            val columns = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            for (col in columns) {
                try {
                    context.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(col)
                            if (idx != -1) {
                                val raw = cursor.getString(idx)
                                if (!raw.isNullOrBlank()) {
                                    return cleanDisplayName(raw)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Checks whether the given URI can be opened directly by ContentResolver.
     */
    fun isUriDirectlyReadable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves external storage document URIs, SAF tree child URIs, or restricted
     * direct file paths into an authorized, playable MediaStore or local File URI.
     */
    fun resolveToPlayableUri(context: Context, inputUri: Uri): Uri {
        val scheme = inputUri.scheme
        val uriStr = inputUri.toString()

        // 1. If it's a file scheme or raw POSIX path
        if (scheme == "file" || scheme == null || uriStr.startsWith("/")) {
            val directPath = when {
                uriStr.startsWith("file://") -> uriStr.removePrefix("file://")
                uriStr.startsWith("file:/") -> uriStr.removePrefix("file:")
                uriStr.startsWith("file:") -> uriStr.removePrefix("file:")
                else -> inputUri.path ?: uriStr
            }
            val decodedPath = Uri.decode(directPath)
            val candidateFile = File(decodedPath)
            if (candidateFile.exists() && candidateFile.canRead()) {
                return Uri.fromFile(candidateFile)
            }
            // Scoped storage fallback: resolve POSIX path to MediaStore content URI
            val mediaStoreUri = resolveFilePathToMediaStoreUri(context, decodedPath)
            if (mediaStoreUri != null && isUriDirectlyReadable(context, mediaStoreUri)) {
                LogKeeper.log("Resolved raw file path to MediaStore URI: $mediaStoreUri", "MediaUriResolver")
                return mediaStoreUri
            }
            return inputUri
        }

        // 2. If it's a content URI
        if (scheme == "content") {
            // If already readable directly, keep it
            if (isUriDirectlyReadable(context, inputUri)) {
                return inputUri
            }

            val authority = inputUri.authority ?: ""

            // Handle ExternalStorageProvider document URIs:
            // e.g. content://com.android.externalstorage.documents/document/primary%3AMovies%2Fvideo.mp4
            if (authority.contains("externalstorage.documents") || authority.contains("externalstorage")) {
                try {
                    val docId = try {
                        if (DocumentsContract.isDocumentUri(context, inputUri)) {
                            DocumentsContract.getDocumentId(inputUri)
                        } else {
                            Uri.decode(inputUri.lastPathSegment ?: "")
                        }
                    } catch (e: Exception) {
                        Uri.decode(inputUri.lastPathSegment ?: "")
                    }

                    val fullPath = when {
                        docId.startsWith("primary:", ignoreCase = true) -> {
                            val subPath = docId.substringAfter(':')
                            "/storage/emulated/0/$subPath"
                        }
                        docId.contains(":") -> {
                            val volumeId = docId.substringBefore(':')
                            val subPath = docId.substringAfter(':')
                            "/storage/$volumeId/$subPath"
                        }
                        else -> null
                    }

                    if (fullPath != null) {
                        val file = File(fullPath)
                        if (file.exists() && file.canRead()) {
                            return Uri.fromFile(file)
                        }
                        // Query MediaStore for this physical path
                        val mediaStoreUri = resolveFilePathToMediaStoreUri(context, fullPath, file.name)
                        if (mediaStoreUri != null && isUriDirectlyReadable(context, mediaStoreUri)) {
                            LogKeeper.log("Resolved ExternalStorageProvider URI to MediaStore URI: $mediaStoreUri", "MediaUriResolver")
                            return mediaStoreUri
                        }
                    }
                } catch (e: Exception) {
                    LogKeeper.logError("MediaUriResolver", "Failed to resolve ExternalStorageProvider URI: $uriStr", e)
                }
            }

            // Handle MediaDocumentsProvider:
            // e.g. content://com.android.providers.media.documents/document/video%3A12345
            if (authority.contains("media.documents")) {
                try {
                    val docId = if (DocumentsContract.isDocumentUri(context, inputUri)) {
                        DocumentsContract.getDocumentId(inputUri)
                    } else {
                        inputUri.lastPathSegment ?: ""
                    }
                    val parts = docId.split(":")
                    if (parts.size >= 2) {
                        val type = parts[0].lowercase()
                        val id = parts[1].toLongOrNull()
                        if (id != null) {
                            val targetUri = when (type) {
                                "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                                "audio" -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                                else -> ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                            }
                            if (isUriDirectlyReadable(context, targetUri)) {
                                LogKeeper.log("Resolved media.documents URI to MediaStore URI: $targetUri", "MediaUriResolver")
                                return targetUri
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogKeeper.logError("MediaUriResolver", "Failed to resolve media.documents URI: $uriStr", e)
                }
            }

            // Fallback: Query display name and attempt MediaStore lookup
            try {
                val displayName = getDisplayName(context, inputUri)
                if (!displayName.isNullOrBlank()) {
                    val mediaStoreUri = findInMediaStoreByName(context, displayName)
                    if (mediaStoreUri != null && isUriDirectlyReadable(context, mediaStoreUri)) {
                        LogKeeper.log("Resolved content URI by display name to MediaStore URI: $mediaStoreUri", "MediaUriResolver")
                        return mediaStoreUri
                    }
                }
            } catch (e: Exception) {}
        }

        return inputUri
    }

    /**
     * Resolves a raw POSIX file path against MediaStore.Files or MediaStore.Downloads.
     */
    fun resolveFilePathToMediaStoreUri(context: Context, directPath: String, optionalName: String? = null): Uri? {
        val fileName = optionalName ?: File(directPath).name
        if (fileName.isBlank()) return null

        // 1. Query MediaStore.Files by DATA (exact path)
        try {
            val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sel = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selArgs = arrayOf(directPath)
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel, selArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    return buildMediaStoreUri(id, mediaType)
                }
            }
        } catch (e: Exception) {}

        // 2. Query MediaStore.Files by DISPLAY_NAME (refuse ambiguous multi-matches without exact path match)
        try {
            val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DATA)
            val sel = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val selArgs = arrayOf(fileName)
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel, selArgs, null)?.use { cursor ->
                val matches = mutableListOf<Pair<Long, Int>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    val data = cursor.getString(2)
                    if (!data.isNullOrBlank() && data.equals(directPath, ignoreCase = true)) {
                        return buildMediaStoreUri(id, mediaType)
                    }
                    matches.add(id to mediaType)
                }
                if (matches.size == 1) {
                    val (id, mediaType) = matches[0]
                    return buildMediaStoreUri(id, mediaType)
                } else if (matches.size > 1) {
                    LogKeeper.logWarn("MediaUriResolver", "Ambiguous MediaStore DISPLAY_NAME lookup (${matches.size} matches); refusing guess.")
                }
            }
        } catch (e: Exception) {}

        // 3. Query MediaStore.Downloads on Android 10+ (API 29+) (only if unique)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val proj = arrayOf(MediaStore.MediaColumns._ID)
                val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selArgs = arrayOf(fileName)
                context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, selArgs, null)?.use { cursor ->
                    if (cursor.count == 1 && cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    } else if (cursor.count > 1) {
                        LogKeeper.logWarn("MediaUriResolver", "Ambiguous MediaStore.Downloads lookup (${cursor.count} matches); refusing guess.")
                    }
                }
            } catch (e: Exception) {}
        }

        return null
    }

    private fun buildMediaStoreUri(id: Long, mediaType: Int): Uri {
        return when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            else ->
                ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        }
    }

    private fun findInMediaStoreByName(context: Context, displayName: String): Uri? {
        try {
            val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            val sel = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val selArgs = arrayOf(displayName)
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel, selArgs, null)?.use { cursor ->
                if (cursor.count == 1 && cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    return buildMediaStoreUri(id, mediaType)
                } else if (cursor.count > 1) {
                    LogKeeper.logWarn("MediaUriResolver", "Ambiguous fallback MediaStore lookup (${cursor.count} matches); refusing guess.")
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return cursor.getString(idx)
                }
            }
        } catch (e: Exception) {}
        return uri.lastPathSegment
    }

    private val APPROVED_INTERNAL_TEMP_PREFIXES = listOf(
        "edited_", "editor_converted_", "ffmpeg_", "join_", "preconverted_", "audio_add_", "Temp_"
    )

    private fun extractRawFilePath(uriOrPath: String): String? {
        if (uriOrPath.isBlank()) return null
        val uri = try { Uri.parse(uriOrPath) } catch (_: Exception) { null }
        val scheme = uri?.scheme?.lowercase(java.util.Locale.ROOT)
        val isFileLike = scheme == "file" || uriOrPath.startsWith("file:") || uriOrPath.startsWith("/")
        if (!isFileLike) return null
        val directPath = when {
            uriOrPath.startsWith("file://") -> uriOrPath.removePrefix("file://")
            uriOrPath.startsWith("file:/") -> uriOrPath.removePrefix("file:")
            uriOrPath.startsWith("file:") -> uriOrPath.removePrefix("file:")
            else -> uri?.path ?: uriOrPath
        }
        return try { Uri.decode(directPath) } catch (_: Exception) { directPath }
    }

    fun isAppPrivatePath(context: Context, rawPath: String?, allowApprovedCacheTemp: Boolean): Boolean {
        if (rawPath.isNullOrBlank()) return false
        val canonicalTarget = try {
            File(rawPath).canonicalPath
        } catch (_: Exception) {
            rawPath
        }
        val appContext = context.applicationContext
        val cacheCanonical = try { appContext.cacheDir.canonicalPath } catch (_: Exception) { appContext.cacheDir.absolutePath }
        val extCacheCanonical = try { appContext.externalCacheDir?.canonicalPath } catch (_: Exception) { appContext.externalCacheDir?.absolutePath }

        if (allowApprovedCacheTemp) {
            val fileObj = File(canonicalTarget)
            val parentCanonical = fileObj.parentFile?.let {
                try { it.canonicalPath } catch (_: Exception) { it.absolutePath }
            }
            if ((parentCanonical == cacheCanonical || (extCacheCanonical != null && parentCanonical == extCacheCanonical)) &&
                APPROVED_INTERNAL_TEMP_PREFIXES.any { fileObj.name.startsWith(it) }
            ) {
                return false
            }
        }

        val blockedRoots = buildList {
            try { add(appContext.filesDir.canonicalPath) } catch (_: Exception) { add(appContext.filesDir.absolutePath) }
            add(cacheCanonical)
            try { appContext.codeCacheDir?.canonicalPath?.let { add(it) } } catch (_: Exception) {}
            try { appContext.noBackupFilesDir?.canonicalPath?.let { add(it) } } catch (_: Exception) {}
            try { appContext.dataDir.canonicalPath.let { add(it) } } catch (_: Exception) {}
            add("/data/data/${appContext.packageName}")
            add("/data/user/0/${appContext.packageName}")
        }
        if (blockedRoots.any { root ->
                canonicalTarget == root || canonicalTarget.startsWith("$root/")
            }) {
            return true
        }
        val lower = canonicalTarget.lowercase(java.util.Locale.ROOT)
        return (lower.contains("/${appContext.packageName.lowercase(java.util.Locale.ROOT)}/") &&
            (lower.contains("/shared_prefs/") || lower.contains("/databases/") || lower.contains("/files/") || lower.contains("/logs/")))
    }

    fun isSafeExternalUri(context: Context, uriOrPath: String?): Boolean {
        if (uriOrPath.isNullOrBlank()) return false
        val parsed = try { Uri.parse(uriOrPath) } catch (_: Exception) { return false }
        val scheme = parsed.scheme?.lowercase(java.util.Locale.ROOT)
        val allowedSchemes = setOf("content", "file", "http", "https", "rtsp", "rtmp", "rtmpe", "rtmps", "rtp")
        if (scheme != null && scheme !in allowedSchemes) return false
        if (scheme == null && !uriOrPath.startsWith("/")) return false

        if (scheme == "content") {
            val auth = parsed.authority?.lowercase(java.util.Locale.ROOT) ?: ""
            val pkg = context.applicationContext.packageName.lowercase(java.util.Locale.ROOT)
            if (auth == pkg || auth.startsWith("$pkg.")) {
                return false
            }
        }
        val rawPath = extractRawFilePath(uriOrPath)
        if (rawPath != null && isAppPrivatePath(context, rawPath, allowApprovedCacheTemp = false)) {
            return false
        }
        return true
    }

    fun safeStartActivity(context: Context, intent: android.content.Intent, fallbackToast: String? = null): Boolean {
        return try {
            if (context !is android.app.Activity) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            LogKeeper.logWarn("MediaUriResolver", "No activity found to handle intent: ${intent.action}")
            showSafeToast(context, fallbackToast ?: "Feature not supported on this device")
            false
        } catch (e: SecurityException) {
            LogKeeper.logError("MediaUriResolver", "SecurityException starting activity: ${e.message}", e)
            showSafeToast(context, fallbackToast ?: "Permission denied launching action")
            false
        } catch (e: Exception) {
            LogKeeper.logError("MediaUriResolver", "Failed starting activity: ${e.message}", e)
            if (fallbackToast != null) {
                showSafeToast(context, fallbackToast)
            }
            false
        }
    }

    private fun showSafeToast(context: Context, message: String) {
        val appContext = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    /**
     * Safely opens an InputStream for FFmpeg processing, handling Scoped Storage,
     * Document URIs, and POSIX path permission restrictions.
     */
    fun openInputStreamSafely(context: Context, uriOrPath: String): InputStream {
        val uri = Uri.parse(uriOrPath)
        val isFileUri = uri.scheme == "file" || uriOrPath.startsWith("file:") || uriOrPath.startsWith("/")

        if (!isFileUri) {
            if (!isSafeExternalUri(context, uriOrPath)) {
                throw SecurityException("Access to protected URI is denied")
            }
            // First attempt direct ContentResolver stream
            try {
                val directStream = context.contentResolver.openInputStream(uri)
                if (directStream != null) return directStream
            } catch (e: Exception) {
                LogKeeper.log("Direct content stream failed for $uriOrPath: ${e.message}. Attempting fallback resolution.", "MediaUriResolver")
            }

            // Fallback: Resolve to playable MediaStore or physical URI
            val resolvedUri = resolveToPlayableUri(context, uri)
            if (resolvedUri != uri) {
                val resolvedPath = extractRawFilePath(resolvedUri.toString())
                if (resolvedPath != null && isAppPrivatePath(context, resolvedPath, allowApprovedCacheTemp = false)) {
                    throw SecurityException("Access to app-private resolved path is denied")
                }
                try {
                    val resolvedStream = context.contentResolver.openInputStream(resolvedUri)
                    if (resolvedStream != null) return resolvedStream
                } catch (e: Exception) {
                    LogKeeper.log("Resolved content stream failed for $resolvedUri: ${e.message}", "MediaUriResolver")
                }
            }
        } else {
            val directPath = when {
                uriOrPath.startsWith("file://") -> uriOrPath.removePrefix("file://")
                uriOrPath.startsWith("file:/") -> uriOrPath.removePrefix("file:")
                uriOrPath.startsWith("file:") -> uriOrPath.removePrefix("file:")
                else -> uri.path ?: uriOrPath
            }
            val decodedPath = Uri.decode(directPath)
            if (isAppPrivatePath(context, decodedPath, allowApprovedCacheTemp = true)) {
                throw SecurityException("Access to app-private storage path is denied")
            }
            val candidateFile = File(decodedPath)

            // Direct POSIX stream if readable
            if (candidateFile.exists() && candidateFile.canRead()) {
                try {
                    return FileInputStream(candidateFile)
                } catch (e: Exception) {}
            }

            // Scoped storage fallback via MediaStore resolution
            val mediaStoreUri = resolveFilePathToMediaStoreUri(context, decodedPath, candidateFile.name)
            if (mediaStoreUri != null) {
                try {
                    val stream = context.contentResolver.openInputStream(mediaStoreUri)
                    if (stream != null) return stream
                } catch (e: Exception) {
                    LogKeeper.log("MediaStore stream for $decodedPath failed: ${e.message}", "MediaUriResolver")
                }
            }

            // Try ParcelFileDescriptor fallback on raw path if available
            try {
                val pfd = ParcelFileDescriptor.open(candidateFile, ParcelFileDescriptor.MODE_READ_ONLY)
                if (pfd != null) {
                    return ParcelFileDescriptor.AutoCloseInputStream(pfd)
                }
            } catch (e: Exception) {}
        }

        throw FileNotFoundException("Unable to resolve or read input file via POSIX or ContentResolver: $uriOrPath")
    }
}
