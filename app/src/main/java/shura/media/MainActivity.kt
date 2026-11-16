package shura.media

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
import shura.media.ui.navigation.AppNavigation
import shura.media.ui.screens.LoggerScreen
import shura.media.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  override fun onDestroy() {
      super.onDestroy()
      try {
          requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      } catch (e: Exception) {}
  }

  override fun finish() {
      try {
          requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      } catch (e: Exception) {}
      super.finish()
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
              try {
                  grantUriPermission(packageName, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

      shura.media.LogKeeper.log("handleFloatingOrMiniIntent: isMini=$isMini, isPip=$isPip, urisCount=${uris.size}", "MainActivity")

      if (isMini) {
          val mediaItems = uris.map { uriStr ->
              val uri = android.net.Uri.parse(uriStr)
              val fastName = try {
                  val seg = uri.lastPathSegment?.let { android.net.Uri.decode(it) }
                  seg?.substringBeforeLast('.')?.ifBlank { null } ?: "Media"
              } catch (_: Exception) { "Media" }
              androidx.media3.common.MediaItem.Builder()
                  .setUri(uri)
                  .setMediaId(uriStr)
                  .setMediaMetadata(
                      androidx.media3.common.MediaMetadata.Builder()
                          .setTitle(fastName)
                          .setDisplayTitle(fastName)
                          .setArtworkUri(uri)
                          .build()
                  )
                  .build()
          }

          val existingPlayer = shura.media.service.PlayerManager.exoPlayer
          val hasActivePlayback = existingPlayer != null && existingPlayer.mediaItemCount > 0
          val startPos = if (hasActivePlayback) {
              android.widget.Toast.makeText(this, "Added ${mediaItems.size} item(s) to queue", android.widget.Toast.LENGTH_SHORT).show()
              0L
          } else {
              val settings = shura.media.data.SettingsManager.getInstance(applicationContext)
              val firstUriStr = uris.first()
              val firstFastName = mediaItems.first().mediaMetadata.title?.toString() ?: "Media"
              val lastPos = settings.getPlaybackPosition(firstUriStr, firstFastName)
              if (lastPos > 0 && !settings.isFinished(firstUriStr, firstFastName)) lastPos else 0L
          }

          val existingUrisSnapshot = if (existingPlayer != null && hasActivePlayback) {
              val base = mutableListOf<String>()
              for (i in 0 until existingPlayer.mediaItemCount) {
                  base.add(existingPlayer.getMediaItemAt(i).mediaId)
              }
              base + uris
          } else {
              uris
          }

          // Auto-save playlist snapshot to Room database
          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
              try {
                  val db = shura.media.data.AppDatabase.getDatabase(applicationContext)
                  val playlistRepo = shura.media.data.PlaylistRepository(db.playlistDao())
                  playlistRepo.saveOrUpdateTemporaryPlaylist(existingUrisSnapshot)
              } catch (e: Exception) {
                  shura.media.LogKeeper.logError("MainActivity", "Error auto-saving temporary playlist", e)
              }
          }

          if (android.provider.Settings.canDrawOverlays(this)) {
              shura.media.service.PlayerManager.stagePlaybackRequest(
                  mediaItems = mediaItems,
                  startIndex = 0,
                  startPositionMs = startPos,
                  isImplicit = false,
                  appendIfActive = hasActivePlayback
              )
              shura.media.service.PlaybackService.openOverlay(this, videoMode = false)
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
          val displayName = try {
              val seg = targetUri.lastPathSegment?.let { android.net.Uri.decode(it) }
              seg?.substringBeforeLast('.')?.ifBlank { null } ?: "Video"
          } catch (_: Exception) { "Video" }

          if (android.provider.Settings.canDrawOverlays(this)) {
              val mediaItem = androidx.media3.common.MediaItem.Builder()
                  .setUri(targetUri)
                  .setMediaId(targetUriStr)
                  .setMediaMetadata(
                      androidx.media3.common.MediaMetadata.Builder()
                          .setTitle(displayName)
                          .setDisplayTitle(displayName)
                          .setArtworkUri(targetUri)
                          .build()
                  )
                  .build()

              val settings = shura.media.data.SettingsManager.getInstance(applicationContext)
              val lastPos = settings.getPlaybackPosition(targetUriStr, displayName)
              val startPos = if (lastPos > 0 && !settings.isFinished(targetUriStr, displayName)) lastPos else 0L

              shura.media.service.PlayerManager.stagePlaybackRequest(
                  mediaItems = listOf(mediaItem),
                  startIndex = 0,
                  startPositionMs = startPos,
                  isImplicit = true,
                  appendIfActive = false
              )
              shura.media.service.PlaybackService.openOverlay(this, videoMode = true)
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
      val isEditNew = intent.action == android.content.Intent.ACTION_EDIT ||
          intent.action == "edit" ||
          intent.component?.className?.contains("EditMediaActivity") == true
      if (isEditNew) {
          val editorIntent = android.content.Intent(this, shura.media.ui.EditorActivity::class.java).apply {
              action = intent.action
              data = intent.data
              intent.type?.let { type = it }
              intent.extras?.let { putExtras(it) }
              intent.clipData?.let { clipData = it }
              addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
          startActivity(editorIntent)
          return
      }
      if (intent.action == "shura.media.ACTION_OPEN_PLAYER" ||
          intent.component?.className?.contains("PlayMediaActivity") == true ||
          (intent.action == android.content.Intent.ACTION_VIEW && !isEditNew)) {
          shura.media.service.PlaybackService.hideOverlay(this)
          val playerIntent = android.content.Intent(this, shura.media.ui.PlayerActivity::class.java).apply {
              action = intent.action
              data = intent.data
              intent.extras?.let { putExtras(it) }
              intent.clipData?.let { clipData = it }
              addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
          startActivity(playerIntent)
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
        val isEditCreate = intent?.action == android.content.Intent.ACTION_EDIT ||
            intent?.action == "edit" ||
            intent?.component?.className?.contains("EditMediaActivity") == true
        if (isEditCreate) {
            val editorIntent = android.content.Intent(this, shura.media.ui.EditorActivity::class.java).apply {
                action = intent?.action
                data = intent?.data
                intent?.type?.let { type = it }
                intent?.extras?.let { putExtras(it) }
                intent?.clipData?.let { clipData = it }
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(editorIntent)
            finish()
            return
        }
        if (intent?.action == "shura.media.ACTION_OPEN_PLAYER" ||
            intent?.component?.className?.contains("PlayMediaActivity") == true ||
            (intent?.action == android.content.Intent.ACTION_VIEW && !isEditCreate)) {
            shura.media.service.PlaybackService.hideOverlay(this)
            val playerIntent = android.content.Intent(this, shura.media.ui.PlayerActivity::class.java).apply {
                action = intent?.action
                data = intent?.data
                intent?.extras?.let { putExtras(it) }
                intent?.clipData?.let { clipData = it }
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(playerIntent)
            finish()
            return
        }
    } catch (e: Exception) {
        LogKeeper.logError("MainActivity", "Error handling startup intent", e)
    }

    shura.media.data.CacheManager.purgeOrphanedTempFiles(this)

    // Passive on-launch janitor: Clean up temporary playlists older than 24 hours without continuous background services
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val db = shura.media.data.AppDatabase.getDatabase(applicationContext)
            val playlistRepo = shura.media.data.PlaylistRepository(db.playlistDao())
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
    
    setContent {
      val currentIntent by _currentIntent.collectAsState()
      val settings = shura.media.data.SettingsManager.getInstance(this)
      val themePref by settings.themePreference.collectAsState()
      val fontPref by settings.fontPreference.collectAsState()
      MyApplicationTheme(themePreference = themePref, fontPreference = fontPref) {
        var isLoggerOpen by remember { mutableStateOf(false) }

        if (isLoggerOpen) {
          LoggerScreen(onClose = { isLoggerOpen = false })
        } else {
          Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
              var initialUris: List<String> = emptyList()
              if (currentIntent?.action == "shura.media.ACTION_OPEN_PLAYER") {
                  shura.media.service.PlaybackService.hideOverlay(applicationContext)
                  val intentUri = currentIntent?.getStringExtra("uri")
                  val currentMediaId = intentUri
                      ?: shura.media.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                      ?: shura.media.service.PlayerManager.playbackState.value.playlist.getOrNull(
                          shura.media.service.PlayerManager.playbackState.value.currentIndex
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
              } ?: when (currentIntent?.action) {
                  android.content.Intent.ACTION_VIEW -> "play"
                  "edit" -> "edit"
                  else -> currentIntent?.action
              }

              AppNavigation(
                  initialUris = initialUris, 
                  forceAction = forceAction,
                  onIntentConsumed = {
                      _currentIntent.value = null
                  }
              )
              
              // Global Diagnostic FAB
              val logEnabled by LogKeeper.isEnabled.collectAsState()
              val settingsManager = remember { shura.media.data.SettingsManager.getInstance(applicationContext) }
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

