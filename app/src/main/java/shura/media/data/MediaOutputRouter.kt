package shura.media.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import shura.media.LogKeeper
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

data class OutputTarget(
    val uri: Uri?,
    val stream: OutputStream?,
    val directFile: File?,
    val savedToOriginalFolder: Boolean,
    val usesMediaStoreInsert: Boolean,
    val fallbackReason: String? = null
)

object MediaOutputRouter {

    private val TRAILING_SUFFIX_REGEX = Regex(
        """^(.*?)(?:_(?:edited|compressed))(?:\s*\(\d+\))?$""",
        RegexOption.IGNORE_CASE
    )

    fun buildSuffixedFileName(rawStem: String, suffix: String, ext: String): String {
        val cleanExt = ext.trim().trimStart('.').lowercase(Locale.ROOT).ifEmpty { "bin" }
        val trimmedStem = rawStem.trim().ifEmpty { "edited_${System.currentTimeMillis()}" }
        val cleanSuffix = suffix.trim().trimStart('_').ifEmpty { "edited" }
        val normalizedStem = if (cleanSuffix.equals("edited", ignoreCase = true) || cleanSuffix.equals("compressed", ignoreCase = true)) {
            val match = TRAILING_SUFFIX_REGEX.matchEntire(trimmedStem)
            val candidateBase = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (candidateBase.isNotEmpty()) candidateBase else trimmedStem
        } else {
            trimmedStem
        }
        return "${normalizedStem}_${cleanSuffix}.${cleanExt}"
    }

    fun openOutputTarget(
        context: Context,
        sourceUriStr: String?,
        fileName: String,
        mimeType: String,
        preferOriginalFolder: Boolean,
        fallbackOutputTreeUri: String?,
        defaultRelativePath: String,
        defaultCollectionLegacy: Uri,
        defaultCollectionQ: Uri
    ): OutputTarget {
        var fallbackReason: String? = null

        if (preferOriginalFolder && !sourceUriStr.isNullOrBlank()) {
            val originalAttempt = tryOpenOriginalFolderTarget(
                context = context,
                sourceUriStr = sourceUriStr,
                fileName = fileName,
                mimeType = mimeType
            )
            if (originalAttempt.first != null) {
                return originalAttempt.first!!
            }
            fallbackReason = originalAttempt.second ?: "Original folder is protected by Android"
            LogKeeper.log("Original-folder save fell back ($fallbackReason); routing to Output Folder.", "MediaOutputRouter")
        }

        // Fallback 1: Configured SAF Output Folder
        if (!fallbackOutputTreeUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(fallbackOutputTreeUri)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    docUri,
                    mimeType,
                    fileName
                )
                if (createdUri != null) {
                    val out = context.contentResolver.openOutputStream(createdUri)
                    if (out != null) {
                        return OutputTarget(
                            uri = createdUri,
                            stream = out,
                            directFile = null,
                            savedToOriginalFolder = false,
                            usesMediaStoreInsert = false,
                            fallbackReason = fallbackReason
                        )
                    } else {
                        try { DocumentsContract.deleteDocument(context.contentResolver, createdUri) } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                LogKeeper.logError("MediaOutputRouter", "Failed SAF create in Output Folder", e)
            }
        }

        // Fallback 2: Default App MediaStore Folder
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            defaultCollectionQ
        } else {
            defaultCollectionLegacy
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, defaultRelativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return try {
            val insertedUri = context.contentResolver.insert(collection, contentValues)
            val stream = insertedUri?.let { context.contentResolver.openOutputStream(it) }
            if (insertedUri != null && stream == null) {
                try { context.contentResolver.delete(insertedUri, null, null) } catch (_: Exception) {}
            }
            OutputTarget(
                uri = if (stream != null) insertedUri else null,
                stream = stream,
                directFile = null,
                savedToOriginalFolder = false,
                usesMediaStoreInsert = stream != null,
                fallbackReason = fallbackReason
            )
        } catch (e: Exception) {
            LogKeeper.logError("MediaOutputRouter", "Failed default MediaStore insert", e)
            OutputTarget(
                uri = null,
                stream = null,
                directFile = null,
                savedToOriginalFolder = false,
                usesMediaStoreInsert = false,
                fallbackReason = fallbackReason
            )
        }
    }

    fun finalizeOutputTarget(context: Context, target: OutputTarget, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && target.usesMediaStoreInsert && target.uri != null) {
            try {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(target.uri, cv, null, null)
            } catch (_: Exception) {}
        }
        val directPath = target.directFile?.absolutePath
            ?: target.uri?.takeIf { it.scheme == "file" }?.path
        if (!directPath.isNullOrBlank()) {
            try {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(directPath),
                    arrayOf(mimeType),
                    null
                )
            } catch (_: Exception) {}
        }
    }

    fun abortOutputTarget(context: Context, target: OutputTarget) {
        try { target.stream?.close() } catch (_: Exception) {}
        if (target.directFile != null) {
            try {
                if (target.directFile.exists()) target.directFile.delete()
            } catch (_: Exception) {}
            return
        }
        if (target.uri != null) {
            try {
                if (target.usesMediaStoreInsert) {
                    context.contentResolver.delete(target.uri, null, null)
                } else if (DocumentsContract.isDocumentUri(context, target.uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, target.uri)
                }
            } catch (_: Exception) {}
        }
    }

    private data class OriginalLocation(
        val relativePath: String?,
        val volumeName: String?,
        val directDir: File?
    )

    private fun tryOpenOriginalFolderTarget(
        context: Context,
        sourceUriStr: String,
        fileName: String,
        mimeType: String
    ): Pair<OutputTarget?, String?> {
        val loc = resolveOriginalLocation(context, sourceUriStr)
            ?: return Pair(null, "External or read-only source URI hides folder path")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !loc.relativePath.isNullOrBlank()) {
            val msTarget = tryInsertOriginalMediaStoreQ(
                context = context,
                relativePath = loc.relativePath,
                volumeName = loc.volumeName,
                fileName = fileName,
                mimeType = mimeType
            )
            if (msTarget != null) {
                return Pair(msTarget, null)
            }
        }

        // Direct filesystem write (primary path on API 24-28 or legacy-accessible directories)
        val dir = loc.directDir
        if (dir != null && dir.exists() && dir.isDirectory && dir.canWrite() &&
            !MediaUriResolver.isAppPrivatePath(context, dir.absolutePath, allowApprovedCacheTemp = false)
        ) {
            val uniqueFile = buildNonCollidingFile(dir, fileName)
            try {
                val out = FileOutputStream(uniqueFile)
                return Pair(
                    OutputTarget(
                        uri = Uri.fromFile(uniqueFile),
                        stream = out,
                        directFile = uniqueFile,
                        savedToOriginalFolder = true,
                        usesMediaStoreInsert = false,
                        fallbackReason = null
                    ),
                    null
                )
            } catch (e: Exception) {
                try { if (uniqueFile.exists() && uniqueFile.length() == 0L) uniqueFile.delete() } catch (_: Exception) {}
                LogKeeper.logWarn("MediaOutputRouter", "Direct folder write blocked: ${e.javaClass.simpleName}")
            }
        }

        return Pair(null, "Original folder is protected by Android")
    }

    private fun buildNonCollidingFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val stem = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val extSuffix = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 2
        while (candidate.exists() && counter < 10_000) {
            candidate = File(dir, "$stem ($counter)$extSuffix")
            counter++
        }
        return candidate
    }

    private fun tryInsertOriginalMediaStoreQ(
        context: Context,
        relativePath: String,
        volumeName: String?,
        fileName: String,
        mimeType: String
    ): OutputTarget? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val normalizedRel = relativePath.trim().trimStart('/').let {
            if (it.isEmpty() || it.endsWith("/")) it else "$it/"
        }
        if (normalizedRel.isEmpty()) return null

        val firstSeg = normalizedRel.substringBefore('/').lowercase(Locale.ROOT)
        val targetVolume = volumeName?.takeIf { it.isNotBlank() } ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
        if (targetVolume != MediaStore.VOLUME_EXTERNAL_PRIMARY) {
            // Removable/secondary volumes generally restrict non-SAF MediaStore inserts across OEMs; check safely
            try {
                if (targetVolume !in MediaStore.getExternalVolumeNames(context)) return null
            } catch (_: Exception) {
                return null
            }
        }

        val collection: Uri = when {
            firstSeg == "download" -> MediaStore.Downloads.getContentUri(targetVolume)
            mimeType.startsWith("video/") && firstSeg in setOf("dcim", "movies", "pictures") ->
                MediaStore.Video.Media.getContentUri(targetVolume)
            mimeType.startsWith("image/") && firstSeg in setOf("dcim", "pictures") ->
                MediaStore.Images.Media.getContentUri(targetVolume)
            mimeType.startsWith("audio/") && firstSeg in setOf(
                "music", "podcasts", "ringtones", "alarms", "notifications", "audiobooks", "recordings"
            ) -> MediaStore.Audio.Media.getContentUri(targetVolume)
            else -> return null
        }

        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedRel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            val insertedUri = context.contentResolver.insert(collection, cv) ?: return null
            val stream = try {
                context.contentResolver.openOutputStream(insertedUri)
            } catch (e: Exception) {
                null
            }
            if (stream == null) {
                try { context.contentResolver.delete(insertedUri, null, null) } catch (_: Exception) {}
                null
            } else {
                OutputTarget(
                    uri = insertedUri,
                    stream = stream,
                    directFile = null,
                    savedToOriginalFolder = true,
                    usesMediaStoreInsert = true,
                    fallbackReason = null
                )
            }
        } catch (e: Exception) {
            LogKeeper.logWarn("MediaOutputRouter", "MediaStore original-folder insert rejected: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun resolveOriginalLocation(context: Context, sourceUriStr: String): OriginalLocation? {
        val parsed = try { Uri.parse(sourceUriStr) } catch (_: Exception) { return null }
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        val primaryRoot = try { Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') } catch (_: Exception) { "/storage/emulated/0" }

        // 1. POSIX file:// or raw path
        if (scheme == "file" || scheme == null || sourceUriStr.startsWith("/")) {
            val rawPath = when {
                sourceUriStr.startsWith("file://") -> sourceUriStr.removePrefix("file://")
                sourceUriStr.startsWith("file:/") -> sourceUriStr.removePrefix("file:")
                sourceUriStr.startsWith("file:") -> sourceUriStr.removePrefix("file:")
                else -> parsed.path ?: sourceUriStr
            }
            val decoded = try { Uri.decode(rawPath) } catch (_: Exception) { rawPath }
            if (MediaUriResolver.isAppPrivatePath(context, decoded, allowApprovedCacheTemp = false)) {
                return null
            }
            val parentDir = File(decoded).parentFile ?: return null
            val rel = deriveRelativePath(parentDir.absolutePath, primaryRoot)
            return OriginalLocation(
                relativePath = rel,
                volumeName = if (rel != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else null,
                directDir = parentDir
            )
        }

        if (scheme != "content") return null
        if (!MediaUriResolver.isSafeExternalUri(context, sourceUriStr)) return null

        val authority = parsed.authority?.lowercase(Locale.ROOT) ?: ""

        // 2. ExternalStorageProvider document URI (primary:Folder/file.ext)
        if (authority.contains("externalstorage")) {
            try {
                val docId = if (DocumentsContract.isDocumentUri(context, parsed)) {
                    DocumentsContract.getDocumentId(parsed)
                } else {
                    Uri.decode(parsed.lastPathSegment ?: "")
                }
                if (docId.startsWith("primary:", ignoreCase = true)) {
                    val subPath = docId.substringAfter(':').trimStart('/')
                    val parentRel = if (subPath.contains('/')) subPath.substringBeforeLast('/') else ""
                    if (parentRel.isNotEmpty()) {
                        val dir = File("$primaryRoot/$parentRel")
                        return OriginalLocation(
                            relativePath = "$parentRel/",
                            volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.VOLUME_EXTERNAL_PRIMARY else null,
                            directDir = dir
                        )
                    }
                } else if (docId.contains(':')) {
                    val volId = docId.substringBefore(':')
                    val subPath = docId.substringAfter(':').trimStart('/')
                    val parentRel = if (subPath.contains('/')) subPath.substringBeforeLast('/') else ""
                    if (volId.isNotBlank() && parentRel.isNotEmpty()) {
                        val dir = File("/storage/$volId/$parentRel")
                        return OriginalLocation(
                            relativePath = "$parentRel/",
                            volumeName = volId.lowercase(Locale.ROOT),
                            directDir = dir
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Map media.documents URI to concrete MediaStore URI before querying columns
        var queryUri = parsed
        if (authority.contains("media.documents")) {
            try {
                val docId = if (DocumentsContract.isDocumentUri(context, parsed)) {
                    DocumentsContract.getDocumentId(parsed)
                } else {
                    parsed.lastPathSegment ?: ""
                }
                val parts = docId.split(":")
                if (parts.size >= 2) {
                    val id = parts[1].toLongOrNull()
                    if (id != null) {
                        queryUri = when (parts[0].lowercase(Locale.ROOT)) {
                            "video" -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            "audio" -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            "image" -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            else -> ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Query MediaStore RELATIVE_PATH / VOLUME_NAME / DATA
        return queryUriLocation(context, queryUri, primaryRoot)
    }

    private fun queryUriLocation(context: Context, uri: Uri, primaryRoot: String): OriginalLocation? {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.VOLUME_NAME,
                MediaStore.MediaColumns.DATA
            )
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    var relPath: String? = null
                    var volName: String? = null
                    var dataPath: String? = null

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        if (relIdx != -1) relPath = cursor.getString(relIdx)
                        val volIdx = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
                        if (volIdx != -1) volName = cursor.getString(volIdx)
                    }
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) dataPath = cursor.getString(dataIdx)

                    val directDir = dataPath?.takeIf { it.isNotBlank() }?.let { path ->
                        if (MediaUriResolver.isAppPrivatePath(context, path, allowApprovedCacheTemp = false)) null
                        else File(path).parentFile
                    }
                    if (relPath.isNullOrBlank() && directDir != null) {
                        relPath = deriveRelativePath(directDir.absolutePath, primaryRoot)
                    }
                    if (!relPath.isNullOrBlank() || directDir != null) {
                        return OriginalLocation(
                            relativePath = relPath,
                            volumeName = volName,
                            directDir = directDir
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun deriveRelativePath(dirAbsPath: String, primaryRoot: String): String? {
        val cleanDir = dirAbsPath.trimEnd('/')
        if (cleanDir.equals(primaryRoot, ignoreCase = true)) return null
        if (cleanDir.startsWith("$primaryRoot/", ignoreCase = true)) {
            val rel = cleanDir.substring(primaryRoot.length + 1).trim('/')
            return if (rel.isNotEmpty()) "$rel/" else null
        }
        return null
    }
}
