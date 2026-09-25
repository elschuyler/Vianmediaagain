package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaybackProgressRow(
    mediaController: androidx.media3.common.Player?,
    abRepeatStart: Long? = null,
    abRepeatEnd: Long? = null,
    fallbackDuration: Long? = null,
    fallbackPosition: Long? = null,
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentPosition by remember { mutableLongStateOf(fallbackPosition?.takeIf { it > 0L } ?: 0L) }
    var duration by remember { mutableLongStateOf(fallbackDuration?.takeIf { it > 0L } ?: 0L) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf(0f) }

    LaunchedEffect(fallbackDuration, fallbackPosition) {
        if (duration <= 0L && fallbackDuration != null && fallbackDuration > 0L) {
            duration = fallbackDuration
        }
        if (currentPosition <= 0L && fallbackPosition != null && fallbackPosition > 0L) {
            currentPosition = fallbackPosition
        }
    }

    val playerSnapshot by com.example.service.PlayerManager.playbackState.collectAsState()

    LaunchedEffect(playerSnapshot, isScrubbing) {
        if (!isScrubbing) {
            if (playerSnapshot.duration > 0L) {
                duration = playerSnapshot.duration
            } else if (mediaController != null && mediaController.duration > 0L && mediaController.duration != androidx.media3.common.C.TIME_UNSET) {
                duration = mediaController.duration
            }
            if (playerSnapshot.currentPosition >= 0L) {
                currentPosition = playerSnapshot.currentPosition
            } else if (mediaController != null && mediaController.currentPosition >= 0L) {
                currentPosition = mediaController.currentPosition
            }
        }
    }

    LaunchedEffect(mediaController) {
        while (true) {
            if (mediaController != null && !isScrubbing) {
                val dur = mediaController.duration
                if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                    duration = dur
                }
                val pos = mediaController.currentPosition
                if (pos > 0L) {
                    currentPosition = pos
                } else if (pos == 0L && duration > 0L && currentPosition == 0L) {
                    currentPosition = 0L
                }
            }
            delay(500)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = formatTime(if (isScrubbing) (scrubPosition * duration).toLong() else currentPosition),
            color = Color.White,
            fontSize = 12.sp
        )
        var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

        Slider(
            value = if (isScrubbing) scrubPosition else (if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f),
            onValueChange = { scale ->
                onInteraction()
                if (!isScrubbing) {
                    wasPlayingBeforeScrub = mediaController?.isPlaying == true
                    mediaController?.pause()
                }
                isScrubbing = true
                scrubPosition = scale
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSeekTime > 300) {
                    mediaController?.seekTo((scale * duration).toLong())
                    lastSeekTime = currentTime
                }
            },
            onValueChangeFinished = {
                onInteraction()
                mediaController?.seekTo((scrubPosition * duration).toLong())
                isScrubbing = false
                if (wasPlayingBeforeScrub) {
                    mediaController?.play()
                }
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFF2196F3), CircleShape)
                )
            },
            track = { sliderState ->
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF2196F3),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        modifier = Modifier.height(4.dp)
                    )
                    
                    if (duration > 0) {
                        if (abRepeatStart != null) {
                            val fraction = (abRepeatStart.toFloat() / duration).coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                Box(modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)) {
                                    Box(modifier = Modifier.align(Alignment.CenterEnd).size(8.dp).background(Color(0xFFFF9800), CircleShape))
                                }
                            } else {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                            }
                        }
                        if (abRepeatEnd != null) {
                            val fraction = (abRepeatEnd.toFloat() / duration).coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                Box(modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)) {
                                    Box(modifier = Modifier.align(Alignment.CenterEnd).size(8.dp).background(Color(0xFFFF9800), CircleShape))
                                }
                            } else {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF9800), CircleShape))
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Text(
            text = if (showRemainingTime && duration > 0) "-" + formatTime(duration - currentPosition) else formatTime(duration),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.clickable { 
                showRemainingTime = !showRemainingTime
                onInteraction()
            }
        )
    }
}
