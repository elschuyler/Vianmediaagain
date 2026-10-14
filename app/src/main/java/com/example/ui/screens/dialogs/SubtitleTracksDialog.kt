package com.example.ui.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Decoupled Subtitle Tracks dialog with tabs for Track selection (including auto-detected tracks)
 * and Customization Settings (delay, font size, subtitle color).
 */
@Composable
fun SubtitleTracksDialog(
    player: Player?,
    subtitleDelay: Float,
    onSubtitleDelayChange: (Float) -> Unit,
    subtitleSize: Float,
    onSubtitleSizeChange: (Float) -> Unit,
    subtitleColor: Color,
    onSubtitleColorChange: (Color) -> Unit,
    onTrackSelected: (groupIndex: Int, trackIndex: Int, totalTrackIndex: Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onAddSubtitleFile: () -> Unit,
    onDismissRequest: () -> Unit
) {
    CompactPlayerDialog(
        onDismissRequest = onDismissRequest
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Tracks") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Settings") })
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                val currentTracks = player?.currentTracks
                val textGroups = currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()
                val trackSelectionParameters = player?.trackSelectionParameters
                val isTextDisabled = trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: false

                if (textGroups.isEmpty()) {
                    Text("No subtitles available", style = MaterialTheme.typography.bodyLarge)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDisableSubtitles()
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Off",
                                    fontWeight = if (isTextDisabled) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTextDisabled) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(textGroups.size) { groupIndex ->
                            val group = textGroups[groupIndex]
                            for (trackIndex in 0 until group.length) {
                                val format = group.getTrackFormat(trackIndex)
                                val isSelected = !isTextDisabled && group.isSelected
                                val title = format.language ?: format.label ?: "Track ${trackIndex + 1}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            var totalIdx = 0
                                            for (g in 0 until groupIndex) {
                                                totalIdx += textGroups[g].length
                                            }
                                            totalIdx += trackIndex
                                            onTrackSelected(groupIndex, trackIndex, totalIdx)
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAddSubtitleFile()
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add More", tint = Color(0xFF2196F3))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Subtitle File...", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("Subtitle Delay (ms): ${subtitleDelay.toInt()}", fontWeight = FontWeight.Bold)
                    Slider(
                        value = subtitleDelay,
                        onValueChange = onSubtitleDelayChange,
                        valueRange = -5000f..5000f,
                        steps = 100
                    )
                    Text(
                        text = "Delay is not natively supported by this ExoPlayer version without reloading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Font Size: ${subtitleSize.toInt()}sp", fontWeight = FontWeight.Bold)
                    Slider(
                        value = subtitleSize,
                        onValueChange = onSubtitleSizeChange,
                        valueRange = 8f..48f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Subtitle Color", fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val colors = listOf(Color.White, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta)
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(2.dp, if (subtitleColor == c) Color(0xFF2196F3) else Color.Transparent, CircleShape)
                                    .clickable { onSubtitleColorChange(c) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
