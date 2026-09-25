package com.example.data

import android.content.Context
import com.example.LogKeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object CacheManager {

    /**
     * Calculates the total size in bytes of all non-Library temporary and cached files
     * (internal cacheDir and externalCacheDir). Does NOT count Room database or User settings.
     */
    fun getUnusedCacheSizeBytes(context: Context): Long {
        return calculateDirSize(context.cacheDir) + calculateDirSize(context.externalCacheDir)
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            bytes += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return bytes
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Purges all temporary files from video editor, photo editor, compressor, ffmpeg converter,
     * trimmer, and image thumbnail caches.
     * Guaranteed safe: Never touches user media, external storage, Room DB, or preferences.
     */
    suspend fun clearUnusedCache(context: Context): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val dirsToClean = listOfNotNull(context.cacheDir, context.externalCacheDir)
        for (dir in dirsToClean) {
            val files = dir.listFiles() ?: continue
            for (file in files) {
                freedBytes += deleteRecursiveWithCount(file)
            }
        }
        // Clear Coil in-memory and disk caches if active
        try {
            coil.Coil.imageLoader(context).memoryCache?.clear()
            coil.Coil.imageLoader(context).diskCache?.clear()
        } catch (e: Exception) {
            LogKeeper.logError("CacheManager", "Error clearing image loader cache", e)
        }
        LogKeeper.log("CacheManager: Cleared unused cache, freed ${formatBytes(freedBytes)}", "CacheManager")
        freedBytes
    }

    private fun deleteRecursiveWithCount(file: File): Long {
        var count = 0L
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    count += deleteRecursiveWithCount(child)
                }
            }
            try { file.delete() } catch (e: Exception) {}
        } else {
            count = file.length()
            try { file.delete() } catch (e: Exception) {}
        }
        return count
    }

    /**
     * Startup cleaner to automatically delete any leftover files from crashed/killed
     * editing, converting, or compressing operations.
     */
    fun purgeOrphanedTempFiles(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cache = context.cacheDir
                cache?.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("editor_") ||
                        name.startsWith("join_") ||
                        name.startsWith("edited_") ||
                        name.startsWith("ffmpeg_") ||
                        name.startsWith("preconverted_") ||
                        name.startsWith("Temp_Trim_") ||
                        name == "thumbnail_cache"
                    ) {
                        deleteRecursiveWithCount(file)
                    }
                }
            } catch (e: Exception) {
                LogKeeper.logError("CacheManager", "Error purging orphaned temp files on startup", e)
            }
        }
    }
}
