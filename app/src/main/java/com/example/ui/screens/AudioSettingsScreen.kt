package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import com.example.data.SettingsManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    
    var centerChannelEnabled by remember { mutableStateOf(settingsManager.centerChannelEnabled) }
    var nightModeEnabled by remember { mutableStateOf(settingsManager.nightModeEnabled) }
    var eqEnabled by remember { mutableStateOf(settingsManager.eqEnabled) }
    var eqLevels by remember { mutableStateOf(settingsManager.getEqLevels().toMutableList().also {
        if (it.isEmpty()) {
            it.addAll(listOf(0, 0, 0, 0, 0)) // 5 bands default
        }
    }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio & Sound Effects") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Voice & Clarity", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Center Channel Extraction", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Isolates the center channel (usually vocals) by removing wide stereo sounds. This works like a reverse-karaoke effect to enhance dialogue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = centerChannelEnabled,
                        onCheckedChange = { 
                            centerChannelEnabled = it
                            settingsManager.centerChannelEnabled = it
                        }
                    )
                }
            }
            
            item {
                Divider()
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Dynamic Range", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Night Mode / Loudness Leveling", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Compresses dynamic range so loud background effects (explosions, music) are reduced, and quiet whispers are boosted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nightModeEnabled,
                        onCheckedChange = { 
                            nightModeEnabled = it
                            settingsManager.nightModeEnabled = it
                        }
                    )
                }
            }
            
            item {
                Divider()
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Switch(
                        checked = eqEnabled,
                        onCheckedChange = { 
                            eqEnabled = it
                            settingsManager.eqEnabled = it
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (eqEnabled) {
                    Text(
                        "Boost mid-frequencies (middle sliders) to enhance vocal clarity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val bandLabels = listOf("Low", "Mid-L", "Mid", "Mid-H", "High")
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0 until minOf(5, eqLevels.size)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(bandLabels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall)
                                
                                Box(
                                    modifier = Modifier
                                        .height(150.dp)
                                        .width(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = eqLevels[i].toFloat(),
                                        onValueChange = { 
                                            val newList = eqLevels.toMutableList()
                                            newList[i] = it.roundToInt()
                                            eqLevels = newList
                                            settingsManager.setEqLevels(newList)
                                        },
                                        valueRange = -1500f..1500f,
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(40.dp)
                                            .graphicsLayer {
                                                rotationZ = -90f
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            },
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = {
                            eqLevels = mutableListOf(0, 0, 0, 0, 0)
                            settingsManager.setEqLevels(eqLevels)
                        }) { Text("Flat") }
                        
                        OutlinedButton(onClick = {
                            eqLevels = mutableListOf(-300, 500, 1000, 500, -300)
                            settingsManager.setEqLevels(eqLevels)
                        }) { Text("Vocal Boost") }
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
