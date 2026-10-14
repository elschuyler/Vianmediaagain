package com.example.ui.screens.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Decoupled Audio Tracks dialog supporting volume booster gain slider, track selection, and external audio adding.
 */
@Composable
fun AudioTracksDialog(
    player: Player?,
    audioBoosterEnabled: Boolean,
    boostGainMb: Int,
    onBoostGainChange: (Int) -> Unit,
    onTrackSelected: (groupIndex: Int, trackIndex: Int, totalTrackIndex: Int) -> Unit,
    onAddAudioFile: () -> Unit,
    onDismissRequest: () -> Unit
) {
    CompactPlayerDialog(
        onDismissRequest = onDismissRequest
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = "Select Audio Track",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (audioBoosterEnabled) {
                Text(
                    text = "Volume Booster: " + if (boostGainMb == 0) "Off" else "+${boostGainMb / 100}dB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = boostGainMb.toFloat(),
                    onValueChange = { newVal ->
                        onBoostGainChange(newVal.toInt())
                    },
                    valueRange = 0f..1500f,
                    steps = 14
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Text(
                    text = "Volume Booster is disabled in Player Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val currentTracks = player?.currentTracks
            val audioGroups = currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()

            if (audioGroups.isEmpty()) {
                Text("No audio tracks available", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(audioGroups.size) { groupIndex ->
                        val group = audioGroups[groupIndex]
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val isSelected = group.isSelected
                            val title = format.language ?: format.label ?: "Track ${trackIndex + 1}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        var totalIdx = 0
                                        for (g in 0 until groupIndex) {
                                            totalIdx += audioGroups[g].length
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

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAddAudioFile()
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add More", tint = Color(0xFF2196F3))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add More...", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
