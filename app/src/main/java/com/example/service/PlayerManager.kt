package com.example.service
import kotlinx.coroutines.launch

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Equalizer
import android.media.audiofx.DynamicsProcessing
import android.os.Build

object PlayerManager {
    var exoPlayer: ExoPlayer? = null
    var loudnessEnhancer: LoudnessEnhancer? = null
    var equalizer: Equalizer? = null
    var dynamicsProcessing: DynamicsProcessing? = null
    val centerChannelProcessor = CenterChannelAudioProcessor()

    fun initialize(context: Context, skipSilence: Boolean = false) {
        if (exoPlayer != null) return
        com.example.LogKeeper.log("Initializing PlayerManager ExoPlayer", "PlayerManager")
        
        val dataSourceFactory = DefaultDataSource.Factory(context)
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

        val settings = com.example.data.SettingsManager.getInstance(context.applicationContext)
        val customMediaCodecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecure,
                requiresTunneling
            )
            val result = ArrayList<androidx.media3.exoplayer.mediacodec.MediaCodecInfo>(decoders)

            try {
                // Ensure software decoders (like c2.android.* and OMX.google.*) are always available in the fallback list
                val swDecoders = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                    mimeType,
                    /* requiresSecure= */ false,
                    /* requiresTunneling= */ false
                ).filter { it.softwareOnly || !it.hardwareAccelerated }

                for (sw in swDecoders) {
                    if (result.none { it.name == sw.name }) {
                        result.add(sw)
                    }
                }
            } catch (e: Exception) {
                com.example.LogKeeper.logError("PlayerManager", "Failed to query swDecoders", e)
            }

            // On known problematic hardware chipsets (e.g. Unisoc c2.unisoc.*) or when user selects SW preference,
            // place software decoders ahead of failing hardware decoders to avoid fatal native buffer crashes
            val isProblematicHw = result.any { it.name.contains("unisoc", ignoreCase = true) || it.name.contains("sprd", ignoreCase = true) }
            if (settings.decoderPriority == 2 || (isProblematicHw && (mimeType.contains("vp9", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true)))) {
                result.sortByDescending { it.softwareOnly || !it.hardwareAccelerated }
            }

            result
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
                    else -> androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
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
        
        exoPlayer?.pauseAtEndOfMediaItems = true
        
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                exoPlayer?.pauseAtEndOfMediaItems = (repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED || playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    val count = exoPlayer?.mediaItemCount ?: 0
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED || count == 0) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val db = com.example.data.AppDatabase.getDatabase(context)
                                val dao = db.playlistDao()
                                val temp = dao.getAllPlaylistsSync().find { it.name == "Temp Current" }
                                if (temp != null) {
                                    dao.deletePlaylistById(temp.id)
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            }

            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                if (events.contains(androidx.media3.common.Player.EVENT_TIMELINE_CHANGED)) {
                    if (player.mediaItemCount == 0) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                val db = com.example.data.AppDatabase.getDatabase(context)
                                val dao = db.playlistDao()
                                val temp = dao.getAllPlaylistsSync().find { it.name == "Temp Current" }
                                if (temp != null) {
                                    dao.deletePlaylistById(temp.id)
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                        
                        equalizer?.release()
                        try { equalizer = Equalizer(0, audioSessionId) } catch (e: Exception) {}
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            dynamicsProcessing?.release()
                            try {
                                val config = DynamicsProcessing.Config.Builder(
                                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                                    2, true, 0, true, 0, true, 0, true
                                )
                                .build()
                                dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config)
                            } catch (e: Exception) {}
                        }
                        
                        val settings = com.example.data.SettingsManager.getInstance(context.applicationContext)
                        
                        if (settings.audioBoosterEnabled && settings.boostGainMb > 0) {
                            loudnessEnhancer?.setTargetGain(settings.boostGainMb)
                            loudnessEnhancer?.enabled = true
                        } else {
                            loudnessEnhancer?.enabled = false
                        }
                        
                        applyAudioEffects(settings)
                        
                    } catch (e: Exception) {
                        com.example.LogKeeper.logError("PlayerManager", "Failed to create AudioEffects on session change", e)
                    }
                }
            }
        })

        exoPlayer?.audioSessionId?.let { sessionId ->
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                    loudnessEnhancer?.enabled = false
                } catch (e: Exception) {
                    com.example.LogKeeper.logError("PlayerManager", "Failed to create LoudnessEnhancer", e)
                }
            }
        }
        val initialSettings = com.example.data.SettingsManager.getInstance(context.applicationContext)
        applyAudioEffects(initialSettings)
    }

    
    fun applyAudioEffects(settings: com.example.data.SettingsManager) {
        centerChannelProcessor.enabled = settings.centerChannelEnabled
        
        try {
            equalizer?.let { eq ->
                eq.enabled = settings.eqEnabled
                if (settings.eqEnabled) {
                    val levels = settings.getEqLevels()
                    if (levels.isNotEmpty() && levels.size == eq.numberOfBands.toInt()) {
                        for (i in 0 until eq.numberOfBands) {
                            eq.setBandLevel(i.toShort(), levels[i].toShort())
                        }
                    }
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicsProcessing?.let { dp ->
                    dp.enabled = settings.nightModeEnabled
                    if (settings.nightModeEnabled) {
                        // Very aggressive compression for night mode / loudness leveling
                        val mbc = DynamicsProcessing.Mbc(true, true, 1)
                        val mbcBand = DynamicsProcessing.MbcBand(true, 1000f, 50f, 200f, 4f, -40f, 10f, -90f, 1f, 0f, 5f)
                        mbc.setBand(0, mbcBand)
                        dp.setMbcAllChannelsTo(mbc)
                    }
                }
            }
        } catch (e: Exception) {
            com.example.LogKeeper.logError("PlayerManager", "Error applying audio effects", e)
        }
    }

    fun setBoostGain(gainMb: Int) {
        if (gainMb <= 0) {
            loudnessEnhancer?.enabled = false
        } else {
            loudnessEnhancer?.setTargetGain(gainMb)
            loudnessEnhancer?.enabled = true
        }
    }
    
    fun applyAudioBoosterSettings(enabled: Boolean, gainMb: Int) {
        if (!enabled || gainMb <= 0) {
            loudnessEnhancer?.enabled = false
        } else {
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

    fun release() {
        val player = exoPlayer
        exoPlayer = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                player?.let { p ->
                    try { p.stop() } catch (e: Exception) {}
                    try { p.clearVideoSurface() } catch (e: Exception) {}
                    try { p.clearMediaItems() } catch (e: Exception) {}
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post {
                        try { p.release() } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                com.example.LogKeeper.logError("PlayerManager", "Error releasing ExoPlayer", e)
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
    }
}
