package com.example.ui.screens.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import com.example.ui.screens.GestureType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decoupled gesture detector modifier for the video player.
 * Handles:
 * - Multi-touch: Zoom, pan, rotation
 * - Horizontal swipe: Video seek / scrub
 * - Vertical swipe: Volume (or screen brightness if brightness slider is on screen)
 * - Double tap: Play / pause toggle only
 * - Single tap: Overlay controls display and hide (toggle)
 */
@Composable
fun Modifier.playerGestureDetector(
    enabled: Boolean = true,
    density: Float,
    touchSlop: Float,
    coroutineScope: CoroutineScope,
    context: Context,
    showControls: Boolean = false,
    showBrightnessSlider: Boolean,
    boosterEnabled: Boolean,
    currentBoostGainMb: Int,
    startPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    currentBrightness: Float,
    getDeviceBrightness: () -> Float,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekUpdate: (seekOffsetMs: Long, seekSec: Int) -> Unit,
    onSeekCommit: (targetPosMs: Long, resumePlay: Boolean) -> Unit,
    onBrightnessStart: () -> Unit,
    onBrightnessUpdate: (newBrightness: Float) -> Unit,
    onBrightnessEnd: (finalBrightness: Float) -> Unit,
    onVolumeStart: () -> Unit,
    onVolumeUpdate: (ratio: Float, targetSysVolume: Int, targetBoostMb: Int, displayText: String) -> Unit,
    onVolumeEnd: () -> Unit,
    onZoomPanRotate: (zoomChange: Float, panChange: Offset, rotationChange: Float) -> Unit,
    onGestureStateChange: (GestureType) -> Unit
): Modifier {
    if (!enabled) return this

    val curShowControls by rememberUpdatedState(showControls)
    val curShowBrightnessSlider by rememberUpdatedState(showBrightnessSlider)
    val curBoosterEnabled by rememberUpdatedState(boosterEnabled)
    val curBoostGainMb by rememberUpdatedState(currentBoostGainMb)
    val curStartPositionMs by rememberUpdatedState(startPositionMs)
    val curDurationMs by rememberUpdatedState(durationMs)
    val curIsPlaying by rememberUpdatedState(isPlaying)
    val curBrightness by rememberUpdatedState(currentBrightness)
    val curGetDeviceBrightness by rememberUpdatedState(getDeviceBrightness)

    val curOnSingleTap by rememberUpdatedState(onSingleTap)
    val curOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val curOnSeekStart by rememberUpdatedState(onSeekStart)
    val curOnSeekUpdate by rememberUpdatedState(onSeekUpdate)
    val curOnSeekCommit by rememberUpdatedState(onSeekCommit)
    val curOnBrightnessStart by rememberUpdatedState(onBrightnessStart)
    val curOnBrightnessUpdate by rememberUpdatedState(onBrightnessUpdate)
    val curOnBrightnessEnd by rememberUpdatedState(onBrightnessEnd)
    val curOnVolumeStart by rememberUpdatedState(onVolumeStart)
    val curOnVolumeUpdate by rememberUpdatedState(onVolumeUpdate)
    val curOnVolumeEnd by rememberUpdatedState(onVolumeEnd)
    val curOnZoomPanRotate by rememberUpdatedState(onZoomPanRotate)
    val curOnGestureStateChange by rememberUpdatedState(onGestureStateChange)

    return this.pointerInput(enabled) {
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero
        var pendingSingleTapJob: Job? = null

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var currentGesture = GestureType.NONE
            var dragDistanceX = 0f
            var dragDistanceY = 0f
            var lastSeekTime = 0L

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val effectiveBoost = if (curBoosterEnabled) curBoostGainMb else 0
            val startVolumeRatio = if (maxVolume > 0) {
                if (effectiveBoost > 0) {
                    0.5f + (effectiveBoost.toFloat() / 1500f) * 0.5f
                } else {
                    (startVolume.toFloat() / maxVolume.toFloat()) * 0.5f
                }
            } else 0f
            var lastAppliedSystemVolume = startVolume
            var lastAppliedBoost = effectiveBoost

            var startBrightness = curBrightness
            if (startBrightness <= 0f || startBrightness > 1f) {
                startBrightness = curGetDeviceBrightness()
            }
            var lastAppliedBrightness = startBrightness

            var wasPlayingBeforeSeek = false

            do {
                val event = awaitPointerEvent()
                val changes = event.changes

                if (changes.size > 1) {
                    currentGesture = GestureType.ZOOM_PAN
                    curOnGestureStateChange(GestureType.ZOOM_PAN)
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val rotChange = event.calculateRotation()

                    curOnZoomPanRotate(zoomChange, panChange, rotChange)
                    changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (currentGesture != GestureType.ZOOM_PAN) {
                    val change = changes.firstOrNull()
                    if (change != null) {
                        val posChange = change.positionChange()
                        dragDistanceX += posChange.x
                        dragDistanceY += posChange.y

                        if (currentGesture == GestureType.NONE) {
                            if (hypot(dragDistanceX, dragDistanceY) > touchSlop) {
                                if (abs(dragDistanceX) > abs(dragDistanceY)) {
                                    currentGesture = GestureType.SEEK
                                    wasPlayingBeforeSeek = curIsPlaying
                                    curOnSeekStart()
                                } else {
                                    // Vertical swipe: if brightness slider on screen -> brightness, else -> volume
                                    currentGesture = if (curShowBrightnessSlider) {
                                        curOnBrightnessStart()
                                        GestureType.BRIGHTNESS
                                    } else {
                                        curOnVolumeStart()
                                        GestureType.VOLUME
                                    }
                                }
                                curOnGestureStateChange(currentGesture)
                            }
                        }

                        when (currentGesture) {
                            GestureType.SEEK -> {
                                val curDuration = curDurationMs.coerceAtLeast(1L)
                                val seekOffsetMs = (dragDistanceX / size.width) * 120_000
                                val seekSec = ((dragDistanceX / size.width) * 120).toInt()
                                val targetPos = (curStartPositionMs + seekOffsetMs.toLong()).coerceIn(0L, curDuration)
                                val now = System.currentTimeMillis()
                                if (now - lastSeekTime > 300) {
                                    curOnSeekUpdate(seekOffsetMs.toLong(), seekSec)
                                    lastSeekTime = now
                                }
                                change.consume()
                            }
                            GestureType.BRIGHTNESS -> {
                                val brightnessChange = -(dragDistanceY / size.height)
                                val newBrightness = (startBrightness + brightnessChange).coerceIn(0.01f, 1f)
                                if (abs(newBrightness - lastAppliedBrightness) >= 0.004f) {
                                    lastAppliedBrightness = newBrightness
                                    curOnBrightnessUpdate(newBrightness)
                                }
                                change.consume()
                            }
                            GestureType.VOLUME -> {
                                val volumeScale = if (curBoosterEnabled) 1.0f else 0.5f
                                val volumeChange = -(dragDistanceY / size.height) * volumeScale
                                val curRatio = (startVolumeRatio + volumeChange).coerceIn(0f, volumeScale)

                                val targetSystemVolume = if (curRatio <= 0.5f) {
                                    if (maxVolume > 0) ((curRatio / 0.5f) * maxVolume).roundToInt().coerceIn(0, maxVolume) else 0
                                } else {
                                    maxVolume
                                }
                                val targetBoost = if (curBoosterEnabled && curRatio > 0.5f) {
                                    (((curRatio - 0.5f) / 0.5f) * 1500f).roundToInt().coerceIn(0, 1500)
                                } else {
                                    0
                                }

                                lastAppliedSystemVolume = targetSystemVolume
                                lastAppliedBoost = targetBoost
                                val displayText = "Volume: ${(curRatio * 200).roundToInt()}"
                                curOnVolumeUpdate(curRatio, targetSystemVolume, targetBoost, displayText)
                                change.consume()
                            }
                            else -> {}
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (currentGesture == GestureType.SEEK) {
                val curDuration = curDurationMs.coerceAtLeast(1L)
                val seekOffsetMs = (dragDistanceX / size.width) * 120_000
                val targetPos = (curStartPositionMs + seekOffsetMs.toLong()).coerceIn(0L, curDuration)
                curOnSeekCommit(targetPos, wasPlayingBeforeSeek)
            } else if (currentGesture == GestureType.BRIGHTNESS) {
                curOnBrightnessEnd(lastAppliedBrightness)
            } else if (currentGesture == GestureType.VOLUME) {
                curOnVolumeEnd()
            } else if (currentGesture == GestureType.NONE && hypot(dragDistanceX, dragDistanceY) <= touchSlop) {
                val wasConsumed = down.isConsumed || event.changes.any { it.isConsumed }
                if (!wasConsumed) {
                    val tapPos = down.position
                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTapTime
                    val dist = (tapPos - lastTapPosition).getDistance()

                    if (timeDiff in 40L..350L && dist < 100f * density) {
                        // Double tap: cancel single tap and strictly toggle play/pause
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = null
                        lastTapTime = 0L
                        curOnDoubleTap()
                    } else if (!curShowControls) {
                        // Single tap: only opens controls when they are not already visible
                        lastTapTime = now
                        lastTapPosition = tapPos
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = coroutineScope.launch {
                            delay(300L)
                            curOnSingleTap()
                            pendingSingleTapJob = null
                        }
                    } else {
                        // When controls are already visible, do not launch a delayed canvas single-tap.
                        // Background scrim handles synchronous dismissal on empty space.
                        lastTapTime = now
                        lastTapPosition = tapPos
                    }
                }
            }

            curOnGestureStateChange(GestureType.NONE)
        }
    }
}
