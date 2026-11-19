package shura.media.data

import android.content.Context
import android.net.Uri
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class SettingsManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vianbr_settings", Context.MODE_PRIVATE)
    private val historyDao by lazy { AppDatabase.getDatabase(appContext).playbackHistoryDao() }

    private val historyLock = Any()
    private val historyDbMutex = Mutex()
    private val historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val initialLoadDeferred = CompletableDeferred<Unit>()
    private val historyByUri = ConcurrentHashMap<String, PlaybackHistoryEntity>()
    private val historyByFileName = ConcurrentHashMap<String, PlaybackHistoryEntity>()
    private val dirtyUris = mutableSetOf<String>()
    private val deletedUris = mutableSetOf<String>()
    private val deletedFileNameKeys = mutableSetOf<String>()
    private var writesSincePrune = 0

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _bookmarkedFolders = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedFolders: StateFlow<Set<String>> = _bookmarkedFolders.asStateFlow()

    private val _extensions = MutableStateFlow<List<String>>(emptyList())
    val extensions: StateFlow<List<String>> = _extensions.asStateFlow()

    private val _outputFolderUri = MutableStateFlow<String?>(null)
    val outputFolderUri: StateFlow<String?> = _outputFolderUri.asStateFlow()

    private val _saveSingleEditsToOriginalFolder = MutableStateFlow(true)
    val saveSingleEditsToOriginalFolder: StateFlow<Boolean> = _saveSingleEditsToOriginalFolder.asStateFlow()

    private val _showLoggerFab = MutableStateFlow(true)
    val showLoggerFab: StateFlow<Boolean> = _showLoggerFab.asStateFlow()

    private val _keepScreenAwake = MutableStateFlow(true)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    private val _themePreference = MutableStateFlow("Light")
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private val _fontPreference = MutableStateFlow("Default")
    val fontPreference: StateFlow<String> = _fontPreference.asStateFlow()

    private val _liteModeEnabled = MutableStateFlow(true)
    val liteModeEnabled: StateFlow<Boolean> = _liteModeEnabled.asStateFlow()

    private val _ignoreNoMediaSubfolders = MutableStateFlow(true)
    val ignoreNoMediaSubfolders: StateFlow<Boolean> = _ignoreNoMediaSubfolders.asStateFlow()

    init {
        val excludedStrSet = prefs.getStringSet("excluded_folders", emptySet())
        if (!excludedStrSet.isNullOrEmpty()) {
            _excludedFolders.value = excludedStrSet
        }

        val bookmarkedStrSet = prefs.getStringSet("bookmarked_folders", emptySet())
        if (!bookmarkedStrSet.isNullOrEmpty()) {
            _bookmarkedFolders.value = bookmarkedStrSet
        }
        
        _outputFolderUri.value = prefs.getString("output_folder_uri", null)
        _saveSingleEditsToOriginalFolder.value = prefs.getBoolean("save_single_edits_to_original_folder", true)
        
        _showLoggerFab.value = prefs.getBoolean("show_logger_fab", true)
        _keepScreenAwake.value = prefs.getBoolean("keep_screen_awake", true)
        _liteModeEnabled.value = prefs.getBoolean("lite_mode_enabled", true)
        _ignoreNoMediaSubfolders.value = prefs.getBoolean("ignore_nomedia_subfolders", true)

        _themePreference.value = prefs.getString("theme_preference", "Light") ?: "Light"
        _fontPreference.value = prefs.getString("font_preference", "Default") ?: "Default"


        val defaultExts = (COMMON_VIDEO_EXTENSIONS + COMMON_AUDIO_EXTENSIONS)
        val savedExts = prefs.getStringSet("extensions", null)
        
        val imageExts = COMMON_IMAGE_EXTENSIONS
        val exts = if (savedExts != null) {
            (savedExts.filterNot { it in imageExts } + defaultExts).distinct()
        } else {
            defaultExts.toList()
        }
        
        _extensions.value = exts

        historyScope.launch {
            initializeHistoryFromPrefsAndRoom()
        }
    }

    fun addExcludedFolder(bucketId: String) {
        val currentSet = _excludedFolders.value.toMutableSet()
        if (currentSet.add(bucketId)) {
            _excludedFolders.value = currentSet
            prefs.edit().putStringSet("excluded_folders", currentSet).apply()
        }
    }

    fun removeExcludedFolder(bucketId: String) {
        val currentSet = _excludedFolders.value.toMutableSet()
        if (currentSet.remove(bucketId)) {
            _excludedFolders.value = currentSet
            prefs.edit().putStringSet("excluded_folders", currentSet).apply()
        }
    }

    fun addBookmarkedFolder(bucketId: String) {
        val currentSet = _bookmarkedFolders.value.toMutableSet()
        if (currentSet.add(bucketId)) {
            _bookmarkedFolders.value = currentSet
            prefs.edit().putStringSet("bookmarked_folders", currentSet).apply()
        }
    }

    fun removeBookmarkedFolder(bucketId: String) {
        val currentSet = _bookmarkedFolders.value.toMutableSet()
        if (currentSet.remove(bucketId)) {
            _bookmarkedFolders.value = currentSet
            prefs.edit().putStringSet("bookmarked_folders", currentSet).apply()
        }
    }

    fun toggleBookmarkedFolder(bucketId: String) {
        if (bucketId in _bookmarkedFolders.value) {
            removeBookmarkedFolder(bucketId)
        } else {
            addBookmarkedFolder(bucketId)
        }
    }

    fun setExtensions(exts: List<String>) {
        _extensions.value = exts
        prefs.edit().putStringSet("extensions", exts.toSet()).apply()
    }

    fun setIgnoreNoMediaSubfolders(ignore: Boolean) {
        _ignoreNoMediaSubfolders.value = ignore
        prefs.edit().putBoolean("ignore_nomedia_subfolders", ignore).apply()
    }

    fun setOutputFolderUri(uriStr: String?) {
        _outputFolderUri.value = uriStr
        if (uriStr == null) {
            prefs.edit().remove("output_folder_uri").apply()
        } else {
            prefs.edit().putString("output_folder_uri", uriStr).apply()
        }
    }

    fun setSaveSingleEditsToOriginalFolder(enabled: Boolean) {
        _saveSingleEditsToOriginalFolder.value = enabled
        prefs.edit().putBoolean("save_single_edits_to_original_folder", enabled).apply()
    }

    fun setShowLoggerFab(show: Boolean) {
        _showLoggerFab.value = show
        prefs.edit().putBoolean("show_logger_fab", show).apply()
    }

    fun setKeepScreenAwake(keep: Boolean) {
        _keepScreenAwake.value = keep
        prefs.edit().putBoolean("keep_screen_awake", keep).apply()
    }

    fun setThemePreference(theme: String) {
        _themePreference.value = theme
        prefs.edit().putString("theme_preference", theme).apply()
    }

    fun setFontPreference(font: String) {
        _fontPreference.value = font
        prefs.edit().putString("font_preference", font).apply()
    }

    fun setLiteModeEnabled(enabled: Boolean) {
        _liteModeEnabled.value = enabled
        prefs.edit().putBoolean("lite_mode_enabled", enabled).apply()
    }


    private fun normalizeUriKey(rawUri: String): String {
        val trimmed = rawUri.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("file://", ignoreCase = true)) {
            val decodedPath = try {
                Uri.parse(trimmed).path
            } catch (_: Exception) {
                null
            }
            if (!decodedPath.isNullOrBlank()) {
                return "file://$decodedPath"
            }
        }
        return trimmed
    }

    private fun normalizeFileNameStem(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val trimmed = rawName.trim()
        if (trimmed.equals("Unknown", ignoreCase = true)) return ""
        val decoded = try {
            Uri.decode(trimmed)
        } catch (_: Exception) {
            trimmed
        }.trim()
        if (decoded.isBlank() || decoded.equals("Unknown", ignoreCase = true)) return ""
        val dotIndex = decoded.lastIndexOf('.')
        val stem = if (dotIndex > 0) decoded.substring(0, dotIndex).trim() else decoded
        return stem.lowercase(java.util.Locale.ROOT)
    }

    private fun isSafeFileNameKey(stem: String): Boolean {
        if (stem.length < 4) return false
        if (stem.all { it.isDigit() } && stem.length < 6) return false
        return stem !in GENERIC_FILENAME_STEMS
    }

    private fun extractSafeFileNameKey(fileName: String?, uri: String?): String {
        val fromName = normalizeFileNameStem(fileName)
        if (isSafeFileNameKey(fromName)) return fromName
        if (!uri.isNullOrBlank()) {
            try {
                val parsedUri = Uri.parse(uri)
                val lastPath = parsedUri.lastPathSegment
                if (!lastPath.isNullOrBlank()) {
                    val seg = if (lastPath.contains('/')) lastPath.substringAfterLast('/') else lastPath
                    val segDoc = if (seg.contains(':')) seg.substringAfterLast(':') else seg
                    val fromUri = normalizeFileNameStem(segDoc)
                    if (isSafeFileNameKey(fromUri)) return fromUri
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    private fun indexFileNameLocked(entity: PlaybackHistoryEntity) {
        val key = entity.fileNameKey
        if (!isSafeFileNameKey(key)) return
        val existing = historyByFileName[key]
        if (existing == null || entity.lastPlayedTime >= existing.lastPlayedTime) {
            historyByFileName[key] = entity
        }
    }

    private fun findHistoryEntityLocked(
        normUri: String,
        fileName: String?,
        uriMap: Map<String, PlaybackHistoryEntity> = historyByUri,
        fnMap: Map<String, PlaybackHistoryEntity> = historyByFileName
    ): PlaybackHistoryEntity? {
        if (normUri.isNotEmpty()) {
            uriMap[normUri]?.let { return it }
        }
        val fnKey = extractSafeFileNameKey(fileName, normUri)
        if (fnKey.isNotEmpty()) {
            return fnMap[fnKey]
        }
        return null
    }

    suspend fun ensureHistoryLoaded() {
        initialLoadDeferred.await()
    }

    fun awaitHistoryLoadedIfOffMain(timeoutMs: Long = 1500L) {
        if (initialLoadDeferred.isCompleted || Looper.myLooper() == Looper.getMainLooper()) return
        try {
            runBlocking {
                withTimeoutOrNull(timeoutMs) {
                    initialLoadDeferred.await()
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun initializeHistoryFromPrefsAndRoom() {
        historyDbMutex.withLock {
            try {
                migrateLegacyPrefsLocked()
                val totalDbRows = historyDao.getAllHistory()
                if (totalDbRows.size > HISTORY_CAP) {
                    historyDao.pruneBeyondLimit(HISTORY_CAP)
                }
                val dbRows = if (totalDbRows.size > HISTORY_CAP) {
                    historyDao.getAllHistory()
                } else {
                    totalDbRows
                }
                synchronized(historyLock) {
                    for (row in dbRows) {
                        val normUri = normalizeUriKey(row.mediaUri)
                        if (normUri.isEmpty()) continue
                        if (normUri in deletedUris || row.mediaUri in deletedUris) continue
                        if (row.fileNameKey.isNotEmpty() && row.fileNameKey in deletedFileNameKeys) continue
                        if (normUri in dirtyUris || row.mediaUri in dirtyUris) continue

                        val normalizedRow = if (normUri == row.mediaUri) row else row.copy(mediaUri = normUri)
                        historyByUri.putIfAbsent(normUri, normalizedRow)
                        indexFileNameLocked(normalizedRow)
                    }
                }
            } catch (e: Exception) {
                shura.media.LogKeeper.logError("SettingsManager", "Failed initializing Room playback history", e)
            } finally {
                initialLoadDeferred.complete(Unit)
            }
        }
    }

    private suspend fun migrateLegacyPrefsLocked() {
        if (prefs.getBoolean(KEY_HISTORY_ROOM_MIGRATED, false)) return

        val allEntries = prefs.all
        val legacyKeys = mutableListOf<String>()
        val rawUriKeys = linkedSetOf<String>()
        val fnTimeToSafeKeys = HashMap<Long, MutableList<String>>()

        for ((key, value) in allEntries) {
            when {
                key.startsWith("time_fn_") || key.startsWith("pos_fn_") || key.startsWith("dur_fn_") -> {
                    legacyKeys.add(key)
                    if (key.startsWith("time_fn_")) {
                        val ts = (value as? Number)?.toLong() ?: 0L
                        val safeStem = normalizeFileNameStem(key.removePrefix("time_fn_"))
                        if (ts > 0L && isSafeFileNameKey(safeStem)) {
                            fnTimeToSafeKeys.getOrPut(ts) { mutableListOf() }.add(safeStem)
                        }
                    }
                }
                key.startsWith("time_") -> {
                    legacyKeys.add(key)
                    rawUriKeys.add(key.removePrefix("time_"))
                }
                key.startsWith("pos_") -> {
                    legacyKeys.add(key)
                    rawUriKeys.add(key.removePrefix("pos_"))
                }
                key.startsWith("dur_") -> {
                    legacyKeys.add(key)
                    rawUriKeys.add(key.removePrefix("dur_"))
                }
                key.startsWith("speed_") -> {
                    legacyKeys.add(key)
                    rawUriKeys.add(key.removePrefix("speed_"))
                }
                key.startsWith("orient_") -> {
                    legacyKeys.add(key)
                    rawUriKeys.add(key.removePrefix("orient_"))
                }
                key.startsWith("track_") -> {
                    legacyKeys.add(key)
                    val rest = key.removePrefix("track_")
                    val sep = rest.indexOf('_')
                    if (sep > 0 && rest.substring(0, sep).toIntOrNull() != null) {
                        rawUriKeys.add(rest.substring(sep + 1))
                    }
                }
            }
        }

        if (rawUriKeys.isNotEmpty()) {
            val mergedByNormUri = LinkedHashMap<String, PlaybackHistoryEntity>()
            for (rawUri in rawUriKeys) {
                val normUri = normalizeUriKey(rawUri)
                if (normUri.isEmpty()) continue
                val ts = (allEntries["time_$rawUri"] as? Number)?.toLong() ?: 0L
                val pos = (allEntries["pos_$rawUri"] as? Number)?.toLong() ?: 0L
                val dur = (allEntries["dur_$rawUri"] as? Number)?.toLong() ?: -1L
                val speed = (allEntries["speed_$rawUri"] as? Number)?.toFloat() ?: 1.0f
                val orient = allEntries["orient_$rawUri"] as? Boolean
                val audioIdx = (allEntries["track_${TRACK_TYPE_AUDIO}_$rawUri"] as? Number)?.toInt() ?: -1
                val subIdx = (allEntries["track_${TRACK_TYPE_TEXT}_$rawUri"] as? Number)?.toInt() ?: -1

                val matchedFnKey = extractSafeFileNameKey(null, normUri).ifEmpty {
                    fnTimeToSafeKeys[ts]?.maxByOrNull { it.length } ?: ""
                }

                val existing = mergedByNormUri[normUri]
                mergedByNormUri[normUri] = if (existing == null) {
                    PlaybackHistoryEntity(
                        mediaUri = normUri,
                        fileNameKey = matchedFnKey,
                        positionMs = pos,
                        durationMs = dur,
                        lastPlayedTime = ts,
                        playbackSpeed = speed,
                        isPortrait = orient,
                        audioTrackIndex = audioIdx,
                        subtitleTrackIndex = subIdx
                    )
                } else {
                    existing.copy(
                        fileNameKey = existing.fileNameKey.ifEmpty { matchedFnKey },
                        positionMs = if (ts >= existing.lastPlayedTime && pos > 0L) pos else existing.positionMs,
                        durationMs = if (dur > 0L) dur else existing.durationMs,
                        lastPlayedTime = maxOf(existing.lastPlayedTime, ts),
                        playbackSpeed = if (speed != 1.0f) speed else existing.playbackSpeed,
                        isPortrait = orient ?: existing.isPortrait,
                        audioTrackIndex = if (audioIdx != -1) audioIdx else existing.audioTrackIndex,
                        subtitleTrackIndex = if (subIdx != -1) subIdx else existing.subtitleTrackIndex
                    )
                }
            }

            val topEntities = mergedByNormUri.values
                .sortedByDescending { it.lastPlayedTime }
                .take(HISTORY_CAP)
            if (topEntities.isNotEmpty()) {
                historyDao.upsertAll(topEntities)
                historyDao.pruneBeyondLimit(HISTORY_CAP)
            }
        }

        val editor = prefs.edit()
        for (k in legacyKeys) {
            editor.remove(k)
        }
        editor.putBoolean(KEY_HISTORY_ROOM_MIGRATED, true)
        editor.commit()
    }

    private fun trimInMemoryHistoryLocked() {
        writesSincePrune++
        val shouldPrune = writesSincePrune >= PRUNE_INTERVAL || historyByUri.size > HISTORY_CAP + 20
        if (!shouldPrune) return
        writesSincePrune = 0

        if (historyByUri.size > HISTORY_CAP) {
            val overflow = historyByUri.values
                .sortedBy { it.lastPlayedTime }
                .take(historyByUri.size - HISTORY_CAP)
            for (old in overflow) {
                historyByUri.remove(old.mediaUri)
                if (old.fileNameKey.isNotEmpty() && historyByFileName[old.fileNameKey]?.mediaUri == old.mediaUri) {
                    historyByFileName.remove(old.fileNameKey)
                }
            }
        }

        historyScope.launch {
            historyDbMutex.withLock {
                try {
                    historyDao.pruneBeyondLimit(HISTORY_CAP)
                } catch (_: Exception) {}
            }
        }
    }

    private fun persistUpsert(entity: PlaybackHistoryEntity) {
        historyScope.launch {
            historyDbMutex.withLock {
                try {
                    historyDao.upsert(entity)
                } catch (e: Exception) {
                    shura.media.LogKeeper.logError("SettingsManager", "Failed persisting playback history", e)
                }
            }
        }
    }

    fun savePlaybackState(uri: String, position: Long, duration: Long, fileName: String? = null) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val now = System.currentTimeMillis()
        prefs.edit().putString("last_played_uri", normUri).apply()

        val updated = synchronized(historyLock) {
            deletedUris.remove(normUri)
            dirtyUris.add(normUri)
            val existing = historyByUri[normUri]
            val resolvedFnKey = extractSafeFileNameKey(fileName, normUri).ifEmpty { existing?.fileNameKey ?: "" }
            val next = PlaybackHistoryEntity(
                mediaUri = normUri,
                fileNameKey = resolvedFnKey,
                positionMs = if (position > 0L) position else (existing?.positionMs ?: 0L),
                durationMs = if (duration > 0L) duration else (existing?.durationMs ?: -1L),
                lastPlayedTime = now,
                playbackSpeed = existing?.playbackSpeed ?: 1.0f,
                isPortrait = existing?.isPortrait,
                audioTrackIndex = existing?.audioTrackIndex ?: -1,
                subtitleTrackIndex = existing?.subtitleTrackIndex ?: -1
            )
            historyByUri[normUri] = next
            indexFileNameLocked(next)
            trimInMemoryHistoryLocked()
            next
        }
        persistUpsert(updated)
    }

    fun markAsFinished(uri: String, fileName: String? = null, duration: Long = 0L) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val targetDur = if (duration > 0L) duration else getStoredDuration(normUri, fileName).takeIf { it > 0L } ?: 1000L
        savePlaybackState(normUri, targetDur, targetDur, fileName)
        val lastUri = getLastPlayedUri()
        if (lastUri != null && normalizeUriKey(lastUri) == normUri) {
            prefs.edit().remove("last_played_uri").apply()
        }
    }

    fun getLastPlayedUri(): String? {
        return prefs.getString("last_played_uri", null)
    }

    fun markAsOpened(uri: String, fileName: String? = null) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val now = System.currentTimeMillis()
        prefs.edit().putString("last_played_uri", normUri).apply()

        val updated = synchronized(historyLock) {
            deletedUris.remove(normUri)
            dirtyUris.add(normUri)
            val existing = historyByUri[normUri]
            val resolvedFnKey = extractSafeFileNameKey(fileName, normUri).ifEmpty { existing?.fileNameKey ?: "" }
            val next = (existing ?: PlaybackHistoryEntity(mediaUri = normUri)).copy(
                fileNameKey = resolvedFnKey,
                lastPlayedTime = now
            )
            historyByUri[normUri] = next
            indexFileNameLocked(next)
            trimInMemoryHistoryLocked()
            next
        }
        persistUpsert(updated)
    }

    fun savePlaybackSpeed(uri: String, speed: Float) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val updated = synchronized(historyLock) {
            deletedUris.remove(normUri)
            dirtyUris.add(normUri)
            val existing = historyByUri[normUri]
            val resolvedFnKey = existing?.fileNameKey?.ifEmpty { extractSafeFileNameKey(null, normUri) }
                ?: extractSafeFileNameKey(null, normUri)
            val next = (existing ?: PlaybackHistoryEntity(mediaUri = normUri)).copy(
                fileNameKey = resolvedFnKey,
                playbackSpeed = speed
            )
            historyByUri[normUri] = next
            indexFileNameLocked(next)
            next
        }
        persistUpsert(updated)
    }

    fun getPlaybackSpeed(uri: String): Float {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return 1.0f
        return synchronized(historyLock) {
            historyByUri[normUri]?.playbackSpeed ?: 1.0f
        }
    }

    fun saveTrackSelection(uri: String, trackType: Int, trackIndex: Int) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        if (trackType != TRACK_TYPE_AUDIO && trackType != TRACK_TYPE_TEXT) return
        val updated = synchronized(historyLock) {
            deletedUris.remove(normUri)
            dirtyUris.add(normUri)
            val existing = historyByUri[normUri]
            val resolvedFnKey = existing?.fileNameKey?.ifEmpty { extractSafeFileNameKey(null, normUri) }
                ?: extractSafeFileNameKey(null, normUri)
            val base = existing ?: PlaybackHistoryEntity(mediaUri = normUri, fileNameKey = resolvedFnKey)
            val next = when (trackType) {
                TRACK_TYPE_AUDIO -> base.copy(fileNameKey = resolvedFnKey, audioTrackIndex = trackIndex)
                TRACK_TYPE_TEXT -> base.copy(fileNameKey = resolvedFnKey, subtitleTrackIndex = trackIndex)
                else -> base
            }
            historyByUri[normUri] = next
            indexFileNameLocked(next)
            next
        }
        persistUpsert(updated)
    }

    fun getTrackSelection(uri: String, trackType: Int): Int {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return -1
        return synchronized(historyLock) {
            val entity = historyByUri[normUri] ?: return@synchronized -1
            when (trackType) {
                TRACK_TYPE_AUDIO -> entity.audioTrackIndex
                TRACK_TYPE_TEXT -> entity.subtitleTrackIndex
                else -> -1
            }
        }
    }

    fun getLastPlayedTime(uri: String, fileName: String? = null): Long {
        val normUri = normalizeUriKey(uri)
        return synchronized(historyLock) {
            val direct = if (normUri.isNotEmpty()) historyByUri[normUri] else null
            if (direct != null && direct.lastPlayedTime > 0L) return@synchronized direct.lastPlayedTime
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                return@synchronized historyByFileName[fnKey]?.lastPlayedTime ?: 0L
            }
            0L
        }
    }

    fun getPlaybackPosition(uri: String, fileName: String? = null): Long {
        val normUri = normalizeUriKey(uri)
        return synchronized(historyLock) {
            val direct = if (normUri.isNotEmpty()) historyByUri[normUri] else null
            if (direct != null && direct.positionMs > 0L) return@synchronized direct.positionMs
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                return@synchronized historyByFileName[fnKey]?.positionMs ?: 0L
            }
            0L
        }
    }

    fun getStoredDuration(uri: String, fileName: String? = null): Long {
        val normUri = normalizeUriKey(uri)
        return synchronized(historyLock) {
            val direct = if (normUri.isNotEmpty()) historyByUri[normUri] else null
            if (direct != null && direct.durationMs > 0L) return@synchronized direct.durationMs
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                val fnDur = historyByFileName[fnKey]?.durationMs ?: -1L
                if (fnDur > 0L) return@synchronized fnDur
            }
            -1L
        }
    }

    fun saveVideoOrientation(uri: String, isPortrait: Boolean) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val updated = synchronized(historyLock) {
            deletedUris.remove(normUri)
            dirtyUris.add(normUri)
            val existing = historyByUri[normUri]
            val resolvedFnKey = existing?.fileNameKey?.ifEmpty { extractSafeFileNameKey(null, normUri) }
                ?: extractSafeFileNameKey(null, normUri)
            val next = (existing ?: PlaybackHistoryEntity(mediaUri = normUri)).copy(
                fileNameKey = resolvedFnKey,
                isPortrait = isPortrait
            )
            historyByUri[normUri] = next
            indexFileNameLocked(next)
            next
        }
        persistUpsert(updated)
    }

    fun getVideoOrientation(uri: String): Boolean? {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return null
        return synchronized(historyLock) {
            historyByUri[normUri]?.isPortrait
        }
    }

    fun removePlaybackState(uri: String, fileName: String? = null) {
        val normUri = normalizeUriKey(uri)
        if (normUri.isEmpty()) return
        val fnKey = synchronized(historyLock) {
            val removed = historyByUri.remove(normUri)
            dirtyUris.remove(normUri)
            deletedUris.add(normUri)
            val candidateFnKey = removed?.fileNameKey?.ifEmpty { extractSafeFileNameKey(fileName, normUri) }
                ?: extractSafeFileNameKey(fileName, normUri)
            if (candidateFnKey.isNotEmpty()) {
                deletedFileNameKeys.add(candidateFnKey)
                historyByFileName.remove(candidateFnKey)
            }
            candidateFnKey
        }
        val lastUri = getLastPlayedUri()
        if (lastUri != null && normalizeUriKey(lastUri) == normUri) {
            prefs.edit().remove("last_played_uri").apply()
        }
        historyScope.launch {
            historyDbMutex.withLock {
                try {
                    historyDao.deleteByUri(normUri)
                    if (fnKey.isNotEmpty()) {
                        historyDao.deleteByFileNameKey(fnKey)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // A video is finished if we watched past 99%
    fun isFinished(uri: String, fileName: String? = null): Boolean {
        val pos = getPlaybackPosition(uri, fileName)
        val dur = getStoredDuration(uri, fileName)
        return if (dur > 0L) pos >= dur * 0.99 else false
    }

    var hasSeenWelcome: Boolean
        get() = prefs.getBoolean("has_seen_welcome", false)
        set(value) = prefs.edit().putBoolean("has_seen_welcome", value).apply()

    var ignoreNoMediaSubfoldersEnabled: Boolean
        get() = prefs.getBoolean("ignore_nomedia_subfolders", true)
        set(value) = setIgnoreNoMediaSubfolders(value)

    var audioBoosterEnabled: Boolean
        get() = prefs.getBoolean("audio_booster_enabled", true)
        set(value) = prefs.edit().putBoolean("audio_booster_enabled", value).apply()
        
    var boostGainMb: Int
        get() = prefs.getInt("boost_gain_mb", 0)
        set(value) = prefs.edit().putInt("boost_gain_mb", value).apply()

    var defaultAudioBackgroundPlay: Boolean
        get() = prefs.getBoolean("default_audio_background_play", true)
        set(value) = prefs.edit().putBoolean("default_audio_background_play", value).apply()

    var decoderPriority: Int
        get() = prefs.getInt("decoder_priority", 1) // 0: Device Only, 1: Prefer Device, 2: Prefer App
        set(value) = prefs.edit().putInt("decoder_priority", value).apply()

    
    var centerChannelEnabled: Boolean
        get() = prefs.getBoolean("center_channel_enabled", false)
        set(value) {
            prefs.edit().putBoolean("center_channel_enabled", value).apply()
            shura.media.service.PlayerManager.applyAudioEffects(this)
        }

    var eqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
            shura.media.service.PlayerManager.applyAudioEffects(this)
        }

    var nightModeEnabled: Boolean
        get() = prefs.getBoolean("night_mode_enabled", false)
        set(value) {
            prefs.edit().putBoolean("night_mode_enabled", value).apply()
            shura.media.service.PlayerManager.applyAudioEffects(this)
        }
        
    fun getEqLevels(): List<Int> {
        val str = prefs.getString("eq_levels", "") ?: ""
        if (str.isEmpty()) return emptyList()
        return try {
            str.split(",").map { it.toInt() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun setEqLevels(levels: List<Int>) {
        prefs.edit().putString("eq_levels", levels.joinToString(",")).apply()
        shura.media.service.PlayerManager.applyAudioEffects(this)
    }

    fun getNotificationPriority(): List<String> {
        val defaultPriority = "Loop,Playlist,Floating Player,Close"
        val saved = prefs.getString("notification_priority", defaultPriority) ?: defaultPriority
        return saved.replace("PiP", "Floating Player").split(",")
    }
    
    fun setNotificationPriority(priority: List<String>) {
        prefs.edit().putString("notification_priority", priority.joinToString(",")).apply()
    }

    inner class PlaybackStateLookup(
        private val uriMap: Map<String, PlaybackHistoryEntity>,
        private val fnMap: Map<String, PlaybackHistoryEntity>
    ) {
        fun getLastPlayedTime(uri: String, fileName: String? = null): Long {
            val normUri = normalizeUriKey(uri)
            val direct = if (normUri.isNotEmpty()) uriMap[normUri] else null
            if (direct != null && direct.lastPlayedTime > 0L) return direct.lastPlayedTime
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                return fnMap[fnKey]?.lastPlayedTime ?: 0L
            }
            return 0L
        }

        fun getPlaybackPosition(uri: String, fileName: String? = null): Long {
            val normUri = normalizeUriKey(uri)
            val direct = if (normUri.isNotEmpty()) uriMap[normUri] else null
            if (direct != null && direct.positionMs > 0L) return direct.positionMs
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                return fnMap[fnKey]?.positionMs ?: 0L
            }
            return 0L
        }

        fun getStoredDuration(uri: String, fileName: String? = null): Long {
            val normUri = normalizeUriKey(uri)
            val direct = if (normUri.isNotEmpty()) uriMap[normUri] else null
            if (direct != null && direct.durationMs > 0L) return direct.durationMs
            val fnKey = extractSafeFileNameKey(fileName, normUri)
            if (fnKey.isNotEmpty()) {
                val fnDur = fnMap[fnKey]?.durationMs ?: -1L
                if (fnDur > 0L) return fnDur
            }
            return -1L
        }

        fun isFinished(uri: String, fileName: String? = null): Boolean {
            val pos = getPlaybackPosition(uri, fileName)
            val dur = getStoredDuration(uri, fileName)
            return if (dur > 0L) pos >= dur * 0.99 else false
        }
    }

    fun createPlaybackLookup(): PlaybackStateLookup {
        return synchronized(historyLock) {
            PlaybackStateLookup(
                uriMap = HashMap(historyByUri),
                fnMap = HashMap(historyByFileName)
            )
        }
    }

    companion object {
        private const val HISTORY_CAP = 250
        private const val PRUNE_INTERVAL = 20
        private const val KEY_HISTORY_ROOM_MIGRATED = "playback_history_room_migrated_v1"
        private const val TRACK_TYPE_AUDIO = 1
        private const val TRACK_TYPE_TEXT = 3
        private val GENERIC_FILENAME_STEMS = setOf(
            "unknown", "video", "audio", "media", "movie", "clip", "file",
            "temp", "tmp", "download", "downloads", "recording", "screenrecording",
            "audio_record", "untitled", "index", "stream", "playlist", "master", "playback"
        )

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
