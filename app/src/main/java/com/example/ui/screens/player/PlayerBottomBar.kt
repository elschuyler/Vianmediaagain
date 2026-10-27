package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.media3.common.Player
import com.example.ui.screens.PlaybackProgressRow

/**
 * Modularized Bottom Bar surface for PlayerScreen.
 * Encapsulates timeline scrubber row, lock button, transport controls, and right tool buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerBottomBar(
    mediaController: Player?,
    isPlaying: Boolean,
    isLocked: Boolean = false,
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
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onInteraction()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    onInteraction()
                }
            }
    ) {
        // Bottom controls gradient background (only when unlocked)
        if (!isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            )
        }

        // Bottom controls content layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal +
                        WindowInsetsSides.Bottom
                    )
                )
                .padding(bottom = 4.dp)
        ) {
            if (!isLocked) {
                PlaybackProgressRow(
                    mediaController = mediaController,
                    fallbackPosition = mediaController?.currentPosition?.coerceAtLeast(0L),
                    fallbackDuration = mediaController?.duration?.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET },
                    abRepeatStart = abRepeatStart,
                    abRepeatEnd = abRepeatEnd,
                    onInteraction = onInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                // Left alignment: Lock Button (always anchored in exact same position)
                IconButton(
                    onClick = {
                        onInteraction()
                        onLockClick()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .then(
                            if (isLocked) {
                                Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            } else Modifier
                        )
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = Color.White
                    )
                }

                if (!isLocked) {
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
}
