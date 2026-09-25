package com.example.data

import android.content.Context
import com.example.LogKeeper
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    suspend fun createBackup(context: Context): String {
        LogKeeper.log("Creating full app backup...", "BackupManager")
        val db = AppDatabase.getDatabase(context)
        val dao = db.playlistDao()
        val settings = SettingsManager.getInstance(context)

        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Serialize Settings / Preferences
        val settingsJson = JSONObject()
        settingsJson.put("output_folder_uri", settings.outputFolderUri.value)
        settingsJson.put("show_logger_fab", settings.showLoggerFab.value)
        settingsJson.put("has_seen_welcome", settings.hasSeenWelcome)

        val excludedArray = JSONArray()
        settings.excludedFolders.value.forEach { excludedArray.put(it) }
        settingsJson.put("excluded_folders", excludedArray)

        val extensionsArray = JSONArray()
        settings.extensions.value.forEach { extensionsArray.put(it) }
        settingsJson.put("extensions", extensionsArray)

        root.put("settings", settingsJson)

        // 2. Serialize Playlists & Items
        val playlistsArray = JSONArray()
        val playlists = dao.getAllPlaylistsSync()
        val items = dao.getAllPlaylistItemsSync()

        playlists.forEach { playlist ->
            val playlistJson = JSONObject()
            playlistJson.put("name", playlist.name)
            playlistJson.put("timestamp", playlist.timestamp)

            val playlistItemsArray = JSONArray()
            val playlistItems = items.filter { it.playlistId == playlist.id }
            playlistItems.forEach { item ->
                val itemJson = JSONObject()
                itemJson.put("media_uri", item.mediaUri)
                itemJson.put("timestamp", item.timestamp)
                playlistItemsArray.put(itemJson)
            }
            playlistJson.put("items", playlistItemsArray)
            playlistsArray.put(playlistJson)
        }

        root.put("playlists", playlistsArray)

        val backupStr = root.toString(2) // Pretty print with indent of 2
        LogKeeper.log("Backup created successfully. Size: ${backupStr.length} chars.", "BackupManager")
        return backupStr
    }

    suspend fun restoreBackup(context: Context, backupJsonStr: String): Boolean {
        LogKeeper.log("Starting full app restore...", "BackupManager")
        try {
            val root = JSONObject(backupJsonStr)
            val version = root.optInt("version", 1)
            if (version != 1) {
                LogKeeper.logError("BackupManager", "Unsupported backup version: $version")
                return false
            }

            val db = AppDatabase.getDatabase(context)
            val dao = db.playlistDao()
            val settings = SettingsManager.getInstance(context)

            // 1. Restore Settings
            if (root.has("settings")) {
                val settingsJson = root.getJSONObject("settings")
                
                val outputFolderUri = if (settingsJson.isNull("output_folder_uri")) null else settingsJson.getString("output_folder_uri")
                settings.setOutputFolderUri(outputFolderUri)

                val showLoggerFab = settingsJson.optBoolean("show_logger_fab", true)
                settings.setShowLoggerFab(showLoggerFab)

                val hasSeenWelcome = settingsJson.optBoolean("has_seen_welcome", true)
                settings.hasSeenWelcome = hasSeenWelcome

                if (settingsJson.has("excluded_folders")) {
                    val excludedArray = settingsJson.getJSONArray("excluded_folders")
                    // Clear existing and add new
                    settings.excludedFolders.value.forEach { settings.removeExcludedFolder(it) }
                    for (i in 0 until excludedArray.length()) {
                        settings.addExcludedFolder(excludedArray.getString(i))
                    }
                }

                if (settingsJson.has("extensions")) {
                    val extensionsArray = settingsJson.getJSONArray("extensions")
                    val exts = mutableListOf<String>()
                    for (i in 0 until extensionsArray.length()) {
                        exts.add(extensionsArray.getString(i))
                    }
                    if (exts.isNotEmpty()) {
                        settings.setExtensions(exts)
                    }
                }
            }

            // 2. Restore Database (Playlists and Playlist Items)
            if (root.has("playlists")) {
                // Clear existing database
                dao.clearAllPlaylists()

                val playlistsArray = root.getJSONArray("playlists")
                for (i in 0 until playlistsArray.length()) {
                    val playlistJson = playlistsArray.getJSONObject(i)
                    val name = playlistJson.getString("name")
                    val timestamp = playlistJson.optLong("timestamp", System.currentTimeMillis())

                    // Insert Playlist
                    val playlistId = dao.insertPlaylist(Playlist(name = name, timestamp = timestamp))

                    // Insert Items
                    if (playlistJson.has("items")) {
                        val itemsArray = playlistJson.getJSONArray("items")
                        for (j in 0 until itemsArray.length()) {
                            val itemJson = itemsArray.getJSONObject(j)
                            val mediaUri = itemJson.getString("media_uri")
                            val itemTimestamp = itemJson.optLong("timestamp", System.currentTimeMillis())

                            var isNotFound = false
                            try {
                                val uri = android.net.Uri.parse(mediaUri)
                                if (uri.scheme == "file") {
                                    isNotFound = !java.io.File(uri.path ?: "").exists()
                                } else if (uri.scheme == "content") {
                                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                        if (!cursor.moveToFirst()) isNotFound = true
                                    } ?: run { isNotFound = true }
                                }
                            } catch (e: Exception) {
                                isNotFound = true
                            }

                            dao.insertPlaylistItem(
                                PlaylistItem(
                                    playlistId = playlistId.toInt(),
                                    mediaUri = mediaUri,
                                    timestamp = itemTimestamp,
                                    isNotFound = isNotFound
                                )
                            )
                        }
                    }
                }
            }

            LogKeeper.log("Full app restore completed successfully.", "BackupManager")
            return true
        } catch (e: Exception) {
            LogKeeper.logError("BackupManager", "Error restoring backup: ${e.message}", e)
            return false
        }
    }
}
