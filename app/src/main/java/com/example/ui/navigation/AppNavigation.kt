package com.example.ui.navigation

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
import com.example.data.SettingsManager
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.AudioTrimmerScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.PlaylistDetailScreen
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
            
            val isAnimatedImage = mimeType == "image/gif" || mimeType == "image/webp" || ext == "gif" || ext == "webp"
            val isImage = mimeType?.startsWith("image/") == true || ext in com.example.data.COMMON_IMAGE_EXTENSIONS || isAnimatedImage
            val isAudio = mimeType?.startsWith("audio/") == true || ext in com.example.data.COMMON_AUDIO_EXTENSIONS
            val isVideo = mimeType?.startsWith("video/") == true || ext in com.example.data.COMMON_VIDEO_EXTENSIONS
            
            val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            if (forceAction == "mini" || forceAction == "pip" || forceAction == "none") {
                null
            } else if (forceAction == "play" || forceAction == "com.example.ACTION_OPEN_PLAYER" || forceAction == android.content.Intent.ACTION_VIEW) {
                val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                "player/$encodedUri"
            } else if (forceAction == "edit") {
                val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                if (isAudio) "audio_trimmer/$encodedUri"
                else if (isVideo) "video_editor/$encodedUri"
                else if (isAnimatedImage) "video_editor/$encodedUri"
                else "photo_editor/$encodedUri"
            } else if (isImage) {
                if (initialUris.size == 1 && !isAnimatedImage) {
                    val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                    "photo_editor/$encodedUri"
                } else if (initialUris.size == 1 && isAnimatedImage) {
                    val encodedUri = android.util.Base64.encodeToString(initialUris.first().toByteArray(), base64Flags)
                    "player/$encodedUri"
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
            val mimeType = context.contentResolver.getType(android.net.Uri.parse(initialUris.first()))
            val isImage = mimeType?.startsWith("image/") == true
            val isVideo = mimeType?.startsWith("video/") == true
            val isAudio = mimeType?.startsWith("audio/") == true
            
            if (isImage) {
                batchCompressionUris = initialUris
            } else if (isVideo || isAudio) {
                batchFFmpegUris = initialUris
            }
        }
    }

    LaunchedEffect(forceAction, initialUris) {
        if (forceAction == "play" || forceAction == "com.example.ACTION_OPEN_PLAYER" || forceAction == android.content.Intent.ACTION_VIEW) {
            val targetUri = initialUris.firstOrNull()
                ?: com.example.service.PlayerManager.exoPlayer?.currentMediaItem?.mediaId
                ?: com.example.service.PlayerManager.playbackState.value.playlist.getOrNull(
                    com.example.service.PlayerManager.playbackState.value.currentIndex
                )?.mediaId

            if (!targetUri.isNullOrEmpty()) {
                val intent = android.content.Intent(context, com.example.ui.PlayerActivity::class.java).apply {
                    putExtra("uri", targetUri)
                    data = android.net.Uri.parse(targetUri)
                    action = forceAction
                }
                context.startActivity(intent)
                onIntentConsumed()
            }
        }
    }
    
    androidx.compose.runtime.DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            com.example.LogKeeper.log("Navigated to: ${destination.route}", "Navigation")
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
                    val intent = android.content.Intent(context, com.example.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", uri)
                        data = android.net.Uri.parse(uri)
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
                    com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped) {
                        com.example.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
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
                    com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped) {
                        com.example.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    }
                },
                onNavigateToPlayer = { uri ->
                    val intent = android.content.Intent(context, com.example.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", uri)
                        data = android.net.Uri.parse(uri)
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
                    val intent = android.content.Intent(context, com.example.ui.PlayerActivity::class.java).apply {
                        putExtra("uri", decodedUri)
                        data = android.net.Uri.parse(decodedUri)
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
            com.example.ui.screens.PhotoEditorScreen(
                uriString = decodedUri,
                onNavigateBack = { 
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        com.example.LogKeeper.log("External launch session ended in photo editor — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            com.example.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
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
            com.example.ui.screens.AudioTrimmerScreen(
                uriString = decodedUri,
                onNavigateBack = { 
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        com.example.LogKeeper.log("External launch session ended in audio trimmer — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            com.example.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
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
            com.example.ui.screens.VideoEditorScreen(
                uriString = decodedUri,
                initialJoinUris = effectiveJoinUris,
                onNavigateBack = { 
                    inAppJoinUris = emptyList()
                    try {
                        (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } catch (e: Exception) {}
                    if (isExternalLaunch) {
                        com.example.LogKeeper.log("External launch session ended in video editor — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
                    } else {
                        val popped = navController.popBackStack()
                        com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped) {
                            com.example.LogKeeper.log("No backstack entry to pop — finishing Activity", "Navigation")
                            (context as? android.app.Activity)?.finish()
                        }
                    }
                }
            )
        }
    }

    batchCompressionUris?.let { uris ->
        com.example.ui.components.CompressionOptionsDialog(
            uris = uris,
            onDismiss = { 
                batchCompressionUris = null
                if (initialUris.isNotEmpty()) { (context as? android.app.Activity)?.finish() }
            },
            onStartCompression = { urisToCompress, w, h, q, f ->
                val intent = android.content.Intent(context, com.example.service.CompressionService::class.java).apply {
                    putStringArrayListExtra("uris", java.util.ArrayList(urisToCompress))
                    putExtra("maxWidth", w)
                    putExtra("maxHeight", h)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                batchCompressionUris = null
            }
        )
    }

    batchFFmpegUris?.let { uris ->
        com.example.ui.components.FFmpegBatchDialog(
            uris = uris,
            onDismiss = {
                batchFFmpegUris = null
                if (initialUris.isNotEmpty()) { (context as? android.app.Activity)?.finish() }
            },
            onStartProcessing = { urisToProcess, commandTemplate, outputExt ->
                val intent = android.content.Intent(context, com.example.service.FFmpegService::class.java).apply {
                    putStringArrayListExtra("uris", java.util.ArrayList(urisToProcess))
                    putExtra("commandTemplate", commandTemplate)
                    putExtra("outputExt", outputExt)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                batchFFmpegUris = null
            }
        )
    }

    if (com.example.service.CompressionStatus.isRunning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { androidx.compose.material3.Text("Compressing Images") },
            text = {
                androidx.compose.foundation.layout.Column {
                    val total = com.example.service.CompressionStatus.totalFiles
                    val current = com.example.service.CompressionStatus.currentFile
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
                    com.example.service.CompressionStatus.stop(context)
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    if (com.example.service.FFmpegStatus.isRunning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { androidx.compose.material3.Text("Processing Video/Audio") },
            text = {
                androidx.compose.foundation.layout.Column {
                    val total = com.example.service.FFmpegStatus.totalFiles
                    val current = com.example.service.FFmpegStatus.currentFile
                    val statusText = com.example.service.FFmpegStatus.currentProgress
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
                    val intent = android.content.Intent(context, com.example.service.FFmpegService::class.java).apply {
                        action = "STOP"
                    }
                    context.startService(intent)
                }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
    
    var wasCompressing by remember { mutableStateOf(false) }
    LaunchedEffect(com.example.service.CompressionStatus.isRunning) {
        if (com.example.service.CompressionStatus.isRunning) {
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
    LaunchedEffect(com.example.service.FFmpegStatus.isRunning) {
        if (com.example.service.FFmpegStatus.isRunning) {
            wasFFmpegRunning = true
        } else if (wasFFmpegRunning) {
            wasFFmpegRunning = false
            android.widget.Toast.makeText(context, "Media processing complete!", android.widget.Toast.LENGTH_SHORT).show()
            // Do not auto-finish here so VideoEditor can show the preview dialog.
            // If it's batch compression, they can dismiss the modal.
        }
    }
}
