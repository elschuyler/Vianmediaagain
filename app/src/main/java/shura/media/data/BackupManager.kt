package shura.media.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shura.media.LogKeeper
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private val ALLOWED_MEDIA_SCHEMES = setOf("content", "file", "http", "https", "rtsp", "rtmp", "rtmpe", "rtmps", "rtp")
    private val ALLOWED_FOLDER_SCHEMES = setOf("content", "file")

    private fun isAllowedUriScheme(uriStr: String?, allowedSchemes: Set<String>): Boolean {
        if (uriStr.isNullOrBlank()) return false
        val scheme = try {
            android.net.Uri.parse(uriStr).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        return scheme != null && scheme in allowedSchemes
    }

    private data class ParsedPlaylistItem(
        val mediaUri: String,
        val timestamp: Long,
        val isNotFound: Boolean
    )

    private data class ParsedPlaylist(
        val name: String,
        val timestamp: Long,
        val items: List<ParsedPlaylistItem>
    )

    suspend fun createBackup(context: Context): String = withContext(Dispatchers.IO) {
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
        backupStr
    }

    suspend fun restoreBackup(context: Context, backupJsonStr: String): Boolean = withContext(Dispatchers.IO) {
        LogKeeper.log("Starting full app restore...", "BackupManager")
        try {
            val root = JSONObject(backupJsonStr)
            val version = root.optInt("version", 1)
            if (version != 1) {
                LogKeeper.logError("BackupManager", "Unsupported backup version: $version")
                return@withContext false
            }

            // 1. Pre-parse and validate settings payload before mutating anything
            val hasSettings = root.has("settings")
            var outputFolderUri: String? = null
            var showLoggerFab = true
            var hasSeenWelcome = true
            var excludedFoldersParsed: List<String>? = null
            var extensionsParsed: List<String>? = null

            if (hasSettings) {
                val settingsJson = root.getJSONObject("settings")
                val rawOutputFolderUri = if (settingsJson.isNull("output_folder_uri")) null else settingsJson.getString("output_folder_uri")
                outputFolderUri = if (isAllowedUriScheme(rawOutputFolderUri, ALLOWED_FOLDER_SCHEMES)) rawOutputFolderUri else null
                showLoggerFab = settingsJson.optBoolean("show_logger_fab", true)
                hasSeenWelcome = settingsJson.optBoolean("has_seen_welcome", true)

                if (settingsJson.has("excluded_folders")) {
                    val excludedArray = settingsJson.getJSONArray("excluded_folders")
                    val list = mutableListOf<String>()
                    for (i in 0 until excludedArray.length()) {
                        list.add(excludedArray.getString(i))
                    }
                    excludedFoldersParsed = list
                }

                if (settingsJson.has("extensions")) {
                    val extensionsArray = settingsJson.getJSONArray("extensions")
                    val list = mutableListOf<String>()
                    for (i in 0 until extensionsArray.length()) {
                        list.add(extensionsArray.getString(i))
                    }
                    extensionsParsed = list
                }
            }

            // 2. Pre-parse and validate entire playlists graph before clearing Room DB
            val hasPlaylists = root.has("playlists")
            val parsedPlaylists = mutableListOf<ParsedPlaylist>()
            if (hasPlaylists) {
                val playlistsArray = root.getJSONArray("playlists")
                for (i in 0 until playlistsArray.length()) {
                    val playlistJson = playlistsArray.getJSONObject(i)
                    val name = playlistJson.getString("name")
                    val timestamp = playlistJson.optLong("timestamp", System.currentTimeMillis())
                    val parsedItems = mutableListOf<ParsedPlaylistItem>()

                    if (playlistJson.has("items")) {
                        val itemsArray = playlistJson.getJSONArray("items")
                        for (j in 0 until itemsArray.length()) {
                            val itemJson = itemsArray.getJSONObject(j)
                            val mediaUri = itemJson.getString("media_uri")
                            if (!isAllowedUriScheme(mediaUri, ALLOWED_MEDIA_SCHEMES)) {
                                continue
                            }
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

                            parsedItems.add(
                                ParsedPlaylistItem(
                                    mediaUri = mediaUri,
                                    timestamp = itemTimestamp,
                                    isNotFound = isNotFound
                                )
                            )
                        }
                    }

                    parsedPlaylists.add(
                        ParsedPlaylist(
                            name = name,
                            timestamp = timestamp,
                            items = parsedItems
                        )
                    )
                }
            }

            val db = AppDatabase.getDatabase(context)
            val dao = db.playlistDao()
            val settings = SettingsManager.getInstance(context)

            // 3. Apply database changes atomically inside Room transaction
            if (hasPlaylists) {
                db.withTransaction {
                    dao.clearAllPlaylists()
                    for (playlist in parsedPlaylists) {
                        val playlistId = dao.insertPlaylist(
                            Playlist(name = playlist.name, timestamp = playlist.timestamp)
                        )
                        for (item in playlist.items) {
                            dao.insertPlaylistItem(
                                PlaylistItem(
                                    playlistId = playlistId.toInt(),
                                    mediaUri = item.mediaUri,
                                    timestamp = item.timestamp,
                                    isNotFound = item.isNotFound
                                )
                            )
                        }
                    }
                }
            }

            // 4. Apply settings only after DB restore succeeds
            if (hasSettings) {
                settings.setOutputFolderUri(outputFolderUri)
                settings.setShowLoggerFab(showLoggerFab)
                settings.hasSeenWelcome = hasSeenWelcome

                excludedFoldersParsed?.let { parsedExcluded ->
                    settings.excludedFolders.value.forEach { settings.removeExcludedFolder(it) }
                    parsedExcluded.forEach { settings.addExcludedFolder(it) }
                }

                extensionsParsed?.let { parsedExts ->
                    if (parsedExts.isNotEmpty()) {
                        settings.setExtensions(parsedExts)
                    }
                }
            }

            LogKeeper.log("Full app restore completed successfully.", "BackupManager")
            true
        } catch (e: Exception) {
            LogKeeper.logError("BackupManager", "Error restoring backup: ${e.message}", e)
            false
        }
    }
}
