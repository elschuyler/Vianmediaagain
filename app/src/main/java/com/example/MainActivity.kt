package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.AppNavigation
import com.example.ui.screens.LoggerScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onUserLeaveHint() {
      super.onUserLeaveHint()
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          val player = com.example.service.PlayerManager.exoPlayer
          if (player != null && player.isPlaying) {
              val vs = player.videoSize
              val rot = vs.unappliedRotationDegrees
              @Suppress("DEPRECATION")
              val width = if (rot % 180 == 0) vs.width else vs.height
              @Suppress("DEPRECATION")
              val height = if (rot % 180 == 0) vs.height else vs.width
              try {
                  enterPictureInPictureMode(com.example.ui.screens.PipHelper.buildPipParams(this, player, width, height))
              } catch (e: Exception) {
                  LogKeeper.logError("MainActivity", "Error entering PiP on user leave hint: ${e.message}", e)
              }
          }
      }
  }

  override fun onDestroy() {
      super.onDestroy()
      try {
          requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      } catch (e: Exception) {}
      try {
          unregisterReceiver(pipReceiver)
      } catch (e: Exception) {}
  }

  override fun finish() {
      try {
          requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      } catch (e: Exception) {}
      super.finish()
  }

  private val pipReceiver = object : android.content.BroadcastReceiver() {
      override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
          if (intent?.action == "com.example.ACTION_ENTER_PIP") {
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  enterPictureInPictureMode(com.example.ui.screens.PipHelper.buildPipParams(this@MainActivity, com.example.service.PlayerManager.exoPlayer))
              }
          }
      }
  }

  private val _currentIntent = kotlinx.coroutines.flow.MutableStateFlow<android.content.Intent?>(null)

  private fun persistUriPermissions(intent: android.content.Intent?) {
      if (intent == null) return
      val uris = mutableListOf<android.net.Uri>()
      try {
          intent.data?.let { uris.add(it) }
      } catch (e: Exception) {}
      try {
          (intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? android.net.Uri)?.let {
              uris.add(it)
          }
      } catch (e: Exception) {}
      try {
          val clipData = intent.clipData
          if (clipData != null && clipData.itemCount > 0) {
              for (i in 0 until clipData.itemCount) {
                  clipData.getItemAt(i)?.uri?.let { uris.add(it) }
              }
          }
      } catch (e: Exception) {}
      try {
          val arrayList = intent.getParcelableArrayListExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM)
          if (arrayList != null) {
              for (parcel in arrayList) {
                  (parcel as? android.net.Uri)?.let { uris.add(it) }
              }
          }
      } catch (e: Exception) {}
      for (uri in uris) {
          if (uri.scheme == "content") {
              try {
                  val flags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                  if (flags != 0) {
                      contentResolver.takePersistableUriPermission(uri, flags)
                  }
              } catch (e: Exception) {}
          }
      }
  }

  private fun handleFloatingOrMiniIntent(intent: android.content.Intent?): Boolean {
      if (intent == null) return false
      val className = intent.component?.className ?: ""
      val isMini = className.contains("MiniMediaActivity")
      val isPip = className.contains("PipMediaActivity")
      if (!isMini && !isPip) return false

      val uriSet = LinkedHashSet<String>()
      try {
          intent.data?.let { uriSet.add(it.toString()) }
      } catch (e: Exception) {}
      try {
          (intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? android.net.Uri)?.let {
              uriSet.add(it.toString())
          }
      } catch (e: Exception) {}
      try {
          val clipData = intent.clipData
          if (clipData != null && clipData.itemCount > 0) {
              for (i in 0 until clipData.itemCount) {
                  clipData.getItemAt(i)?.uri?.let { uriSet.add(it.toString()) }
              }
          }
      } catch (e: Exception) {}
      try {
          val arrayList = intent.getParcelableArrayListExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM)
          if (arrayList != null) {
              for (parcel in arrayList) {
                  (parcel as? android.net.Uri)?.let { uriSet.add(it.toString()) }
              }
          }
      } catch (e: Exception) {}

      val uris = uriSet.toList()
      if (uris.isEmpty()) return false

      com.example.LogKeeper.log("handleFloatingOrMiniIntent: isMini=$isMini, isPip=$isPip, urisCount=${uris.size}", "MainActivity")

      if (isMini) {
          val mediaItems = uris.map { uriStr ->
              val uri = android.net.Uri.parse(uriStr)
              val name = com.example.ui.screens.getDisplayNameFromUri(this, uri)
              androidx.media3.common.MediaItem.Builder()
                  .setUri(uri)
                  .setMediaId(uriStr)
                  .setMediaMetadata(
                      androidx.media3.common.MediaMetadata.Builder()
                          .setTitle(name)
                          .build()
                  )
                  .build()
          }

          com.example.service.PlayerManager.initialize(this, false)
          val player = com.example.service.PlayerManager.exoPlayer
          val hasActivePlayback = player != null && player.mediaItemCount > 0

          if (player != null) {
              if (hasActivePlayback) {
                  player.addMediaItems(mediaItems)
                  android.widget.Toast.makeText(this, "Added ${mediaItems.size} item(s) to queue", android.widget.Toast.LENGTH_SHORT).show()
              } else {
                  player.setMediaItems(mediaItems)
                  player.prepare()
                  player.play()
              }
          }

          // Auto-save playlist snapshot to Room database
          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
              try {
                  val allUris = mutableListOf<String>()
                  if (player != null) {
                      for (i in 0 until player.mediaItemCount) {
                          player.getMediaItemAt(i).mediaId.let { allUris.add(it) }
                      }
                  } else {
                      allUris.addAll(uris)
                  }
                  val db = com.example.data.AppDatabase.getDatabase(applicationContext)
                  val playlistRepo = com.example.data.PlaylistRepository(db.playlistDao())
                  playlistRepo.saveOrUpdateTemporaryPlaylist(allUris)
              } catch (e: Exception) {
                  com.example.LogKeeper.logError("MainActivity", "Error auto-saving temporary playlist", e)
              }
          }

          if (android.provider.Settings.canDrawOverlays(this)) {
              val serviceIntent = android.content.Intent(this, com.example.service.PlaybackService::class.java).apply {
                  putExtra("command", "ACTION_MINIPLAYER")
                  addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  startForegroundService(serviceIntent)
              } else {
                  startService(serviceIntent)
              }

              val overlayIntent = android.content.Intent("com.example.ACTION_WIDGET_COMMAND").apply {
                  putExtra("command", "ACTION_MINIPLAYER")
                  setPackage(packageName)
              }
              sendBroadcast(overlayIntent)

              finish()
              return true
          } else {
              val permIntent = android.content.Intent(
                  android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  android.net.Uri.parse("package:$packageName")
              ).apply {
                  addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              startActivity(permIntent)
              android.widget.Toast.makeText(
                  this,
                  "Please enable 'Display over other apps' to use Mini Player",
                  android.widget.Toast.LENGTH_LONG
              ).show()
              finish()
              return true
          }
      } else if (isPip) {
          val targetUriStr = uris.first()
          val targetUri = android.net.Uri.parse(targetUriStr)
          val displayName = com.example.ui.screens.getDisplayNameFromUri(this, targetUri)

          if (android.provider.Settings.canDrawOverlays(this)) {
              val mediaItem = androidx.media3.common.MediaItem.Builder()
                  .setUri(targetUri)
                  .setMediaId(targetUriStr)
                  .setMediaMetadata(
                      androidx.media3.common.MediaMetadata.Builder()
                          .setTitle(displayName)
                          .build()
                  )
                  .build()

              com.example.service.PlayerManager.initialize(this, false)
              val player = com.example.service.PlayerManager.exoPlayer
              player?.setMediaItem(mediaItem)
              player?.prepare()
              player?.play()

              val serviceIntent = android.content.Intent(this, com.example.service.PlaybackService::class.java).apply {
                  putExtra("command", "ACTION_VIDEO_OVERLAY")
                  addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  startForegroundService(serviceIntent)
              } else {
                  startService(serviceIntent)
              }

              val overlayIntent = android.content.Intent("com.example.ACTION_WIDGET_COMMAND").apply {
                  putExtra("command", "ACTION_VIDEO_OVERLAY")
                  setPackage(packageName)
              }
              sendBroadcast(overlayIntent)

              finish()
              return true
          } else {
              val permIntent = android.content.Intent(
                  android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  android.net.Uri.parse("package:$packageName")
              ).apply {
                  addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              startActivity(permIntent)
              android.widget.Toast.makeText(
                  this,
                  "Please enable 'Display over other apps' to use Floating Player",
                  android.widget.Toast.LENGTH_LONG
              ).show()
              finish()
              return true
          }
      }
      return false
  }

  override fun onNewIntent(intent: android.content.Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      persistUriPermissions(intent)
      if (handleFloatingOrMiniIntent(intent)) {
          return
      }
      _currentIntent.value = intent
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    LogKeeper.init(this)
    try {
        persistUriPermissions(intent)
        if (handleFloatingOrMiniIntent(intent)) {
            return
        }
    } catch (e: Exception) {
        LogKeeper.logError("MainActivity", "Error handling startup intent", e)
    }
    val filter = android.content.IntentFilter("com.example.ACTION_ENTER_PIP")
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }
    } catch (e: Exception) {
        LogKeeper.logError("MainActivity", "Error registering pipReceiver", e)
    }
    
    try {
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .components {
                    add(com.example.VideoThumbnailFetcher.Factory())
                }
                .memoryCache {
                    coil.memory.MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .crossfade(true)
                .build()
        )
    } catch (e: Exception) {
        LogKeeper.logError("MainActivity", "Error initializing Coil ImageLoader", e)
    }

    com.example.data.CacheManager.purgeOrphanedTempFiles(this)

    // Passive on-launch janitor: Clean up temporary playlists older than 24 hours without continuous background services
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val db = com.example.data.AppDatabase.getDatabase(applicationContext)
            val playlistRepo = com.example.data.PlaylistRepository(db.playlistDao())
            playlistRepo.cleanExpiredTemporaryPlaylists(24)
        } catch (e: Exception) {
            LogKeeper.logError("MainActivity", "Error cleaning expired temporary playlists", e)
        }
    }

    enableEdgeToEdge(
        statusBarStyle = androidx.activity.SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        ),
        navigationBarStyle = androidx.activity.SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
        )
    )
    _currentIntent.value = intent
    if (intent?.action == "com.example.ACTION_START_PIP") {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            enterPictureInPictureMode(com.example.ui.screens.PipHelper.buildPipParams(this@MainActivity, com.example.service.PlayerManager.exoPlayer))
        }
    }
    
    setContent {
      val currentIntent by _currentIntent.collectAsState()
      val settings = com.example.data.SettingsManager.getInstance(this)
      val themePref by settings.themePreference.collectAsState()
      val fontPref by settings.fontPreference.collectAsState()
      com.example.service.PlayerManager.initialize(this, false)
      MyApplicationTheme(themePreference = themePref, fontPreference = fontPref) {
        var isLoggerOpen by remember { mutableStateOf(false) }

        if (isLoggerOpen) {
          LoggerScreen(onClose = { isLoggerOpen = false })
        } else {
          Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
              var initialUris: List<String> = emptyList()
              if (currentIntent?.action == "com.example.ACTION_OPEN_PLAYER") {
                  val intentUri = currentIntent?.getStringExtra("uri")
                  val currentMediaId = intentUri
                      ?: com.example.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                      ?: com.example.service.PlayerManager.playbackState.value.playlist.getOrNull(
                          com.example.service.PlayerManager.playbackState.value.currentIndex
                      )?.mediaId
                  if (currentMediaId != null) {
                      initialUris = listOf(currentMediaId)
                  }
              } else if (currentIntent?.action == android.content.Intent.ACTION_VIEW || currentIntent?.action == "edit" || currentIntent?.action == android.content.Intent.ACTION_SEND || currentIntent?.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
                val uriSet = LinkedHashSet<String>()
                try {
                    currentIntent?.data?.let { uri ->
                      uriSet.add(uri.toString())
                    }
                } catch (e: Exception) {}
                try {
                    (currentIntent?.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? android.net.Uri)?.let { uri ->
                      uriSet.add(uri.toString())
                    }
                } catch (e: Exception) {}
                try {
                    val clipData = currentIntent?.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i)?.uri?.let { uri ->
                                uriSet.add(uri.toString())
                            }
                        }
                    }
                } catch (e: Exception) {}
                try {
                    val arrayList = currentIntent?.getParcelableArrayListExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM)
                    if (arrayList != null) {
                        for (parcel in arrayList) {
                            (parcel as? android.net.Uri)?.let { uriSet.add(it.toString()) }
                        }
                    }
                } catch (e: Exception) {}
                initialUris = uriSet.toList()
              }
              
              val forceAction = currentIntent?.component?.className?.let { className ->
                  if (className.contains("PlayMediaActivity")) "play"
                  else if (className.contains("EditMediaActivity")) "edit"
                  else null
              } ?: currentIntent?.action

              AppNavigation(
                  initialUris = initialUris, 
                  forceAction = forceAction,
                  onIntentConsumed = {
                      _currentIntent.value = null
                  }
              )
              
              // Global Diagnostic FAB
              val logEnabled by LogKeeper.isEnabled.collectAsState()
              val settingsManager = remember { com.example.data.SettingsManager.getInstance(applicationContext) }
              val showLoggerFab by settingsManager.showLoggerFab.collectAsState()
              
              if (showLoggerFab) {
                  FloatingActionButton(
                    onClick = { isLoggerOpen = true },
                    modifier = Modifier
                      .align(Alignment.BottomStart)
                      .padding(innerPadding)
                      .padding(16.dp),
                    containerColor = if (logEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                  ) {
                    Icon(Icons.Filled.BugReport, contentDescription = "Open Logger")
                  }
              }
            }
          }
        }
      }
    }
  }
}

