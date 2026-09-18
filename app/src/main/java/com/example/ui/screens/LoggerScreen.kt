package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.LogEntry
import com.example.LogKeeper
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val isEnabled by LogKeeper.isEnabled.collectAsStateWithLifecycle()
    val logSizeBytes by LogKeeper.logSizeBytes.collectAsStateWithLifecycle()
    val recentLogs by LogKeeper.logs.collectAsStateWithLifecycle()

    var allLogs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilterHours by remember { mutableLongStateOf(0L) } // 0 means 'All'
    var onlyErrors by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    fun refreshLogs() {
        coroutineScope.launch {
            isLoading = true
            allLogs = LogKeeper.loadAllLogs()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    // Merge live logs emitted while the screen is open
    LaunchedEffect(recentLogs) {
        if (!isLoading && recentLogs.isNotEmpty()) {
            val existingSet = allLogs.map { it.timestampMs to it.message }.toSet()
            val newItems = recentLogs.filter { (it.timestampMs to it.message) !in existingSet }
            if (newItems.isNotEmpty()) {
                allLogs = allLogs + newItems
            }
        }
    }

    val currentMillis = System.currentTimeMillis()
    val filteredLogs = remember(allLogs, selectedFilterHours, onlyErrors, searchQuery) {
        val timeThreshold = if (selectedFilterHours > 0L) {
            currentMillis - (selectedFilterHours * 60 * 60 * 1000)
        } else 0L

        allLogs.asSequence()
            .filter { entry ->
                (timeThreshold == 0L || entry.timestampMs >= timeThreshold) &&
                (!onlyErrors || entry.isError) &&
                (searchQuery.isBlank() ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true))
            }
            .toList()
            .reversed()
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Active Logs") },
            text = { Text("Are you sure you want to reset the current log buffer? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmDialog = false
                    LogKeeper.clearLogs()
                    allLogs = emptyList()
                    Toast.makeText(context, "Log buffer cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Log Keeper", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (isEnabled) "Active Catcher" else "Catcher Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { LogKeeper.toggleLogger() },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { refreshLogs() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Logs")
                    }
                    IconButton(onClick = {
                        val allFormatted = filteredLogs.joinToString("\n") { it.formattedString }
                        clipboardManager.setText(AnnotatedString(allFormatted))
                        Toast.makeText(context, "Copied ${filteredLogs.size} logs to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy to Clipboard")
                    }
                    IconButton(onClick = {
                        LogKeeper.dumpCurrentLogs(context) { success, fileName ->
                            if (success) {
                                Toast.makeText(context, "Dumped to Downloads: $fileName", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Dump failed: $fileName", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Download Logs")
                    }
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Log Size & Auto-Dump Threshold Progress
            val maxSizeBytes = 2L * 1024 * 1024
            val progress = (logSizeBytes.toFloat() / maxSizeBytes).coerceIn(0f, 1f)
            val isNearThreshold = progress > 0.85f

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage: ${LogKeeper.currentLogSizeFormatted} / 2.00 MB",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNearThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Auto-dumps at 2 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = if (isNearThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tags, messages, stacktraces...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // Filters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = onlyErrors,
                    onClick = { onlyErrors = !onlyErrors },
                    label = { Text("Errors Only") },
                    modifier = Modifier.padding(end = 8.dp)
                )

                ScrollableTabRow(
                    selectedTabIndex = getTabIndex(selectedFilterHours),
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    FilterTab("1h", selectedFilterHours == 1L) { selectedFilterHours = 1L }
                    FilterTab("6h", selectedFilterHours == 6L) { selectedFilterHours = 6L }
                    FilterTab("12h", selectedFilterHours == 12L) { selectedFilterHours = 12L }
                    FilterTab("24h", selectedFilterHours == 24L) { selectedFilterHours = 24L }
                    FilterTab("All", selectedFilterHours == 0L) { selectedFilterHours = 0L }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty() || onlyErrors || selectedFilterHours > 0) "No logs match current filters" else "No logs recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredLogs) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (entry.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.formattedTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.tag,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (entry.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (entry.stackTrace != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.stackTrace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 10,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getTabIndex(hours: Long): Int {
    return when (hours) {
        1L -> 0
        6L -> 1
        12L -> 2
        24L -> 3
        else -> 4
    }
}

@Composable
private fun FilterTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(text) }
    )
}

