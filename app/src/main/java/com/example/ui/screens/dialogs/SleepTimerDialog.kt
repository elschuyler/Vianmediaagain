package com.example.ui.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Decoupled Sleep Timer dialog with presets and end-of-track countdown.
 */
@Composable
fun SleepTimerDialog(
    duration: Long,
    currentPosition: Long,
    onSetTimerMs: (Long?) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Sleep Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val endOfVideoDuration = if (duration > 0 && duration > currentPosition) {
                    duration - currentPosition
                } else null

                val options = listOf(
                    "Off" to null,
                    "15 minutes" to 15 * 60 * 1000L,
                    "30 minutes" to 30 * 60 * 1000L,
                    "60 minutes" to 60 * 60 * 1000L,
                    "End of video" to endOfVideoDuration
                )
                options.forEach { (label, timerDuration) ->
                    TextButton(
                        onClick = {
                            onSetTimerMs(timerDuration)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = Color(0xFF2196F3))
            }
        }
    )
}
