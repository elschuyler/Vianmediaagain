package shura.media.service
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build

data class PlayerSnapshot(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val mediaItem: MediaItem? = null,
    val currentMediaUri: android.net.Uri? = null,
    val currentTitle: String = "",
    val playlist: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f
)

data class FloatingPlayerSessionState(
    var windowX: Int? = null,
    var windowY: Int? = null,
    var windowWidth: Int? = null,
    var windowHeight: Int? = null,
    var audioWindowWidth: Int? = null,
    var audioWindowHeight: Int? = null,
    var bubbleX: Int? = null,
    var bubbleY: Int? = null,
    var isMinimized: Boolean = false,
    var isAspectRatioBroken: Boolean = false
) {
    fun reset() {
        windowX = null
        windowY = null
        windowWidth = null
        windowHeight = null
        audioWindowWidth = null
        audioWindowHeight = null
        bubbleX = null
        bubbleY = null
        isMinimized = false
        isAspectRatioBroken = false
    }
}

object PlayerManager {
    var exoPlayer: ExoPlayer? = null
    var loudnessEnhancer: LoudnessEnhancer? = null
    var equalizer: Equalizer? = null
    var dynamicsProcessing: DynamicsProcessing? = null
    val centerChannelProcessor = CenterChannelAudioProcessor()
    val floatingSession = FloatingPlayerSessionState()
    var isImplicitFolderQueue: Boolean = false
    @Volatile
    var isQueuePopulationInProgress: Boolean = false
    @Volatile
    var isFloatingVideoActive: Boolean = false
    @Volatile
    var isPlayerScreenVisible: Boolean = false
    @Volatile
    private var overlayTickerActive: Boolean = false
    @Volatile
    private var lastObservedItemIndex: Int = -1
    @Volatile
    private var lastObservedMediaId: String? = null
    @Volatile
    private var lastObservedTitle: String? = null
    @Volatile
    private var lastObservedDuration: Long = 0L
    @Volatile
    private var pendingAutoEndedFromIndex: Int = -1
    @Volatile
    private var pendingAutoEndedMediaId: String? = null
    @Volatile
    private var pendingAutoEndedTitle: String? = null
    @Volatile
    private var pendingAutoEndedDuration: Long = 0L

    fun isAudioOnlyHint(context: Context, uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        val mime = try { context.contentResolver.getType(uri)?.lowercase() } catch (_: Exception) { null }
        if (mime != null) {
            if (mime.startsWith("audio/")) return true
            if (mime.startsWith("video/")) return false
        }
        val ext = uri.toString().substringBefore('?').substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext != "ogg" && ext in shura.media.data.COMMON_AUDIO_EXTENSIONS
    }

    fun hasSupportedVideoTrack(tracks: androidx.media3.common.Tracks?): Boolean {
        if (tracks == null) return false
        return tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_VIDEO && group.length > 0 && group.isSupported
        }
    }

    private val blacklistedDecoders = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun blacklistDecoder(name: String) {
        if (name.isNotEmpty()) {
            blacklistedDecoders.add(name)
            shura.media.LogKeeper.log("Blacklisted failing decoder: $name", "PlayerManager")
        }
    }

    fun clearBlacklistedDecoders() {
        blacklistedDecoders.clear()
    }

    private val _playbackState = MutableStateFlow(PlayerSnapshot())
    val playbackState: StateFlow<PlayerSnapshot> = _playbackState.asStateFlow()

    private val playerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var progressTickerJob: Job? = null
    private var appContext: Context? = null

    fun initialize(context: Context, skipSilence: Boolean = false) {
        if (exoPlayer != null) return
        val appCtx = context.applicationContext
        appContext = appCtx
        shura.media.LogKeeper.log("Initializing PlayerManager ExoPlayer", "PlayerManager")
        
        val baseDataSourceFactory = DefaultDataSource.Factory(appCtx)
        val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val baseDataSource = baseDataSourceFactory.createDataSource()
            object : androidx.media3.datasource.DataSource by baseDataSource {
                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    return try {
                        baseDataSource.open(dataSpec)
                    } catch (e: Exception) {
                        val isSec = e is SecurityException || e.cause is SecurityException || e.message?.contains("SecurityException", ignoreCase = true) == true || e.message?.contains("Permission Denial", ignoreCase = true) == true
                        if (isSec) {
                            val resolved = shura.media.data.MediaUriResolver.resolveToPlayableUri(appCtx, dataSpec.uri)
                            if (resolved != dataSpec.uri) {
                                shura.media.LogKeeper.log("ExoPlayer DataSource auto-recovered from SecurityException: ${dataSpec.uri} -> $resolved", "PlayerManager")
                                val newSpec = dataSpec.buildUpon().setUri(resolved).build()
                                return baseDataSource.open(newSpec)
                            }
                        }
                        throw e
                    }
                }
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val settings = shura.media.data.SettingsManager.getInstance(context.applicationContext)
        val customMediaCodecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecure,
                requiresTunneling
            )
            if (blacklistedDecoders.isNotEmpty()) {
                decoders.filter { it.name !in blacklistedDecoders }
            } else {
                decoders
            }
        }

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(centerChannelProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }.setMediaCodecSelector(customMediaCodecSelector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (settings.decoderPriority) {
                    0 -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    1 -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    2 -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )

        exoPlayer = ExoPlayer.Builder(context.applicationContext)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSkipSilenceEnabled(skipSilence)
            .build()
        
        exoPlayer?.let { p ->
            p.pauseAtEndOfMediaItems = (p.repeatMode == Player.REPEAT_MODE_OFF)
        }
        
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                exoPlayer?.pauseAtEndOfMediaItems = (repeatMode == Player.REPEAT_MODE_OFF)
                if (repeatMode != Player.REPEAT_MODE_OFF) {
                    pendingAutoEndedFromIndex = -1
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    if (overlayTickerActive) startProgressTicker()
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    stopProgressTicker()
                    if (playbackState == Player.STATE_ENDED) {
                        val p = exoPlayer
                        if (p != null && p.repeatMode == Player.REPEAT_MODE_OFF) {
                            val currentItem = p.currentMediaItem
                            val uriStr = currentItem?.localConfiguration?.uri?.toString() ?: currentItem?.mediaId
                            if (!uriStr.isNullOrBlank()) {
                                val title = currentItem?.mediaMetadata?.title?.toString()
                                    ?: uriStr.substringAfterLast('/')
                                val dur = p.duration.takeIf { it > 0L }
                                    ?: lastObservedDuration.takeIf { it > 0L }
                                    ?: 1000L
                                shura.media.data.SettingsManager.getInstance(appCtx)
                                    .markAsFinished(uriStr, title, dur)
                            }
                        }
                    }
                } else if (playbackState == Player.STATE_READY && pendingAutoEndedFromIndex == -1) {
                    val p = exoPlayer
                    val item = p?.currentMediaItem
                    if (p != null && item != null) {
                        lastObservedItemIndex = p.currentMediaItemIndex
                        lastObservedMediaId = item.localConfiguration?.uri?.toString() ?: item.mediaId
                        lastObservedTitle = item.mediaMetadata.title?.toString()
                            ?: lastObservedMediaId?.substringAfterLast('/')
                        val dur = p.duration
                        if (dur > 0L) lastObservedDuration = dur
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (overlayTickerActive) startProgressTicker()
                } else {
                    stopProgressTicker()
                    flushProgressToStorage()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    val p = exoPlayer
                    if (p != null && p.repeatMode == Player.REPEAT_MODE_OFF) {
                        val endedIdx = pendingAutoEndedFromIndex
                        if (endedIdx in 0 until p.mediaItemCount && endedIdx != p.currentMediaItemIndex) {
                            val endedId = pendingAutoEndedMediaId
                            val endedTitle = pendingAutoEndedTitle
                            val endedDur = pendingAutoEndedDuration.coerceAtLeast(1000L)
                            pendingAutoEndedFromIndex = -1
                            if (!endedId.isNullOrBlank()) {
                                shura.media.data.SettingsManager.getInstance(appCtx)
                                    .markAsFinished(endedId, endedTitle, endedDur)
                            }
                            p.playWhenReady = false
                            p.seekTo(endedIdx, 0L)
                        } else {
                            pendingAutoEndedFromIndex = -1
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val p = exoPlayer
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    p != null &&
                    p.repeatMode == Player.REPEAT_MODE_OFF &&
                    lastObservedItemIndex >= 0 &&
                    lastObservedItemIndex != p.currentMediaItemIndex
                ) {
                    pendingAutoEndedFromIndex = lastObservedItemIndex
                    pendingAutoEndedMediaId = lastObservedMediaId
                    pendingAutoEndedTitle = lastObservedTitle
                    pendingAutoEndedDuration = lastObservedDuration.coerceAtLeast(1000L)
                } else {
                    pendingAutoEndedFromIndex = -1
                    // When transitioning between items, persist the previous item's progress
                    flushProgressToStorage()
                }
                if (p != null) {
                    lastObservedItemIndex = p.currentMediaItemIndex
                    lastObservedMediaId = mediaItem?.localConfiguration?.uri?.toString() ?: mediaItem?.mediaId
                    lastObservedTitle = mediaItem?.mediaMetadata?.title?.toString()
                        ?: lastObservedMediaId?.substringAfterLast('/')
                    lastObservedDuration = p.duration.takeIf { it > 0L } ?: 0L
                }
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                val timelineChanged = events.contains(androidx.media3.common.Player.EVENT_TIMELINE_CHANGED)
                if (pendingAutoEndedFromIndex == -1 && player.currentMediaItem != null) {
                    if (timelineChanged) {
                        lastObservedItemIndex = player.currentMediaItemIndex
                    }
                    if (player.duration > 0L && lastObservedItemIndex == player.currentMediaItemIndex) {
                        lastObservedDuration = player.duration
                    }
                }
                updateSnapshot(rebuildPlaylist = timelineChanged)
                if (timelineChanged && player.mediaItemCount == 0) {
                    playerScope.launch {
                        try {
                            val db = shura.media.data.AppDatabase.getDatabase(appCtx)
                            val dao = db.playlistDao()
                            val temp = dao.getAllPlaylistsSync().find { it.name == "Temp Current" }
                            if (temp != null) {
                                dao.deletePlaylistById(temp.id)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = null
                        equalizer?.release()
                        equalizer = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            dynamicsProcessing?.release()
                            dynamicsProcessing = null
                        }

                        val settings = shura.media.data.SettingsManager.getInstance(appCtx)
                        applyAudioEffects(settings)
                    } catch (e: Exception) {
                        shura.media.LogKeeper.logError("PlayerManager", "Failed to update AudioEffects on session change", e)
                    }
                }
            }
        })

        updateSnapshot(rebuildPlaylist = true)
        val initialSettings = shura.media.data.SettingsManager.getInstance(context.applicationContext)
        applyAudioEffects(initialSettings)
    }

    
    fun applyAudioEffects(settings: shura.media.data.SettingsManager) {
        centerChannelProcessor.enabled = settings.centerChannelEnabled
        if (!settings.centerChannelEnabled) {
            centerChannelProcessor.releaseScratchBuffer()
        }
        
        try {
            val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
            if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

            // 1. LoudnessEnhancer - On demand
            if (settings.audioBoosterEnabled && settings.boostGainMb > 0) {
                if (loudnessEnhancer == null) {
                    try { loudnessEnhancer = LoudnessEnhancer(sessionId) } catch (e: Exception) {}
                }
                loudnessEnhancer?.setTargetGain(settings.boostGainMb)
                loudnessEnhancer?.enabled = true
            } else {
                loudnessEnhancer?.enabled = false
            }

            // 2. Equalizer - On demand
            if (settings.eqEnabled) {
                if (equalizer == null) {
                    try { equalizer = Equalizer(0, sessionId) } catch (e: Exception) {}
                }
                equalizer?.let { eq ->
                    eq.enabled = true
                    val levels = settings.getEqLevels()
                    if (levels.isNotEmpty() && levels.size == eq.numberOfBands.toInt()) {
                        for (i in 0 until eq.numberOfBands) {
                            eq.setBandLevel(i.toShort(), levels[i].toShort())
                        }
                    }
                }
            } else {
                equalizer?.enabled = false
            }

            // 3. DynamicsProcessing (Night mode) - On demand
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (settings.nightModeEnabled) {
                    if (dynamicsProcessing == null) {
                        try {
                            val config = DynamicsProcessing.Config.Builder(
                                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                                2, true, 0, true, 0, true, 0, true
                            ).build()
                            dynamicsProcessing = DynamicsProcessing(0, sessionId, config)
                        } catch (e: Exception) {}
                    }
                    dynamicsProcessing?.let { dp ->
                        dp.enabled = true
                        val mbc = DynamicsProcessing.Mbc(true, true, 1)
                        val mbcBand = DynamicsProcessing.MbcBand(true, 1000f, 50f, 200f, 4f, -40f, 10f, -90f, 1f, 0f, 5f)
                        mbc.setBand(0, mbcBand)
                        dp.setMbcAllChannelsTo(mbc)
                    }
                } else {
                    dynamicsProcessing?.enabled = false
                }
            }
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("PlayerManager", "Error applying audio effects", e)
        }
    }

    fun setBoostGain(gainMb: Int) {
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (gainMb <= 0) {
            loudnessEnhancer?.enabled = false
        } else {
            if (loudnessEnhancer == null && sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try { loudnessEnhancer = LoudnessEnhancer(sessionId) } catch (e: Exception) {}
            }
            loudnessEnhancer?.setTargetGain(gainMb)
            loudnessEnhancer?.enabled = true
        }
    }
    
    fun applyAudioBoosterSettings(enabled: Boolean, gainMb: Int) {
        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (!enabled || gainMb <= 0) {
            loudnessEnhancer?.enabled = false
        } else {
            if (loudnessEnhancer == null && sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try { loudnessEnhancer = LoudnessEnhancer(sessionId) } catch (e: Exception) {}
            }
            loudnessEnhancer?.setTargetGain(gainMb)
            loudnessEnhancer?.enabled = true
        }
    }

    fun addSubtitle(uriStr: String) {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        
        val mimeType = if (uriStr.endsWith(".vtt", true)) androidx.media3.common.MimeTypes.TEXT_VTT
            else if (uriStr.endsWith(".ssa", true) || uriStr.endsWith(".ass", true)) androidx.media3.common.MimeTypes.TEXT_SSA
            else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        val subtitleConfig = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(uriStr))
            .setMimeType(mimeType)
            .setLanguage(null)
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()
        
        val newItemBuilder = currentItem.buildUpon()
        val oldConfigs = currentItem.localConfiguration?.subtitleConfigurations
        if (oldConfigs != null) {
            newItemBuilder.setSubtitleConfigurations(oldConfigs + subtitleConfig)
        } else {
            newItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        
        val newItem = newItemBuilder.build()
        val currentItemIndex = player.currentMediaItemIndex
        player.replaceMediaItem(currentItemIndex, newItem)
        
        val builder = player.trackSelectionParameters.buildUpon()
        builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
        player.trackSelectionParameters = builder.build()
    }

    fun detachVideoSurface() {
        if (isFloatingVideoActive) {
            shura.media.LogKeeper.log("detachVideoSurface ignored: floating video overlay is active", "PlayerManager")
            return
        }
        val player = exoPlayer ?: return
        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                if (isFloatingVideoActive) {
                    shura.media.LogKeeper.log("detachVideoSurface skipped: floating video overlay became active", "PlayerManager")
                    return@post
                }
                flushProgressToStorage()
                try {
                    player.clearVideoSurface()
                    shura.media.LogKeeper.log("Video surface successfully cleared for audio-only remote operation", "PlayerManager")
                } catch (e: Exception) {
                    shura.media.LogKeeper.logError("PlayerManager", "Error in player.clearVideoSurface()", e)
                }
            }
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("PlayerManager", "Failed to post clearVideoSurface", e)
        }
    }

    fun setVideoTrackEnabled(enabled: Boolean) {
        val player = exoPlayer ?: return
        flushProgressToStorage()
        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    val currentParams = player.trackSelectionParameters
                    val isCurrentlyDisabled = currentParams.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)
                    if (isCurrentlyDisabled == enabled) {
                        player.trackSelectionParameters = currentParams.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
                            .build()
                        shura.media.LogKeeper.log("PlayerManager: setVideoTrackEnabled($enabled) - video track disabled: ${!enabled}", "PlayerManager")
                    }
                } catch (e: Exception) {
                    shura.media.LogKeeper.logError("PlayerManager", "Failed to update trackSelectionParameters in setVideoTrackEnabled", e)
                }
            }
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("PlayerManager", "Failed to post setVideoTrackEnabled", e)
        }
    }

    fun updateSnapshot(rebuildPlaylist: Boolean = true) {
        val player = exoPlayer ?: run {
            _playbackState.value = PlayerSnapshot()
            return
        }
        val currentItem = player.currentMediaItem
        val uri = currentItem?.localConfiguration?.uri ?: currentItem?.mediaId?.let { android.net.Uri.parse(it) }
        val title = currentItem?.mediaMetadata?.title?.toString()
            ?: currentItem?.mediaMetadata?.displayTitle?.toString()
            ?: uri?.lastPathSegment
            ?: ""

        val previous = _playbackState.value
        val playlist = if (rebuildPlaylist || previous.playlist.size != player.mediaItemCount) {
            val built = ArrayList<MediaItem>(player.mediaItemCount)
            for (i in 0 until player.mediaItemCount) {
                built.add(player.getMediaItemAt(i))
            }
            built
        } else {
            previous.playlist
        }

        val dur = if (player.duration < 0L) 0L else player.duration
        val pos = if (player.currentPosition < 0L) 0L else player.currentPosition

        _playbackState.value = PlayerSnapshot(
            isPlaying = player.isPlaying,
            currentPosition = pos,
            duration = dur,
            playbackState = player.playbackState,
            mediaItem = currentItem,
            currentMediaUri = uri,
            currentTitle = title,
            playlist = playlist,
            currentIndex = player.currentMediaItemIndex,
            repeatMode = player.repeatMode,
            shuffleModeEnabled = player.shuffleModeEnabled,
            playbackSpeed = player.playbackParameters.speed
        )
    }

    fun setOverlayProgressTickerActive(active: Boolean) {
        overlayTickerActive = active
        if (active) {
            val player = exoPlayer
            if (player != null && (player.isPlaying || player.playbackState == Player.STATE_BUFFERING)) {
                startProgressTicker()
            } else {
                updateSnapshot(rebuildPlaylist = false)
            }
        } else {
            stopProgressTicker()
        }
    }

    fun startProgressTicker() {
        if (!overlayTickerActive) return
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = playerScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            while (overlayTickerActive) {
                val player = exoPlayer
                if (player != null && (player.isPlaying || player.playbackState == Player.STATE_BUFFERING)) {
                    val pos = if (player.currentPosition < 0L) 0L else player.currentPosition
                    val dur = if (player.duration < 0L) 0L else player.duration
                    val current = _playbackState.value
                    if (current.currentPosition != pos || current.duration != dur || current.isPlaying != player.isPlaying) {
                        _playbackState.value = current.copy(
                            currentPosition = pos,
                            duration = dur,
                            isPlaying = player.isPlaying,
                            playbackState = player.playbackState
                        )
                    }
                } else {
                    break
                }
                delay(500)
            }
        }
    }

    fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    fun flushProgressToStorage(targetPlayer: ExoPlayer? = exoPlayer) {
        try {
            val player = targetPlayer ?: return
            val currentItem = player.currentMediaItem ?: return
            val uri = currentItem.localConfiguration?.uri ?: currentItem.mediaId.let { android.net.Uri.parse(it) } ?: return
            val uriStr = uri.toString()
            val pos = player.currentPosition
            val dur = player.duration
            val title = currentItem.mediaMetadata.title?.toString() ?: uri.lastPathSegment ?: ""
            val ctx = appContext ?: return

            if (pos > 0L) {
                val settings = shura.media.data.SettingsManager.getInstance(ctx)
                if (!settings.isFinished(uriStr, title)) {
                    settings.savePlaybackState(uriStr, pos, dur, title)
                    shura.media.LogKeeper.log("PlayerManager: Flushed progress to storage ($pos ms)", "PlayerManager")
                } else {
                    shura.media.LogKeeper.log("PlayerManager: Skipping progress flush because video is already marked finished", "PlayerManager")
                }
            }
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("PlayerManager", "Failed to flush progress to storage", e)
        }
    }

    fun releasePlayer() = release()

    fun release() {
        pendingQueueItems = null
        pendingQueueIndex = 0
        progressTickerJob?.cancel()
        progressTickerJob = null
        _playbackState.value = PlayerSnapshot()
        val player = exoPlayer
        exoPlayer = null
        if (player != null) {
            val releaseBlock = {
                try {
                    flushProgressToStorage(player)
                    val hasError = (player.playerError != null)
                    try { player.stop() } catch (e: Exception) {}
                    if (!hasError) {
                        try { player.clearVideoSurface() } catch (e: Exception) {}
                        try { player.clearMediaItems() } catch (e: Exception) {}
                    }
                    player.release()
                } catch (e: Exception) {
                    shura.media.LogKeeper.log("PlayerManager: Handled ExoPlayer release (${e.message})", "PlayerManager")
                }
                Unit
            }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                releaseBlock()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post { releaseBlock() }
            }
        }
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {}
        loudnessEnhancer = null
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                dynamicsProcessing?.release()
            } catch (e: Exception) {}
        }
        dynamicsProcessing = null
        isImplicitFolderQueue = false
        isQueuePopulationInProgress = false
        isFloatingVideoActive = false
        isPlayerScreenVisible = false
        overlayTickerActive = false
        lastObservedItemIndex = -1
        lastObservedMediaId = null
        lastObservedTitle = null
        lastObservedDuration = 0L
        pendingAutoEndedFromIndex = -1
        pendingAutoEndedMediaId = null
        pendingAutoEndedTitle = null
        pendingAutoEndedDuration = 0L
        pendingQueueItems = null
        pendingQueueIndex = 0
        pendingQueueIsImplicit = true
    }

    private var pendingQueueItems: List<MediaItem>? = null
    private var pendingQueueIndex: Int = 0
    private var pendingQueueIsImplicit: Boolean = true

    fun setPendingQueue(items: List<MediaItem>, startIndex: Int, isImplicit: Boolean = true) {
        pendingQueueItems = items
        pendingQueueIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        pendingQueueIsImplicit = isImplicit
        isQueuePopulationInProgress = false
        shura.media.LogKeeper.log("PlayerManager: setPendingQueue with ${items.size} items at index $pendingQueueIndex (implicit=$isImplicit)", "PlayerManager")
    }

    fun hasPendingQueue(): Boolean = !pendingQueueItems.isNullOrEmpty()

    fun consumePendingQueue(): Pair<List<MediaItem>, Int>? {
        val items = pendingQueueItems ?: return null
        val idx = pendingQueueIndex
        pendingQueueItems = null
        pendingQueueIndex = 0
        return Pair(items, idx)
    }

    fun consumePendingQueueWithImplicitFlag(): Triple<List<MediaItem>, Int, Boolean>? {
        val items = pendingQueueItems ?: return null
        val idx = pendingQueueIndex
        val implicit = pendingQueueIsImplicit
        pendingQueueItems = null
        pendingQueueIndex = 0
        pendingQueueIsImplicit = true
        return Triple(items, idx, implicit)
    }

    fun applyFolderQueueAroundCurrent(
        activeMediaId: String,
        fullQueue: List<MediaItem>,
        startIndex: Int,
        isImplicit: Boolean = true
    ) {
        val attachBlock = {
            try {
                val player = exoPlayer
                if (player != null && fullQueue.size > 1 && startIndex in fullQueue.indices) {
                    val currentId = player.currentMediaItem?.mediaId
                    if (currentId == activeMediaId && player.mediaItemCount <= 1) {
                        val preceding = fullQueue.subList(0, startIndex)
                        val succeeding = fullQueue.subList(startIndex + 1, fullQueue.size)
                        if (preceding.isNotEmpty()) {
                            player.addMediaItems(0, preceding)
                        }
                        if (succeeding.isNotEmpty()) {
                            player.addMediaItems(player.mediaItemCount, succeeding)
                        }
                        isImplicitFolderQueue = isImplicit
                    }
                } else if (player == null && fullQueue.isNotEmpty()) {
                    setPendingQueue(fullQueue, startIndex, isImplicit)
                }
            } finally {
                isQueuePopulationInProgress = false
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            attachBlock()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(attachBlock)
        }
    }

    fun appendOrTransitionQueue(context: Context, newMediaItems: List<MediaItem>) {
        if (newMediaItems.isEmpty()) return
        val executeBlock = {
            val player = exoPlayer
            if (player != null) {
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(newMediaItems)
                    player.prepare()
                    player.play()
                    isImplicitFolderQueue = false
                } else {
                    if (isImplicitFolderQueue) {
                        val currentIdx = player.currentMediaItemIndex
                        val totalCount = player.mediaItemCount
                        if (currentIdx + 1 < totalCount) {
                            player.removeMediaItems(currentIdx + 1, totalCount)
                        }
                        player.addMediaItems(newMediaItems)
                        if (currentIdx > 0) {
                            player.removeMediaItems(0, currentIdx)
                        }
                        isImplicitFolderQueue = false
                        shura.media.LogKeeper.log("PlayerManager: Transitioned implicit folder queue to explicit user playlist (current item + ${newMediaItems.size} added)", "PlayerManager")
                    } else {
                        player.addMediaItems(newMediaItems)
                        shura.media.LogKeeper.log("PlayerManager: Appended ${newMediaItems.size} items to existing queue", "PlayerManager")
                    }
                }
                updateSnapshot()

                val allUris = mutableListOf<String>()
                for (i in 0 until player.mediaItemCount) {
                    allUris.add(player.getMediaItemAt(i).mediaId)
                }
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = shura.media.data.AppDatabase.getDatabase(context.applicationContext)
                        val playlistRepo = shura.media.data.PlaylistRepository(db.playlistDao())
                        playlistRepo.saveOrUpdateTemporaryPlaylist(allUris)
                    } catch (e: Exception) {
                        shura.media.LogKeeper.logError("PlayerManager", "Failed to sync temporary playlist", e)
                    }
                }
            }
        }

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            executeBlock()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(executeBlock)
        }
    }
}
