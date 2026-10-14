package com.example.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.example.LogKeeper
import java.io.File
import java.util.Locale

/**
 * Non-blocking sibling subtitle auto-detection utility inspired by Next Player.
 * Scans the enclosing folder or SAF directory for subtitle files (.srt, .vtt, .ass, .ssa, .sub)
 * sharing the video's basename, builds MediaItem.SubtitleConfiguration instances,
 * and binds them directly at preparation time so subtitles are immediately available.
 */
object SubtitleAutoDetector {

    private val SUPPORTED_SUBTITLE_EXTENSIONS = setOf("srt", "sub", "vtt", "ass", "ssa")

    /**
     * Maps subtitle file extensions to proper Media3 MimeTypes.
     * .srt, .sub -> MimeTypes.APPLICATION_SUBRIP
     * .vtt -> MimeTypes.TEXT_VTT
     * .ass, .ssa -> MimeTypes.TEXT_SSA
     */
    fun getMimeTypeForExtension(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "srt", "sub" -> MimeTypes.APPLICATION_SUBRIP
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    /**
     * Extracts an ISO language tag from a subtitle file name if present.
     * e.g., "video.en.srt" -> "en", "video.eng.srt" -> "eng"
     */
    fun extractLanguageTag(filenameWithoutExt: String, videoBaseName: String): String? {
        val remainder = filenameWithoutExt.removePrefix(videoBaseName).trim('.', '_', '-', ' ')
        if (remainder.isNotEmpty()) {
            val parts = remainder.split('.', '_', '-', ' ')
            for (part in parts) {
                val p = part.lowercase(Locale.ROOT)
                if (p.length in 2..3 && p.all { it.isLetter() }) {
                    return p
                }
            }
        }
        return null
    }

    /**
     * Resolves the primary base name of a video without its extension.
     */
    fun resolveBaseName(context: Context, uri: Uri): String? {
        var displayName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) displayName = cursor.getString(idx)
                    }
                }
            } catch (e: Exception) {}
        }
        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment
        }
        return displayName?.substringBeforeLast('.')?.ifBlank { null }
    }

    /**
     * Scans for sibling subtitle files (.srt, .vtt, .ass, .ssa, .sub) matching
     * the video's basename in the same folder or directory.
     * Performs fast, non-blocking resolution.
     */
    fun detectSiblingSubtitles(context: Context, videoUri: Uri): List<MediaItem.SubtitleConfiguration> {
        val configs = mutableListOf<MediaItem.SubtitleConfiguration>()
        try {
            val videoBaseName = resolveBaseName(context, videoUri) ?: return emptyList()

            // 1. Direct file:// URI or local disk path
            if (videoUri.scheme == "file" || (videoUri.scheme == null && videoUri.path?.startsWith("/") == true)) {
                val file = File(videoUri.path ?: "")
                val parentDir = file.parentFile
                if (parentDir != null && parentDir.isDirectory) {
                    configs.addAll(scanDirectoryForSubtitles(parentDir, videoBaseName))
                }
            } else if (videoUri.scheme == "content") {
                // Try resolving filesystem path via MediaStore.MediaColumns.DATA first
                var resolvedFilePath: String? = null
                try {
                    context.contentResolver.query(
                        videoUri,
                        arrayOf(MediaStore.MediaColumns.DATA),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIdx != -1) {
                                resolvedFilePath = cursor.getString(dataIdx)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handled gracefully under Scoped Storage
                }

                if (!resolvedFilePath.isNullOrBlank()) {
                    val file = File(resolvedFilePath)
                    val parentDir = file.parentFile
                    if (parentDir != null && parentDir.isDirectory) {
                        configs.addAll(scanDirectoryForSubtitles(parentDir, videoBaseName))
                    }
                }

                // If direct filesystem scan yielded nothing, query MediaStore by BUCKET_ID
                if (configs.isEmpty()) {
                    configs.addAll(queryMediaStoreSiblingSubtitles(context, videoUri, videoBaseName))
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("SubtitleAutoDetector: Handled sibling detection error: ${e.message}", "SubtitleAutoDetector")
        }
        return configs
    }

    private fun scanDirectoryForSubtitles(
        parentDir: File,
        videoBaseName: String
    ): List<MediaItem.SubtitleConfiguration> {
        val result = mutableListOf<MediaItem.SubtitleConfiguration>()
        val files = parentDir.listFiles() ?: return emptyList()
        val normalizedVideoBase = videoBaseName.lowercase(Locale.ROOT).trim()

        var first = true
        for (f in files) {
            if (!f.isFile) continue
            val ext = f.extension.lowercase(Locale.ROOT)
            if (ext !in SUPPORTED_SUBTITLE_EXTENSIONS) continue

            val nameWithoutExt = f.nameWithoutExtension.lowercase(Locale.ROOT).trim()
            if (nameWithoutExt.startsWith(normalizedVideoBase) || normalizedVideoBase.startsWith(nameWithoutExt)) {
                val mime = getMimeTypeForExtension(ext)
                val lang = extractLanguageTag(f.nameWithoutExtension, videoBaseName)
                val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                    .setMimeType(mime)
                    .setLabel(f.name)
                    .setLanguage(lang)
                    .setSelectionFlags(if (first) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
                result.add(config)
                first = false
                LogKeeper.log("SubtitleAutoDetector: Auto-detected sibling subtitle: ${f.name}", "SubtitleAutoDetector")
            }
        }
        return result
    }

    private fun queryMediaStoreSiblingSubtitles(
        context: Context,
        videoUri: Uri,
        videoBaseName: String
    ): List<MediaItem.SubtitleConfiguration> {
        val result = mutableListOf<MediaItem.SubtitleConfiguration>()
        try {
            var bucketId: String? = null
            context.contentResolver.query(
                videoUri,
                arrayOf(MediaStore.Video.Media.BUCKET_ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                    if (idx != -1) bucketId = cursor.getString(idx)
                }
            }

            if (bucketId != null) {
                val filesUri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME
                )
                val selection = "${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
                val selectionArgs = arrayOf(bucketId)
                val normalizedVideoBase = videoBaseName.lowercase(Locale.ROOT).trim()

                context.contentResolver.query(
                    filesUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    var first = true

                    while (cursor.moveToNext()) {
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null ?: continue
                        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (ext in SUPPORTED_SUBTITLE_EXTENSIONS) {
                            val nameWithoutExt = name.substringBeforeLast('.').lowercase(Locale.ROOT).trim()
                            if (nameWithoutExt.startsWith(normalizedVideoBase) || normalizedVideoBase.startsWith(nameWithoutExt)) {
                                val id = cursor.getLong(idCol)
                                val subUri = ContentUris.withAppendedId(filesUri, id)
                                val mime = getMimeTypeForExtension(ext)
                                val lang = extractLanguageTag(name.substringBeforeLast('.'), videoBaseName)
                                val config = MediaItem.SubtitleConfiguration.Builder(subUri)
                                    .setMimeType(mime)
                                    .setLabel(name)
                                    .setLanguage(lang)
                                    .setSelectionFlags(if (first) C.SELECTION_FLAG_DEFAULT else 0)
                                    .build()
                                result.add(config)
                                first = false
                                LogKeeper.log("SubtitleAutoDetector: Auto-detected MediaStore sibling subtitle: $name", "SubtitleAutoDetector")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogKeeper.log("SubtitleAutoDetector: Handled MediaStore sibling scan error: ${e.message}", "SubtitleAutoDetector")
        }
        return result
    }
}
