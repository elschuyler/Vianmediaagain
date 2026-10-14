package com.example.ui.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSliderRow(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Slider(
        value = playbackSpeed,
        onValueChange = {
            onSpeedChange(Math.round(it * 10.0f) / 10.0f)
        },
        valueRange = 0.1f..4.0f,
        thumb = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(0xFF2196F3), CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFF2196F3),
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                ),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                modifier = Modifier.height(4.dp)
            )
        },
        modifier = modifier
    )
}

/**
 * Decoupled Speed Controls Bottom Sheet / Dialog supporting fine slider, preset pills, +/- stepping, and skip silence.
 */
@Composable
fun SpeedControlsSheet(
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    skipSilence: Boolean,
    onSkipSilenceChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    CompactPlayerDialog(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select playback speed",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val newSpeed = maxOf(0.1f, playbackSpeed - 0.1f)
                        onPlaybackSpeedChange(Math.round(newSpeed * 10.0f) / 10.0f)
                    },
                    modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }

                Text(
                    text = String.format(java.util.Locale.US, "%.1f", playbackSpeed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        val newSpeed = minOf(4.0f, playbackSpeed + 0.1f)
                        onPlaybackSpeedChange(Math.round(newSpeed * 10.0f) / 10.0f)
                    },
                    modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedSliderRow(
                    playbackSpeed = playbackSpeed,
                    onSpeedChange = onPlaybackSpeedChange,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onPlaybackSpeedChange(1.0f)
                }) {
                    Icon(Icons.Filled.Restore, contentDescription = "Reset")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val predefinedSpeeds = listOf(0.5f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
                predefinedSpeeds.forEach { speed ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onPlaybackSpeedChange(speed)
                            }
                            .border(1.dp, if (playbackSpeed == speed) Color(0xFF2196F3) else MaterialTheme.colorScheme.outline, CircleShape)
                            .background(if (playbackSpeed == speed) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("${speed}x", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Skip silence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(
                    checked = skipSilence,
                    onCheckedChange = onSkipSilenceChange
                )
            }
        }
    }
}
