package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsManagerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(checkStoragePermission(context)) }
    var notificationGranted by remember { mutableStateOf(checkNotificationPermission(context)) }
    var overlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        storageGranted = checkStoragePermission(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = checkNotificationPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vian Permissions Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Manage the permissions requested by the app for transparency and privacy control.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                PermissionItem(
                    title = "Media Storage Access",
                    icon = Icons.Filled.Folder,
                    isGranted = storageGranted,
                    onToggle = {
                        if (!storageGranted) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                storageLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO))
                            } else {
                                storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                            }
                        } else {
                            openAppSettings(context)
                        }
                    },
                    description = "Allows the app to read media files from your device storage.",
                    features = listOf(
                        "Library Scan: To find audio and video files.",
                        "Media Player: To play selected media files.",
                        "Audio Trimmer: To read audio files for editing.",
                        "Video Editor: To read video files for editing."
                    )
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    PermissionItem(
                        title = "Notifications",
                        icon = Icons.Filled.Notifications,
                        isGranted = notificationGranted,
                        onToggle = {
                            if (!notificationGranted) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppSettings(context)
                            }
                        },
                        description = "Allows the app to show notifications in the status bar.",
                        features = listOf(
                            "Background Playback: Shows media controls when playing in the background.",
                            "Compression Service: Shows progress of media compression.",
                            "FFmpeg Service: Shows progress of media processing tasks."
                        )
                    )
                }
            }

            item {
                PermissionItem(
                    title = "Draw Over Other Apps",
                    icon = Icons.Filled.PictureInPicture,
                    isGranted = overlayGranted,
                    onToggle = {
                        if (!overlayGranted) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    },
                    description = "Allows the app to display content over other applications.",
                    features = listOf(
                        "Mini Player Overlay: Shows a floating mini player that can be dragged around the screen while using other apps."
                    )
                )
            }
        }
    }

    // Refresh overlay permission status when coming back to the screen
    LaunchedEffect(Unit) {
        // A simple polling or just checking once when composed
        while(true) {
            val newOverlay = checkOverlayPermission(context)
            if (newOverlay != overlayGranted) {
                overlayGranted = newOverlay
            }
            val newStorage = checkStoragePermission(context)
            if (newStorage != storageGranted) {
                storageGranted = newStorage
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val newNotif = checkNotificationPermission(context)
                if (newNotif != notificationGranted) {
                    notificationGranted = newNotif
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    icon: ImageVector,
    isGranted: Boolean,
    onToggle: () -> Unit,
    description: String,
    features: List<String>
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isGranted) "Granted" else "Not Granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = isGranted,
                    onCheckedChange = { onToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Expand details"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Features using this permission:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    features.forEach { feature ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("• ", style = MaterialTheme.typography.bodySmall)
                            Text(feature, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Not required before Android 13
    }
}

private fun checkOverlayPermission(context: android.content.Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
