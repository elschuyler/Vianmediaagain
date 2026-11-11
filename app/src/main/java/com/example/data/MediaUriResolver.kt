package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.LogKeeper
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

object MediaUriResolver {

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

        // 2. Query MediaStore.Files by DISPLAY_NAME
        try {
            val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DATA)
            val sel = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val selArgs = arrayOf(fileName)
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel, selArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    val data = cursor.getString(2)
                    // If multiple files with same name exist, prefer matching path or return first
                    if (data.isNullOrBlank() || data.equals(directPath, ignoreCase = true)) {
                        return buildMediaStoreUri(id, mediaType)
                    }
                }
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    return buildMediaStoreUri(id, mediaType)
                }
            }
        } catch (e: Exception) {}

        // 3. Query MediaStore.Downloads on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val proj = arrayOf(MediaStore.MediaColumns._ID)
                val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selArgs = arrayOf(fileName)
                context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, selArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
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
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val mediaType = cursor.getInt(1)
                    return buildMediaStoreUri(id, mediaType)
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

    /**
     * Safely opens an InputStream for FFmpeg processing, handling Scoped Storage,
     * Document URIs, and POSIX path permission restrictions.
     */
    fun openInputStreamSafely(context: Context, uriOrPath: String): InputStream {
        val uri = Uri.parse(uriOrPath)
        val isFileUri = uri.scheme == "file" || uriOrPath.startsWith("file:") || uriOrPath.startsWith("/")

        if (!isFileUri) {
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
