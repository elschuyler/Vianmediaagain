package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.example.ui.screens.PlaybackProgressRow

/**
 * Modularized Bottom Bar surface for PlayerScreen.
 * Encapsulates timeline scrubber row, lock button, transport controls, and right tool buttons.
 */
@Composable
fun PlayerBottomBar(
    mediaController: MediaController?,
    isPlaying: Boolean,
    onLockClick: () -> Unit,
    abRepeatStart: Long?,
    abRepeatEnd: Long?,
    repeatMode: Int,
    onRepeatModeClick: () -> Unit,
    backgroundPlayEnabled: Boolean,
    onBackgroundPlayClick: () -> Unit,
    resizeMode: Int,
    onResizeModeClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onStopClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onFloatingPlayerClick: () -> Unit,
    onInteraction: () -> Unit,
    onHeightMeasured: (Float) -> Unit,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onHeightMeasured(coordinates.size.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    onInteraction()
                }
            }
    ) {
        // Bottom controls gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )

        // Bottom controls content layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility
                        .union(androidx.compose.foundation.layout.WindowInsets.displayCutout)
                        .only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                            androidx.compose.foundation.layout.WindowInsetsSides.Bottom
                        )
                )
                .padding(bottom = 4.dp)
        ) {
            PlaybackProgressRow(
                mediaController = mediaController,
                abRepeatStart = abRepeatStart,
                abRepeatEnd = abRepeatEnd,
                onInteraction = onInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Left alignment: Lock Button
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White)
                }

                // Center alignment: Transport Controls (Prev, Play/Pause, Next, Stop)
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    onPreviousClick = onPreviousClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                    onStopClick = onStopClick,
                    onInteraction = onInteraction,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Right alignment: Right Tools (Loop, BgPlay, Resize, Mini, Float)
                PlayerRightTools(
                    repeatMode = repeatMode,
                    onRepeatModeClick = onRepeatModeClick,
                    backgroundPlayEnabled = backgroundPlayEnabled,
                    onBackgroundPlayClick = onBackgroundPlayClick,
                    resizeMode = resizeMode,
                    onResizeModeClick = onResizeModeClick,
                    onMiniPlayerClick = onMiniPlayerClick,
                    onFloatingPlayerClick = onFloatingPlayerClick,
                    onInteraction = onInteraction,
                    isPortrait = isPortrait,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
