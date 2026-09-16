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
fun AppNavigation(initialUris: List<String> = emptyList(), forceAction: String? = null) {
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
            val isImage = mimeType?.startsWith("image/") == true || ext in listOf("jpg", "jpeg", "png", "heic") || isAnimatedImage
            val isAudio = mimeType?.startsWith("audio/") == true || ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
            val isVideo = mimeType?.startsWith("video/") == true || ext in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "m4v", "m4s", "m3u8", "ts")
            
            val base64Flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            if (forceAction == "mini" || forceAction == "pip" || forceAction == "none") {
                null
            } else if (forceAction == "play" || forceAction == "com.example.ACTION_OPEN_PLAYER") {
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

    val startDest = intentDest ?: defaultStartDest
    


    var batchCompressionUris by remember { mutableStateOf<List<String>?>(null) }
    var batchFFmpegUris by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(initialUris) {
        if (initialUris.size > 1) {
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
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("player/$encodedUri")
                },
                onNavigateToPhotoEditor = { uri ->
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("photo_editor/$encodedUri")
                },
                onNavigateToPlaylists = {
                    navController.navigate("playlists")
                },
                onNavigateToAudioTrimmer = { uri ->
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("audio_trimmer/$encodedUri")
                },
                onNavigateToVideoEditor = { uri ->
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
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
                    val encodedUri = android.util.Base64.encodeToString(uri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    navController.navigate("player/$encodedUri")
                }
            )
        }
        composable(
            route = "player/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            var hasNavigatedBackOnce by remember(uriString) { mutableStateOf(false) }
            PlayerScreen(
                uriString = uriString,
                onNavigateBack = { 
                    if (!hasNavigatedBackOnce) {
                        hasNavigatedBackOnce = true
                        try {
                            (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } catch (e: Exception) {}
                        val popped = navController.popBackStack()
                        com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                        if (!popped || (initialUris.isNotEmpty() && navController.currentDestination?.route == "main")) {
                            com.example.LogKeeper.log("No backstack entry to pop or launched via intent — finishing Activity", "Navigation")
                            (context as? android.app.Activity)?.finish()
                        }
                    } else {
                        com.example.LogKeeper.log("onNavigateBack called again for same session, ignoring", "Navigation")
                    }
                },
                onNavigateToEdit = { editUri ->
                    val encodedUri = android.util.Base64.encodeToString(editUri.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    // If video -> photo_editor (which should ideally be video editor but prompt says "edit(for audio and video(placeholder))"
                    // If audio -> audio_trimmer
                    // Need to find out mimeType or assume we can check in PlayerScreen and emit "audio_trimmer/..." or "photo_editor/..." route to AppNavigation
                    // Actually, let's just make onNavigateToEdit emit the full route!
                    navController.navigate(editUri)
                }
            )
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
                    val popped = navController.popBackStack()
                    com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped || (initialUris.isNotEmpty() && navController.currentDestination?.route == "main")) {
                        com.example.LogKeeper.log("No backstack entry to pop or launched via intent — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
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
                    val popped = navController.popBackStack()
                    com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped || (initialUris.isNotEmpty() && navController.currentDestination?.route == "main")) {
                        com.example.LogKeeper.log("No backstack entry to pop or launched via intent — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
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
            com.example.ui.screens.VideoEditorScreen(
                uriString = decodedUri,
                onNavigateBack = { 
                    val popped = navController.popBackStack()
                    com.example.LogKeeper.log("popBackStack() returned $popped, current backstack size: ${navController.currentBackStack.value.size}", "Navigation")
                    if (!popped || (initialUris.isNotEmpty() && navController.currentDestination?.route == "main")) {
                        com.example.LogKeeper.log("No backstack entry to pop or launched via intent — finishing Activity", "Navigation")
                        (context as? android.app.Activity)?.finish()
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
