package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.LogKeeper
import com.example.data.SettingsManager
import kotlinx.coroutines.launch

import com.example.ui.screens.MediaViewModel
import com.example.data.MediaFolder
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    var currentMenu by remember { mutableStateOf("main") }

    androidx.activity.compose.BackHandler(enabled = currentMenu != "main") {
        currentMenu = "main"
    }

    androidx.compose.animation.Crossfade(targetState = currentMenu, label = "SettingsTransition") { screen ->
        when (screen) {
            "main" -> MainSettingsMenu(
                onNavigate = { currentMenu = it },
                onNavigateBack = onNavigateBack
            )
            "general" -> GeneralSettingsPage(onNavigateBack = { currentMenu = "main" })
            "storage" -> StorageSettingsPage(onNavigateBack = { currentMenu = "main" })
            "media" -> MediaConfigPage(onNavigateBack = { currentMenu = "main" })
            "permissions" -> PermissionsManagerScreen(onNavigateBack = { currentMenu = "main" })
            "player" -> PlayerSettingsScreen(onNavigateBack = { currentMenu = "main" })
            "audio" -> AudioSettingsScreen(onNavigateBack = { currentMenu = "main" })
            "notifications" -> NotificationsPage(onNavigateBack = { currentMenu = "main" })
            "data" -> DataManagementPage(onNavigateBack = { currentMenu = "main" })
            "developer" -> DeveloperSettingsPage(onNavigateBack = { currentMenu = "main" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsMenu(onNavigate: (String) -> Unit, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsListItem(
                    icon = Icons.Filled.Palette,
                    title = "General",
                    subtitle = "Theme, typography, and appearance",
                    onClick = { onNavigate("general") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.PlayCircle,
                    title = "Player Settings",
                    subtitle = "Gestures, PiP, resize behavior, background play",
                    onClick = { onNavigate("player") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.GraphicEq,
                    title = "Audio & EQ",
                    subtitle = "Volume steps, boost limits, equalization",
                    onClick = { onNavigate("audio") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.Folder,
                    title = "Storage & Output",
                    subtitle = "Output folders, hidden/excluded directories",
                    onClick = { onNavigate("storage") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.VideoFile,
                    title = "Media Configuration",
                    subtitle = "Supported file extensions and filtering",
                    onClick = { onNavigate("media") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    subtitle = "Custom quick actions in system tray",
                    onClick = { onNavigate("notifications") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.Security,
                    title = "Permissions",
                    subtitle = "System access and directory approvals",
                    onClick = { onNavigate("permissions") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.Backup,
                    title = "Data Management",
                    subtitle = "Import or export application backups",
                    onClick = { onNavigate("data") }
                )
            }
            item {
                SettingsListItem(
                    icon = Icons.Filled.Build,
                    title = "Developer Settings",
                    subtitle = "Background logger, floating action button",
                    onClick = { onNavigate("developer") }
                )
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageSettingsPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val excludedFolders by settingsManager.excludedFolders.collectAsState()
    val outputFolderUri by settingsManager.outputFolderUri.collectAsState()
    var showExcludeDialog by remember { mutableStateOf(false) }
    val viewModel: MediaViewModel = viewModel()
    val mediaFolders by viewModel.mediaFolders.collectAsState()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            settingsManager.setOutputFolderUri(it.toString())
        }
    }

    val scope = rememberCoroutineScope()
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    var isClearing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cacheSizeBytes = com.example.data.CacheManager.getUnusedCacheSizeBytes(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & Output") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Output Folder", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    outputFolderUri ?: "Default (Downloads/Compressed)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { dirPickerLauncher.launch(null) }) {
                    Text("Select")
                }
            }
            if (outputFolderUri != null) {
                TextButton(onClick = { settingsManager.setOutputFolderUri(null) }) {
                    Text("Reset to Default", color = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Excluded Folders (Hidden)", style = MaterialTheme.typography.labelLarge)
            
            if (excludedFolders.isEmpty()) {
                Text("No folders excluded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                ) {
                    items(excludedFolders.toList()) { bucketId ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                "Folder ID: $bucketId",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { settingsManager.removeExcludedFolder(bucketId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Restore Folder", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = { showExcludeDialog = true }) {
                Text("Add Excluded Folder")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(".nomedia Folder Scan Exclusion", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val ignoreNoMedia by settingsManager.ignoreNoMediaSubfolders.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newVal = !ignoreNoMedia
                        settingsManager.setIgnoreNoMediaSubfolders(newVal)
                        viewModel.loadMedia()
                    }
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Ignore .nomedia & Subfolders",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "When a .nomedia file is found in a folder, exclude that folder and all of its subfolders from media library scans",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ignoreNoMedia,
                    onCheckedChange = {
                        settingsManager.setIgnoreNoMediaSubfolders(it)
                        viewModel.loadMedia()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Unused App Data & Cache", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Temporary cache from editor, compressor, trimmer, and converter. Safe to clear anytime. Does not affect your playlists, library, playback history, or files on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    "Cache Size: ${com.example.data.CacheManager.formatBytes(cacheSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (!isClearing) {
                            isClearing = true
                            scope.launch {
                                val freed = com.example.data.CacheManager.clearUnusedCache(context)
                                cacheSizeBytes = com.example.data.CacheManager.getUnusedCacheSizeBytes(context)
                                isClearing = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Cleared ${com.example.data.CacheManager.formatBytes(freed)} of unused data",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !isClearing
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Clear Unused Data")
                }
            }
        }
        
        if (showExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showExcludeDialog = false },
                title = { Text("Select Folder to Exclude") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(mediaFolders) { folder ->
                            TextButton(
                                onClick = { 
                                    settingsManager.addExcludedFolder(folder.id)
                                    showExcludeDialog = false 
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(folder.name, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExcludeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaConfigPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val extensions by settingsManager.extensions.collectAsState()
    var newExtension by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Included Extensions:", style = MaterialTheme.typography.labelLarge)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                extensions.forEach { ext ->
                    InputChip(
                        selected = false,
                        onClick = { },
                        label = { Text(ext) },
                        trailingIcon = {
                            IconButton(
                                onClick = { 
                                    settingsManager.setExtensions(extensions.filter { it != ext }) 
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = "Remove")
                            }
                        }
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = newExtension,
                    onValueChange = { newExtension = it },
                    label = { Text("Add extension (e.g. mkv)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val ext = newExtension.trim().removePrefix(".").lowercase()
                    if (ext.isNotEmpty() && !extensions.contains(ext)) {
                        settingsManager.setExtensions(extensions + ext)
                        newExtension = ""
                    }
                }) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(".nomedia Folder Scan Exclusion", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            val ignoreNoMedia by settingsManager.ignoreNoMediaSubfolders.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newVal = !ignoreNoMedia
                        settingsManager.setIgnoreNoMediaSubfolders(newVal)
                    }
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        "Ignore .nomedia & Subfolders",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Exclude folders containing .nomedia and all of their subdirectories from media library scans",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ignoreNoMedia,
                    onCheckedChange = { settingsManager.setIgnoreNoMediaSubfolders(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    var selectedActions by remember { mutableStateOf(settingsManager.getNotificationPriority().toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Select custom actions to show in the notification (Playback and Close are permanent):", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            val availableActions = listOf("Loop", "Playlist", "PiP")
            availableActions.forEach { action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selectedActions.contains(action)) {
                                selectedActions = selectedActions - action
                            } else {
                                selectedActions = selectedActions + action
                            }
                        }
                        .padding(vertical = 12.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = selectedActions.contains(action),
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(action, style = MaterialTheme.typography.bodyLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val toSave = selectedActions.toMutableList()
                    if (!toSave.contains("Close")) toSave.add("Close")
                    settingsManager.setNotificationPriority(toSave)
                    
                    val intent = android.content.Intent("com.example.ACTION_UPDATE_NOTIFICATION")
                    intent.setPackage(context.packageName)
                    context.sendBroadcast(intent)
                    
                    android.widget.Toast.makeText(context, "Notification actions updated", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Save Configuration")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagementPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val backupStr = com.example.data.BackupManager.createBackup(context)
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(backupStr.toByteArray())
                    }
                    android.widget.Toast.makeText(context, "Backup saved successfully!", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    com.example.LogKeeper.logError("DataManagementPage", "Failed to save backup", e)
                    android.widget.Toast.makeText(context, "Failed to save backup", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val backupStr = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    }
                    if (backupStr != null) {
                        val success = com.example.data.BackupManager.restoreBackup(context, backupStr)
                        if (success) {
                            android.widget.Toast.makeText(context, "Backup restored successfully! Updating UI...", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to restore backup (invalid format)", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    com.example.LogKeeper.logError("DataManagementPage", "Failed to read backup file", e)
                    android.widget.Toast.makeText(context, "Error reading backup file", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Backup and Restore", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Export or import all application settings, excluded folders, preferred extensions, and custom sidebar elements/playlists structure to/from a separate backup file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { createBackupLauncher.launch("vianbr_backup.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Backup (Export)")
                }
                FilledTonalButton(
                    onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore (Import)")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperSettingsPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val showLoggerFab by settingsManager.showLoggerFab.collectAsState()
    val isLoggerEnabled by LogKeeper.isEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Background Logger", style = MaterialTheme.typography.bodyLarge)
                    Text("Record application events and crashes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isLoggerEnabled, onCheckedChange = { LogKeeper.toggleLogger() })
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Logger FAB", style = MaterialTheme.typography.bodyLarge)
                    Text("Display floating action button to access logs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showLoggerFab, onCheckedChange = { settingsManager.setShowLoggerFab(it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralSettingsPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val themePref by settingsManager.themePreference.collectAsState()
    val fontPref by settingsManager.fontPreference.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            val themes = listOf("System Default", "Light", "Dark", "True Black")
            themes.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { settingsManager.setThemePreference(theme) }
                        .padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = themePref == theme,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(theme, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Typography", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            val fonts = listOf("Default", "Serif", "Monospace")
            fonts.forEach { font ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { settingsManager.setFontPreference(font) }
                        .padding(vertical = 8.dp)
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = fontPref == font,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(font, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
