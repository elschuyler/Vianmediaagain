package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("vianbr_settings", Context.MODE_PRIVATE)

    private val _excludedFolders = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _extensions = MutableStateFlow<List<String>>(emptyList())
    val extensions: StateFlow<List<String>> = _extensions.asStateFlow()

    private val _outputFolderUri = MutableStateFlow<String?>(null)
    val outputFolderUri: StateFlow<String?> = _outputFolderUri.asStateFlow()

    private val _showLoggerFab = MutableStateFlow(true)
    val showLoggerFab: StateFlow<Boolean> = _showLoggerFab.asStateFlow()

    private val _keepScreenAwake = MutableStateFlow(true)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    private val _themePreference = MutableStateFlow("Light")
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private val _fontPreference = MutableStateFlow("Default")
    val fontPreference: StateFlow<String> = _fontPreference.asStateFlow()

    private val _ignoreNoMediaSubfolders = MutableStateFlow(true)
    val ignoreNoMediaSubfolders: StateFlow<Boolean> = _ignoreNoMediaSubfolders.asStateFlow()

    init {
        val excludedStrSet = prefs.getStringSet("excluded_folders", emptySet())
        if (!excludedStrSet.isNullOrEmpty()) {
            _excludedFolders.value = excludedStrSet
        }
        
        _outputFolderUri.value = prefs.getString("output_folder_uri", null)
        
        _showLoggerFab.value = prefs.getBoolean("show_logger_fab", true)
        _keepScreenAwake.value = prefs.getBoolean("keep_screen_awake", true)
        _ignoreNoMediaSubfolders.value = prefs.getBoolean("ignore_nomedia_subfolders", true)

        _themePreference.value = prefs.getString("theme_preference", "Light") ?: "Light"
        _fontPreference.value = prefs.getString("font_preference", "Default") ?: "Default"


        val defaultExts = setOf("mp4", "mkv", "mp3", "webm", "3gp", "avi", "mov", "flv", "wmv", "m4v", "aac", "wav", "flac", "opus", "m4a")
        val savedExts = prefs.getStringSet("extensions", null)
        
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic")
        val exts = if (savedExts != null) {
            (savedExts.filterNot { it in imageExts } + setOf("opus", "m4a")).distinct()
        } else {
            defaultExts.toList()
        }
        
        _extensions.value = exts
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


    private fun getFileNameVariants(fileName: String?, uri: String?): Set<String> {
        val variants = mutableSetOf<String>()
        fun addVariantsFor(raw: String) {
            val trimmed = raw.trim()
            if (trimmed.isNotBlank() && trimmed != "Unknown") {
                variants.add(trimmed)
                val dotIndex = trimmed.lastIndexOf('.')
                if (dotIndex > 0) {
                    val base = trimmed.substring(0, dotIndex).trim()
                    if (base.isNotBlank()) variants.add(base)
                }
                try {
                    val decoded = Uri.decode(trimmed)
                    if (decoded.isNotBlank()) {
                        variants.add(decoded)
                        val decDot = decoded.lastIndexOf('.')
                        if (decDot > 0) {
                            val base = decoded.substring(0, decDot).trim()
                            if (base.isNotBlank()) variants.add(base)
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        if (!fileName.isNullOrBlank()) {
            addVariantsFor(fileName)
        }
        if (!uri.isNullOrBlank()) {
            try {
                val parsedUri = Uri.parse(uri)
                val lastPath = parsedUri.lastPathSegment
                if (!lastPath.isNullOrBlank()) {
                    val seg = if (lastPath.contains('/')) lastPath.substringAfterLast('/') else lastPath
                    val segDoc = if (seg.contains(':')) seg.substringAfterLast(':') else seg
                    addVariantsFor(segDoc)
                }
            } catch (e: Exception) {}
        }
        return variants
    }

    fun savePlaybackState(uri: String, position: Long, duration: Long, fileName: String? = null) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
            .putLong("time_$uri", now)
        if (position > 0L) {
            editor.putLong("pos_$uri", position)
        }
        if (duration > 0L) {
            editor.putLong("dur_$uri", duration)
        }
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            editor.putLong("time_fn_$v", now)
            if (position > 0L) {
                editor.putLong("pos_fn_$v", position)
            }
            if (duration > 0L) {
                editor.putLong("dur_fn_$v", duration)
            }
        }
        editor.apply()
    }

    fun markAsOpened(uri: String, fileName: String? = null) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit().putLong("time_$uri", now)
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            editor.putLong("time_fn_$v", now)
        }
        editor.apply()
    }

    fun savePlaybackSpeed(uri: String, speed: Float) {
        prefs.edit().putFloat("speed_$uri", speed).apply()
    }

    fun getPlaybackSpeed(uri: String): Float {
        return prefs.getFloat("speed_$uri", 1.0f)
    }

    fun saveTrackSelection(uri: String, trackType: Int, trackIndex: Int) {
        prefs.edit().putInt("track_${trackType}_$uri", trackIndex).apply()
    }

    fun getTrackSelection(uri: String, trackType: Int): Int {
        return prefs.getInt("track_${trackType}_$uri", -1)
    }

    fun getLastPlayedTime(uri: String, fileName: String? = null): Long {
        val directTime = prefs.getLong("time_$uri", 0L)
        if (directTime > 0L) return directTime
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            val fnTime = prefs.getLong("time_fn_$v", 0L)
            if (fnTime > 0L) return fnTime
        }
        return 0L
    }

    fun getPlaybackPosition(uri: String, fileName: String? = null): Long {
        val pos = prefs.getLong("pos_$uri", 0L)
        if (pos > 0L) return pos
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            val fnPos = prefs.getLong("pos_fn_$v", 0L)
            if (fnPos > 0L) return fnPos
        }
        return 0L
    }

    fun getStoredDuration(uri: String, fileName: String? = null): Long {
        val dur = prefs.getLong("dur_$uri", -1L)
        if (dur > 0L) return dur
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            val fnDur = prefs.getLong("dur_fn_$v", -1L)
            if (fnDur > 0L) return fnDur
        }
        return -1L
    }

    fun saveVideoOrientation(uri: String, isPortrait: Boolean) {
        prefs.edit().putBoolean("orient_$uri", isPortrait).apply()
    }

    fun getVideoOrientation(uri: String): Boolean? {
        return if (prefs.contains("orient_$uri")) {
            prefs.getBoolean("orient_$uri", false)
        } else {
            null
        }
    }

    fun removePlaybackState(uri: String, fileName: String? = null) {
        val editor = prefs.edit()
            .remove("time_$uri")
            .remove("pos_$uri")
            .remove("dur_$uri")
            .remove("orient_$uri")
        val variants = getFileNameVariants(fileName, uri)
        for (v in variants) {
            editor.remove("time_fn_$v")
                .remove("pos_fn_$v")
                .remove("dur_fn_$v")
        }
        editor.apply()
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
        get() = prefs.getBoolean("center_channel_enabled", true)
        set(value) {
            prefs.edit().putBoolean("center_channel_enabled", value).apply()
            com.example.service.PlayerManager.applyAudioEffects(this)
        }

    var eqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
            com.example.service.PlayerManager.applyAudioEffects(this)
        }

    var nightModeEnabled: Boolean
        get() = prefs.getBoolean("night_mode_enabled", true)
        set(value) {
            prefs.edit().putBoolean("night_mode_enabled", value).apply()
            com.example.service.PlayerManager.applyAudioEffects(this)
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
        com.example.service.PlayerManager.applyAudioEffects(this)
    }

    fun getNotificationPriority(): List<String> {
        val defaultPriority = "Loop,Playlist,Floating Player,Close"
        val saved = prefs.getString("notification_priority", defaultPriority) ?: defaultPriority
        return saved.replace("PiP", "Floating Player").split(",")
    }
    
    fun setNotificationPriority(priority: List<String>) {
        prefs.edit().putString("notification_priority", priority.joinToString(",")).apply()
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
