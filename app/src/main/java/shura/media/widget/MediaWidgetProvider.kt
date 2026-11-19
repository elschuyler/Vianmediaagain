package shura.media.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import shura.media.MainActivity
import shura.media.R

class MediaWidgetProvider : AppWidgetProvider() {
    private fun updateWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = android.content.ComponentName(context.applicationContext, MediaWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }


    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        cachedHasWidgets = true
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cachedHasWidgets = false
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        cachedHasWidgets = null
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        cachedHasWidgets = appWidgetIds.isNotEmpty()
        shura.media.LogKeeper.log("onUpdate started for ${appWidgetIds.size} widgets", "MediaWidgetProvider")
        try {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            shura.media.LogKeeper.log("Widget updated successfully for ${appWidgetIds.size} widgets", "MediaWidgetProvider")
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("MediaWidgetProvider", "Error in onUpdate", e)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        shura.media.LogKeeper.log("updateAppWidget started for widgetId $appWidgetId", "MediaWidgetProvider")
        try {
        val views = RemoteViews(context.packageName, R.layout.widget_media)

        // Pending intent to launch player
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                action = "shura.media.ACTION_OPEN_PLAYER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        // Top bar intents
        views.setOnClickPendingIntent(R.id.widget_btn_pip, getPendingIntent(context, "ACTION_PIP"))
        views.setOnClickPendingIntent(R.id.widget_btn_expand, getPendingIntent(context, "ACTION_OPEN_APP"))
        views.setOnClickPendingIntent(R.id.widget_btn_refresh, getPendingIntent(context, "ACTION_REFRESH"))
        
        // Intents for playback controls
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, "ACTION_PREV"))
        views.setOnClickPendingIntent(R.id.widget_btn_rewind, getPendingIntent(context, "ACTION_REWIND"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getPendingIntent(context, "ACTION_PLAY_PAUSE"))
        views.setOnClickPendingIntent(R.id.widget_btn_ffwd, getPendingIntent(context, "ACTION_FFWD"))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, "ACTION_NEXT"))
        views.setOnClickPendingIntent(R.id.widget_btn_stop, getPendingIntent(context, "ACTION_STOP"))
        views.setOnClickPendingIntent(R.id.widget_btn_loop, getPendingIntent(context, "ACTION_LOOP"))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, getPendingIntent(context, "ACTION_SHUFFLE"))
        
        // Bottom right intents
        views.setOnClickPendingIntent(R.id.widget_btn_close, getPendingIntent(context, "ACTION_CLOSE"))

        
        views.setOnClickPendingIntent(R.id.widget_btn_back, getPendingIntent(context, "ACTION_BACK_FOLDER"))
        
        // Play icon, title & progress update
        val player = shura.media.service.PlayerManager.exoPlayer
        val primaryColor = android.graphics.Color.parseColor("#3F51B5")
        val defaultColor = android.graphics.Color.parseColor("#19202D")
        if (player != null && player.mediaItemCount > 0 && player.currentMediaItem != null) {
            views.setTextViewText(R.id.widget_title, shura.media.service.PlayerManager.getResolvedTitleForItem(player.currentMediaItem) ?: "No Media")
            if (player.isPlaying) {
                views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_pause)
            } else {
                views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_play)
            }
            val dur = player.duration
            val pos = player.currentPosition.coerceAtLeast(0L)
            val progress = if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                ((pos.toFloat() / dur.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else 0
            views.setProgressBar(R.id.widget_progress, 100, progress, false)

            // Loop icon
            val loopMode = player.repeatMode
            if (loopMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                views.setImageViewResource(R.id.widget_btn_loop, R.drawable.ic_widget_loop_one)
                views.setInt(R.id.widget_btn_loop, "setColorFilter", primaryColor)
            } else if (loopMode == androidx.media3.common.Player.REPEAT_MODE_ALL) {
                views.setImageViewResource(R.id.widget_btn_loop, R.drawable.ic_widget_loop)
                views.setInt(R.id.widget_btn_loop, "setColorFilter", primaryColor)
            } else {
                views.setImageViewResource(R.id.widget_btn_loop, R.drawable.ic_widget_loop)
                views.setInt(R.id.widget_btn_loop, "setColorFilter", defaultColor)
            }

            // Shuffle icon
            if (player.shuffleModeEnabled) {
                views.setInt(R.id.widget_btn_shuffle, "setColorFilter", primaryColor)
            } else {
                views.setInt(R.id.widget_btn_shuffle, "setColorFilter", defaultColor)
            }
        } else {
            views.setTextViewText(R.id.widget_title, "No Media")
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
            views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_play)
            views.setImageViewResource(R.id.widget_btn_loop, R.drawable.ic_widget_loop)
            views.setInt(R.id.widget_btn_loop, "setColorFilter", defaultColor)
            views.setInt(R.id.widget_btn_shuffle, "setColorFilter", defaultColor)
        }

        // Hierarchy UI Update
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getString("explorer_mode", "current") ?: "current"
        val folderId = prefs.getString("folder_id", null)
        val searchQuery = prefs.getString("search_query", "")
        
        if (searchQuery?.isNotBlank() == true) {
            views.setTextViewText(R.id.widget_explorer_title, "Search Results")
            views.setViewVisibility(R.id.widget_btn_back, android.view.View.VISIBLE)
        } else {
            when (currentMode) {
                "root" -> {
                    views.setTextViewText(R.id.widget_explorer_title, "Library")
                    views.setViewVisibility(R.id.widget_btn_back, android.view.View.GONE)
                }
                "current" -> {
                    views.setTextViewText(R.id.widget_explorer_title, "Current")
                    views.setViewVisibility(R.id.widget_btn_back, android.view.View.VISIBLE)
                }
                "folders" -> {
                    views.setTextViewText(R.id.widget_explorer_title, "Folder List")
                    views.setViewVisibility(R.id.widget_btn_back, android.view.View.VISIBLE)
                }
                "folder_items" -> {
                    views.setTextViewText(R.id.widget_explorer_title, "Folder")
                    views.setViewVisibility(R.id.widget_btn_back, android.view.View.VISIBLE)
                }
                "playlists" -> {
                    views.setTextViewText(R.id.widget_explorer_title, "Playlists")
                    views.setViewVisibility(R.id.widget_btn_back, android.view.View.VISIBLE)
                }
            }
        }
        
        val searchIntent = Intent(context, WidgetSearchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val searchPendingIntent = PendingIntent.getActivity(
            context,
            2,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_search, searchPendingIntent)

        // Set up the collection (ListView)
        val serviceIntent = Intent(context, MediaWidgetService::class.java)
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        
        val token = getWidgetToken(context)
        val clickPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, MediaWidgetProvider::class.java)
                .setAction("ACTION_PLAY_ITEM")
                .putExtra(EXTRA_WIDGET_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
            shura.media.LogKeeper.log("updateAppWidget completed for widgetId $appWidgetId", "MediaWidgetProvider")
        } catch (e: Exception) {
            shura.media.LogKeeper.logError("MediaWidgetProvider", "Error in updateAppWidget for widgetId $appWidgetId", e)
        }
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MediaWidgetProvider::class.java)
            .setAction(action)
            .putExtra(EXTRA_WIDGET_TOKEN, getWidgetToken(context))
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val EXTRA_WIDGET_TOKEN = "EXTRA_WIDGET_TOKEN"
        private const val KEY_WIDGET_TOKEN = "widget_ipc_token"
        private val tokenLock = Any()
        @Volatile
        private var cachedHasWidgets: Boolean? = null

        fun hasInstalledWidgets(context: Context): Boolean {
            val cached = cachedHasWidgets
            if (cached != null) return cached
            return try {
                val mgr = AppWidgetManager.getInstance(context.applicationContext)
                val cn = android.content.ComponentName(context.applicationContext, MediaWidgetProvider::class.java)
                val has = mgr.getAppWidgetIds(cn).isNotEmpty()
                cachedHasWidgets = has
                has
            } catch (_: Exception) {
                false
            }
        }

        fun resetAllWidgetsToIdle(context: Context) {
            if (!hasInstalledWidgets(context)) return
            try {
                val appCtx = context.applicationContext
                val mgr = AppWidgetManager.getInstance(appCtx)
                val cn = android.content.ComponentName(appCtx, MediaWidgetProvider::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                if (ids.isNotEmpty()) {
                    val defaultColor = android.graphics.Color.parseColor("#19202D")
                    for (id in ids) {
                        val views = RemoteViews(appCtx.packageName, R.layout.widget_media)
                        views.setTextViewText(R.id.widget_title, "No Media")
                        views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_play)
                        views.setProgressBar(R.id.widget_progress, 100, 0, false)
                        views.setImageViewResource(R.id.widget_btn_loop, R.drawable.ic_widget_loop)
                        views.setInt(R.id.widget_btn_loop, "setColorFilter", defaultColor)
                        views.setInt(R.id.widget_btn_shuffle, "setColorFilter", defaultColor)
                        mgr.partiallyUpdateAppWidget(id, views)
                        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
                    }
                }
            } catch (_: Exception) {}
        }

        private fun getWidgetToken(context: Context): String {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            return prefs.getString(KEY_WIDGET_TOKEN, null) ?: synchronized(tokenLock) {
                prefs.getString(KEY_WIDGET_TOKEN, null) ?: run {
                    val bytes = ByteArray(16)
                    java.security.SecureRandom().nextBytes(bytes)
                    val generated = bytes.joinToString("") { "%02x".format(it) }
                    prefs.edit().putString(KEY_WIDGET_TOKEN, generated).apply()
                    generated
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        shura.media.LogKeeper.log("onReceive action: $action", "MediaWidgetProvider")

        val isSystemWidgetAction = action == "android.appwidget.action.APPWIDGET_UPDATE" ||
            action == "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS" ||
            action == "android.appwidget.action.APPWIDGET_ENABLED" ||
            action == "android.appwidget.action.APPWIDGET_DISABLED" ||
            action == "android.appwidget.action.APPWIDGET_DELETED"

        if (isSystemWidgetAction) {
            try {
                super.onReceive(context, intent)
            } catch (e: Exception) {
                shura.media.LogKeeper.logError("MediaWidgetProvider", "Error in onReceive standard action", e)
            }
            return
        }

        if (intent.getStringExtra(EXTRA_WIDGET_TOKEN) != getWidgetToken(context)) {
            shura.media.LogKeeper.logWarn("MediaWidgetProvider", "Rejected unauthorized widget action: $action")
            return
        }

        if (action == "ACTION_REFRESH") {
            try {
                updateWidgets(context)
            } catch (e: Exception) {
                shura.media.LogKeeper.logError("MediaWidgetProvider", "Error in ACTION_REFRESH", e)
            }
            return
        }
        
        if (action == "ACTION_OPEN_APP") {
            val openIntent = Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                this.action = "shura.media.ACTION_OPEN_PLAYER"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            shura.media.data.MediaUriResolver.safeStartActivity(context, openIntent)
            return
        }

        if (action == "ACTION_STOP" || action == "ACTION_CLOSE") {
            shura.media.service.PlaybackService.stopPlaybackAndRelease(context)
            updateWidgets(context)
            return
        }
        
        if (action == "ACTION_BACK_FOLDER") {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val currentMode = prefs.getString("explorer_mode", "current") ?: "current"
            val searchQuery = prefs.getString("search_query", "")
            
            if (searchQuery?.isNotBlank() == true) {
                prefs.edit().putString("search_query", "").apply()
            } else if (currentMode == "folder_items") {
                prefs.edit().putString("explorer_mode", "folders").putString("folder_id", null).apply()
            } else if (currentMode == "playlist_items") {
                prefs.edit().putString("explorer_mode", "playlists").putString("folder_id", null).apply()
            } else if (currentMode == "folders" || currentMode == "current" || currentMode == "playlists") {
                prefs.edit().putString("explorer_mode", "root").apply()
            }
            
            updateWidgets(context)
            return
        }

        if (action == "ACTION_PLAY_ITEM") {
            val widgetAction = intent.getStringExtra("WIDGET_ACTION")
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            
            when (widgetAction) {
                "NAVIGATE_CURRENT" -> {
                    prefs.edit().putString("explorer_mode", "current").apply()
                    updateWidgets(context)
                    return
                }
                "NAVIGATE_FOLDERS" -> {
                    prefs.edit().putString("explorer_mode", "folders").putString("folder_id", null).apply()
                    updateWidgets(context)
                    return
                }
                "NAVIGATE_PLAYLISTS" -> {
                    prefs.edit().putString("explorer_mode", "playlists").apply()
                    updateWidgets(context)
                    return
                }
                "OPEN_FOLDER" -> {
                    val folderId = intent.getStringExtra("EXTRA_FOLDER_ID")
                    prefs.edit().putString("explorer_mode", "folder_items").putString("folder_id", folderId).apply()
                    updateWidgets(context)
                    return
                }
                "OPEN_PLAYLIST" -> {
                    val folderId = intent.getStringExtra("EXTRA_FOLDER_ID")
                    prefs.edit().putString("explorer_mode", "playlist_items").putString("folder_id", folderId).apply()
                    updateWidgets(context)
                    return
                }
            }
        }

        // Media controller commands
        val widgetAction = intent.getStringExtra("WIDGET_ACTION")
        val isMediaCommand = action in listOf("ACTION_PLAY_PAUSE", "ACTION_PREV", "ACTION_NEXT", "ACTION_REWIND", "ACTION_FFWD", "ACTION_LOOP", "ACTION_SHUFFLE", "ACTION_MINIPLAYER", "ACTION_PIP") || 
                             (action == "ACTION_PLAY_ITEM" && (widgetAction == "PLAY_FILE" || widgetAction == "PLAYLIST_ITEM" || widgetAction == null))

        if (isMediaCommand) {
            val pendingResult = goAsync()
            val sessionToken = androidx.media3.session.SessionToken(context.applicationContext, android.content.ComponentName(context.applicationContext, shura.media.service.PlaybackService::class.java))
            val controllerFuture = androidx.media3.session.MediaController.Builder(context.applicationContext, sessionToken).buildAsync()
            
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    if (action == "ACTION_PLAY_PAUSE") {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    } else if (action == "ACTION_PREV") {
                        controller.seekToPreviousMediaItem()
                    } else if (action == "ACTION_NEXT") {
                        controller.seekToNextMediaItem()
                    } else if (action == "ACTION_REWIND") {
                        controller.seekTo((controller.currentPosition - 5000L).coerceAtLeast(0L))
                    } else if (action == "ACTION_FFWD") {
                        val rawDur = controller.duration
                        val targetPos = if (rawDur != androidx.media3.common.C.TIME_UNSET && rawDur > 0L) {
                            (controller.currentPosition + 5000L).coerceAtMost(rawDur)
                        } else {
                            (controller.currentPosition + 5000L).coerceAtLeast(0L)
                        }
                        controller.seekTo(targetPos)
                    } else if (action == "ACTION_LOOP") {
                        val nextMode = when (controller.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                        controller.repeatMode = nextMode
                    } else if (action == "ACTION_SHUFFLE") {
                        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                    } else if (action == "ACTION_MINIPLAYER") {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            shura.media.service.PlaybackService.openOverlay(context, videoMode = false)
                        } else {
                            val permIntent = Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            shura.media.data.MediaUriResolver.safeStartActivity(context, permIntent)
                        }
                    } else if (action == "ACTION_PIP") {
                        if (android.provider.Settings.canDrawOverlays(context)) {
                            shura.media.service.PlaybackService.openOverlay(context, videoMode = true)
                        } else {
                            val permIntent = Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            shura.media.data.MediaUriResolver.safeStartActivity(context, permIntent)
                        }
                    } else if (action == "ACTION_PLAY_ITEM") {
                        if (widgetAction == "PLAY_FILE") {
                            val uriStr = intent.getStringExtra("MEDIA_URI")
                            if (uriStr != null) {
                                val parsedUri = android.net.Uri.parse(uriStr)
                                val cleanTitle = shura.media.data.MediaUriResolver.extractCleanTitleFromUri(parsedUri, "Media")
                                val settings = shura.media.data.SettingsManager.getInstance(context.applicationContext)
                                val isAudio = shura.media.service.PlayerManager.isAudioOnlyHint(context.applicationContext, parsedUri)
                                shura.media.service.PlayerManager.initSessionBackgroundPlayForNewMedia(
                                    isAudio,
                                    settings.defaultAudioBackgroundPlay,
                                    uriStr
                                )
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setUri(uriStr)
                                    .setMediaId(uriStr)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(cleanTitle)
                                            .setDisplayTitle(cleanTitle)
                                            .build()
                                    )
                                    .build()
                                controller.setMediaItem(mediaItem)
                                controller.prepare()
                                controller.play()
                            }
                        } else {
                            val index = intent.getIntExtra("EXTRA_INDEX", -1)
                            if (index >= 0) controller.seekToDefaultPosition(index)
                        }
                    }
                    
                    updateWidgets(context)
                } catch (e: Exception) {
                    shura.media.LogKeeper.logError("MediaWidgetProvider", "Error in MediaController logic", e)
                } finally {
                    try {
                        androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                    } catch (_: Exception) {}
                    pendingResult.finish()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }
}
