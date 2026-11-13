package shura.media.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import shura.media.data.SettingsManager
import shura.media.ui.screens.PhotoEditorScreen
import shura.media.ui.screens.AudioTrimmerScreen
import shura.media.ui.screens.VideoEditorScreen
import shura.media.ui.screens.MainScreen
import shura.media.ui.screens.SettingsScreen
import shura.media.ui.screens.WelcomeScreen
import shura.media.ui.screens.PlayerScreen
import shura.media.ui.screens.PlaylistsScreen
import shura.media.ui.screens.PlaylistDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

@Composable
fun AppNavigation(
    initialUris: List<String> = emptyList(), 
    forceAction: String? = null,
    onIntentConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val navController = rememberNavController()
    
    val defaultStartDest = if (settingsManager.hasSeenWelcome) "main" else "welcome"
    
    val intentDest = remember(initialUris, forceAction) {
        if (initialUris.isNotEmpty()) {
            val uriStr = initialUris.first()
            val mimeType = context.contentResolver.getType(android.net.Uri.parse(uriStr))?.lowercase()
            val ext = uriStr.substringAfterLast('.', "").substringBefore('?').lowercase()
            
            val isImage = mimeType?.startsWith("image/") == true || ext in shura.media.data.COMMON_IMAGE_EXTENSIONS
            val isAudio = mimeType?.startsWith("audio/") == true || ext in shura.media.data.COMMON_AUDIO_EXTENSIONS
            val isVideo = mimeType?.startsWith("video/") == true || ext in shura.media.data.COMMON_VIDEO_EXTENSIONS
            val isAnimated = isImage && shura.media.data.isAnimatedMedia(context, android.net.Uri.parse(uriStr))
            val isEffectiveVideo = isVideo || isAnimated
            
            val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            if (forceAction == "mini" || forceAction == "pip" || forceAction == "none") {
                null
            } else if (forceAction == "play" || forceAction == "shura.media.ACTION_OPEN_PLAYER" || forceAction == android.content.Intent.ACTION_VIEW) {
                val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                "player/$encodedUri"
            } else if (forceAction == "edit") {
                val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                if (isAudio) "audio_trimmer/$encodedUri"
                else if (isEffectiveVideo) "video_editor/$encodedUri"
                else "photo_editor/$encodedUri"
            } else if (isImage && !isAnimated) {
                if (initialUris.size == 1) {
                    val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                    "photo_editor/$encodedUri"
                } else {
                    "main"
                }
            } else {
                val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                "player/$encodedUri"
            }
        } else null
    }

    val isExternalIntent = initialUris.isNotEmpty() && (
        forceAction == "play" ||
        forceAction == "edit" ||
        forceAction == android.content.Intent.ACTION_VIEW ||
        forceAction == android.content.Intent.ACTION_SEND ||
        forceAction == android.content.Intent.ACTION_SEND_MULTIPLE
    )
    var isExternalLaunch by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(isExternalIntent) }

    LaunchedEffect(isExternalIntent) {
        if (isExternalIntent) {
            isExternalLaunch = true
        }
    }

    val initialStartDest = remember { intentDest ?: defaultStartDest }
    val startDest = androidx.compose.runtime.saveable.rememberSaveable { initialStartDest }
    


    var inAppJoinUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val initialJoinUris = remember(initialUris, forceAction) {
        if (forceAction == "edit" && initialUris.size > 1) {
            initialUris.drop(1)
        } else {
            emptyList()
        }
    }

    var batchCompressionUris by remember { mutableStateOf<List<String>?>(null) }
    var batchFFmpegUris by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(initialUris, forceAction) {
        if (initialUris.size > 1 && forceAction != "edit") {
            val isVideoOrAudioOrAnimated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                initialUris.any { rawUri ->
                    val parsed = android.net.Uri.parse(rawUri)
                    val mime = context.contentResolver.getType(parsed)?.lowercase() ?: ""
                    val ext = parsed.lastPathSegment
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        ?: rawUri.substringBefore('?').substringAfterLast('.', "").lowercase()
                    val isVideoOrAudio = mime.startsWith("video/") ||
                        mime.startsWith("audio/") ||
                        ext in shura.media.data.COMMON_VIDEO_EXTENSIONS ||
                        ext in shura.media.data.COMMON_AUDIO_EXTENSIONS
                    val isAnim = (mime == "image/gif" || mime == "image/webp" || ext == "gif" || ext == "webp") &&
                        shura.media.data.isAnimatedMedia(context, parsed)
                    isVideoOrAudio || isAnim
                }
            }
            val isImage = if (!isVideoOrAudioOrAnimated) {
                initialUris.any { rawUri ->
                    val parsed = android.net.Uri.parse(rawUri)
                    val mime = context.contentResolver.getType(parsed)?.lowercase() ?: ""
                    val ext = parsed.lastPathSegment
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        ?: rawUri.substringBefore('?').substringAfterLast('.', "").lowercase()
                    mime.startsWith("image/") || ext in shura.media.data.COMMON_IMAGE_EXTENSIONS
                }
            } else false

            if (isVideoOrAudioOrAnimated) {
                batchFFmpegUris = initialUris
            } else if (isImage) {
                batchCompressionUris = initialUris
            }
        }
    }

    LaunchedEffect(forceAction, initialUris) {
        val isPlayAction = forceAction == "play" || forceAction == "shura.media.ACTION_OPEN_PLAYER" || forceAction == android.content.Intent.ACTION_VIEW
        if (isPlayAction && !startDest.startsWith("player/")) {
            val targetUri = initialUris.firstOrNull()
                ?: shura.media.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                ?: shura.media.service.PlayerManager.playbackState.value.playlist.getOrNull(
                    shura.media.service.PlayerManager.playbackState.value.currentIndex
                )?.mediaId

            if (!targetUri.isNullOrEmpty()) {
                val parsedUri = android.net.Uri.parse(targetUri)
                val intent = android.content.Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                    putExtra("uri", targetUri)
                    data = parsedUri
                    clipData = android.content.ClipData.newRawUri("media", parsedUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    action = forceAction
                }
                context.startActivity(intent)
                onIntentConsumed()
            }
        }
    }
    
    androidx.compose.runtime.DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            shura.media.LogKeeper.log("Navigated to: ${destination.route}", "Navigation")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(navController = navController, startDestination = startDest) {
        composable("welcome") {
            WelcomeScreen(
                onPermissionsGranted = {
                    settingsManager.hasSeenWelcome = true
                    navController.navigate("main") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate("main") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                onNavigateToPlayer = { uri ->
                    val parsedUri = android.net.Uri.parse(uri)
                    val intent = android.content.Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", uri)
                        data = parsedUri
                        clipData = android.content.ClipData.newRawUri("media", parsedUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                },
                onNavigateToPhotoEditor = { uri ->
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("photo_editor/$encodedUri")
                },
                onNavigateToPlaylists = {
                    navController.navigate("playlists")
                },
                onNavigateToPlaylistDetail = { id ->
                    navController.navigate("playlist/$id")
                },
                onNavigateToAudioTrimmer = { uri ->
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("audio_trimmer/$encodedUri")
                },
                onNavigateToVideoEditor = { uri ->
                    inAppJoinUris = emptyList()
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("video_editor/$encodedUri")
                },
                onNavigateToVideoEditorWithJoin = { mainUri, joinUris ->
                    inAppJoinUris = joinUris
                    val encodedUri = android.util.Base64.encodeToString(mainUri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("video_editor/$encodedUri")
                },
                initialSearchActive = (forceAction == "ACTION_SEARCH")
            )
        }
        composable("playlists") {
            PlaylistsScreen(
                onNavigateBack = { 
                    val popped = navController.popBackStack()
                    shura.media.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped) {
                        shura.media.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    }
                },
                onNavigateToPlaylistDetail = { id ->
                    navController.navigate("playlist/$id")
                }
            )
        }
        composable(
            route = "playlist/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            PlaylistDetailScreen(
                playlistId = id,
                onNavigateBack = { 
                    val popped = navController.popBackStack()
                    shura.media.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped) {
                        shura.media.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    }
                },
                onNavigateToPlayer = { uri ->
                    val parsedUri = android.net.Uri.parse(uri)
                    val intent = android.content.Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", uri)
                        data = parsedUri
                        clipData = android.content.ClipData.newRawUri("media", parsedUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
            )
        }
        composable(
            route = "player/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = try {
                String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (e: Exception) { uriString }
            LaunchedEffect(uriString) {
                if (decodedUri.isNotEmpty()) {
                    val parsedUri = android.net.Uri.parse(decodedUri)
                    val intent = android.content.Intent(context, shura.media.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", decodedUri)
                        data = parsedUri
                        clipData = android.content.ClipData.newRawUri("media", parsedUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }
                navController.popBackStack()
            }
        }
        composable(
            route = "photo_editor/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = try {
                String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (e: Exception) { uriString }
            shura.media.ui.screens.PhotoEditorScreen(
                uriString = decodedUri,
                onNavigateBack = { 
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        shura.media.LogKeeper.log("External launch session ended in photo editor — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        shura.media.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            shura.media.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                            (context as? android.app.Activity)?.finish()
                        }
                    }
                }
            )
        }
        composable(
            route = "audio_trimmer/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = try {
                String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (e: Exception) { uriString }
            shura.media.ui.screens.AudioTrimmerScreen(
                uriString = decodedUri,
                onNavigateBack = { 
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        shura.media.LogKeeper.log("External launch session ended in audio trimmer — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        shura.media.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            shura.media.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                            (context as? android.app.Activity)?.finish()
                        }
                    }
                }
            )
        }
        composable(
            route = "video_editor/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = try {
                String(android.util.Base64.decode(uriString, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            } catch (e: Exception) { uriString }
            val effectiveJoinUris = if (inAppJoinUris.isNotEmpty()) inAppJoinUris else initialJoinUris
            shura.media.ui.screens.VideoEditorScreen(
                uriString = decodedUri,
                initialJoinUris = effectiveJoinUris,
                onNavigateBack = { 
                    inAppJoinUris = emptyList()
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        shura.media.LogKeeper.log("External launch session ended in video editor — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        shura.media.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            shura.media.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                            (context as? android.app.Activity)?.finish()
                        }
                    }
                }
            )
        }
    }

    batchCompressionUris?.let { uris ->
        shura.media.ui.components.CompressionOptionsDialog(
            uris = uris,
            onDismiss = { 
                batchCompressionUris = null
                if (initialUris.isNotEmpty()) { (context as? android.app.Activity)?.finish() }
            },
            onStartCompression = { urisToCompress, w, h, q, f ->
                val intent = android.content.Intent(context, shura.media.service.CompressionService::class.java).apply {
                    action = "START_BATCH"
                    putStringArrayListExtra("uris", java.util.ArrayList(urisToCompress))
                    putExtra("maxWidth", w)
                    putExtra("maxHeight", h)
                    putExtra("quality", q)
                    putExtra("format", f)
                    if (urisToCompress.isNotEmpty()) {
                        val firstUri = android.net.Uri.parse(urisToCompress.first())
                        data = firstUri
                        val clip = android.content.ClipData.newRawUri("media", firstUri)
                        for (i in 1 until urisToCompress.size) {
                            clip.addItem(android.content.ClipData.Item(android.net.Uri.parse(urisToCompress[i])))
                        }
                        clipData = clip
                    }
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                batchCompressionUris = null
            }
        )
    }

    batchFFmpegUris?.let { uris ->
        shura.media.ui.components.FFmpegBatchDialog(
            uris = uris,
            onDismiss = {
                batchFFmpegUris = null
                if (initialUris.isNotEmpty()) { (context as? android.app.Activity)?.finish() }
            },
            onStartProcessing = { urisToProcess, commandArgs, outputExt ->
                val intent = android.content.Intent(context, shura.media.service.FFmpegService::class.java).apply {
                    putStringArrayListExtra("uris", java.util.ArrayList(urisToProcess))
                    putStringArrayListExtra("commandArgs", java.util.ArrayList(commandArgs))
                    putExtra("outputExt", outputExt)
                    if (urisToProcess.isNotEmpty()) {
                        val firstUri = android.net.Uri.parse(urisToProcess.first())
                        data = firstUri
                        val clip = android.content.ClipData.newRawUri("media", firstUri)
                        for (i in 1 until urisToProcess.size) {
                            clip.addItem(android.content.ClipData.Item(android.net.Uri.parse(urisToProcess[i])))
                        }
                        clipData = clip
                    }
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                batchFFmpegUris = null
            }
        )
    }

    if (shura.media.service.CompressionStatus.isRunning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { androidx.compose.material3.Text("Compressing Images") },
            text = {
                androidx.compose.foundation.layout.Column {
                    val total = shura.media.service.CompressionStatus.totalFiles
                    val current = shura.media.service.CompressionStatus.currentFile
                    if (total > 1) {
                        val progressRatio = if (total > 0) current.toFloat() / total else 0f
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    if (total > 1) {
                        androidx.compose.material3.Text("$current / $total files processed", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    } else {
                        androidx.compose.material3.Text("Processing file...", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    shura.media.service.CompressionStatus.stop(context)
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    if (shura.media.service.FFmpegStatus.isRunning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { androidx.compose.material3.Text("Processing Video/Audio") },
            text = {
                androidx.compose.foundation.layout.Column {
                    val total = shura.media.service.FFmpegStatus.totalFiles
                    val current = shura.media.service.FFmpegStatus.currentFile
                    val statusText = shura.media.service.FFmpegStatus.currentProgress
                    var parsedProgress: Float? = null
                    if (total > 1) {
                        parsedProgress = if (total > 0) current.toFloat() / total else 0f
                    }
                    if (statusText.startsWith("Saving: ") && statusText.contains("%")) {
                        val pctStr = statusText.substringAfter("Saving: ").substringBefore("%").trim()
                        val pct = pctStr.toFloatOrNull()
                        if (pct != null) {
                            parsedProgress = pct / 100f
                        }
                    }
                    
                    if (parsedProgress != null) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { parsedProgress },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        )
                    } else {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                    if (total > 1) {
                        androidx.compose.material3.Text("$current / $total files processed", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(4.dp))
                    }
                    androidx.compose.material3.Text(statusText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    shura.media.service.FFmpegService.requestCancel()
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
    
    var wasCompressing by remember { mutableStateOf(false) }
    LaunchedEffect(shura.media.service.CompressionStatus.isRunning) {
        if (shura.media.service.CompressionStatus.isRunning) {
            wasCompressing = true
        } else if (wasCompressing) {
            wasCompressing = false
            android.widget.Toast.makeText(context, "Compression complete!", android.widget.Toast.LENGTH_SHORT).show()
            if (initialUris.isNotEmpty()) {
                (context as? android.app.Activity)?.finish()
            }
        }
    }
    
    var wasFFmpegRunning by remember { mutableStateOf(false) }
    LaunchedEffect(shura.media.service.FFmpegStatus.isRunning) {
        if (shura.media.service.FFmpegStatus.isRunning) {
            wasFFmpegRunning = true
        } else if (wasFFmpegRunning) {
            wasFFmpegRunning = false
            android.widget.Toast.makeText(context, "Media processing complete!", android.widget.Toast.LENGTH_SHORT).show()
            // Do not auto-finish here so VideoEditor can show the preview dialog.
            // If it's batch compression, they can dismiss the modal.
        }
    }
}
