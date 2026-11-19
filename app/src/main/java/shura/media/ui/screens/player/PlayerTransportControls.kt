package shura.media.ui.screens.player

import androidx.compose.foundation.layout.*
import shura.media.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerTransportControls(
    isPlaying: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onStopClick: () -> Unit,
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        modifier = modifier
    ) {
        // Previous (⏮)
        IconButton(
            onClick = {
                onInteraction()
                onPreviousClick()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = AppIcons.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play / Pause (⏯)
        IconButton(
            onClick = {
                onInteraction()
                onPlayPauseClick()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Next (⏭)
        IconButton(
            onClick = {
                onInteraction()
                onNextClick()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = AppIcons.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Stop (⏹)
        IconButton(
            onClick = {
                onInteraction()
                onStopClick()
            },
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = AppIcons.Stop,
                contentDescription = "Stop",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
