package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

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


    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        com.example.LogKeeper.log("onUpdate started for ${appWidgetIds.size} widgets", "MediaWidgetProvider")
        try {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
            com.example.LogKeeper.log("Widget updated successfully for ${appWidgetIds.size} widgets", "MediaWidgetProvider")
        } catch (e: Exception) {
            com.example.LogKeeper.logError("MediaWidgetProvider", "Error in onUpdate", e)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        com.example.LogKeeper.log("updateAppWidget started for widgetId $appWidgetId", "MediaWidgetProvider")
        try {
        val views = RemoteViews(context.packageName, R.layout.widget_media)

        // Pending intent to launch main app
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_PLAYER"
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
        
        // Play icon update
        val player = com.example.service.PlayerManager.exoPlayer
        if (player != null) {
            if (player.isPlaying) {
                views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_pause)
            } else {
                views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_play)
            }
            
            // Loop icon
            val loopMode = player.repeatMode
            val primaryColor = android.graphics.Color.parseColor("#3F51B5")
            val defaultColor = android.graphics.Color.parseColor("#19202D")
            
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
            views.setImageViewResource(R.id.widget_btn_play, R.drawable.ic_widget_play)
            views.setInt(R.id.widget_btn_loop, "setColorFilter", android.graphics.Color.parseColor("#19202D"))
            views.setInt(R.id.widget_btn_shuffle, "setColorFilter", android.graphics.Color.parseColor("#19202D"))
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
        
        val clickPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, MediaWidgetProvider::class.java).setAction("ACTION_PLAY_ITEM"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
            com.example.LogKeeper.log("updateAppWidget completed for widgetId $appWidgetId", "MediaWidgetProvider")
        } catch (e: Exception) {
            com.example.LogKeeper.logError("MediaWidgetProvider", "Error in updateAppWidget for widgetId $appWidgetId", e)
        }
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MediaWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        com.example.LogKeeper.log("onReceive action: $action", "MediaWidgetProvider")
        
        if (action == "ACTION_REFRESH" || action == "android.appwidget.action.APPWIDGET_UPDATE" || action == "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS" || action == "android.appwidget.action.APPWIDGET_ENABLED" || action == "android.appwidget.action.APPWIDGET_DISABLED" || action == "android.appwidget.action.APPWIDGET_DELETED") {
            try {
                super.onReceive(context, intent)
                if (action == "ACTION_REFRESH") {
                    updateWidgets(context)
                }
            } catch (e: Exception) {
                com.example.LogKeeper.logError("MediaWidgetProvider", "Error in onReceive standard action", e)
            }
            return
        }
        
        if (action == "ACTION_REWIND") {
            val player = com.example.service.PlayerManager.exoPlayer
            player?.seekTo((player.currentPosition - 5000).coerceAtLeast(0))
            return
        }
        if (action == "ACTION_FFWD") {
            val player = com.example.service.PlayerManager.exoPlayer
            player?.seekTo((player.currentPosition + 5000).coerceAtMost(player.duration ?: 0))
            return
        }
        if (action == "ACTION_STOP") {
            val player = com.example.service.PlayerManager.exoPlayer
            player?.stop()
            player?.clearMediaItems()
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
        val isMediaCommand = action in listOf("ACTION_PLAY_PAUSE", "ACTION_PREV", "ACTION_NEXT", "ACTION_LOOP", "ACTION_SHUFFLE", "ACTION_MINIPLAYER", "ACTION_CLOSE", "ACTION_PIP") || 
                             (action == "ACTION_PLAY_ITEM" && (widgetAction == "PLAY_FILE" || widgetAction == "PLAYLIST_ITEM" || widgetAction == null))

        if (isMediaCommand) {
            val pendingResult = goAsync()
            val sessionToken = androidx.media3.session.SessionToken(context.applicationContext, android.content.ComponentName(context.applicationContext, com.example.service.PlaybackService::class.java))
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
                    } else if (action == "ACTION_LOOP") {
                        val nextMode = when (controller.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                        controller.repeatMode = nextMode
                    } else if (action == "ACTION_SHUFFLE") {
                        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                    } else if (action == "ACTION_CLOSE") {
                        controller.stop()
                        controller.clearMediaItems()
                        // send broadcast to close service
                        val serviceIntent = Intent("com.example.ACTION_WIDGET_COMMAND")
                        serviceIntent.setPackage(context.packageName)
                        serviceIntent.putExtra("command", action)
                        context.sendBroadcast(serviceIntent)
                                        } else if (action == "ACTION_PIP") {
                        val serviceIntent = Intent("com.example.ACTION_WIDGET_COMMAND")
                        serviceIntent.setPackage(context.packageName)
                        serviceIntent.putExtra("command", "ACTION_VIDEO_OVERLAY")
                        context.sendBroadcast(serviceIntent)
                                        } else if (action == "ACTION_PIP") {
                        val serviceIntent = Intent("com.example.ACTION_WIDGET_COMMAND")
                        serviceIntent.setPackage(context.packageName)
                        serviceIntent.putExtra("command", "ACTION_VIDEO_OVERLAY")
                        context.sendBroadcast(serviceIntent)
                    } else if (action == "ACTION_PLAY_ITEM") {
                        if (widgetAction == "PLAY_FILE") {
                            val uriStr = intent.getStringExtra("MEDIA_URI")
                            if (uriStr != null) {
                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                    .setUri(uriStr)
                                    .setMediaId(uriStr)
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(android.net.Uri.parse(uriStr).lastPathSegment ?: "Unknown")
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
                    
                    androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                } catch (e: Exception) {
                    com.example.LogKeeper.logError("MediaWidgetProvider", "Error in MediaController logic", e)
                } finally {
                    pendingResult.finish()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }
}
