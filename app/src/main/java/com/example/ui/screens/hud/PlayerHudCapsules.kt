package com.example.ui.screens.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Isolated Volume HUD capsule for smooth 60/120 FPS vertical swipe feedback.
 */
@Composable
fun VolumeHudCapsule(
    gestureVolumeRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(start = 24.dp)
                .height(140.dp)
                .width(38.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(19.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "${(gestureVolumeRatio * 200).roundToInt()}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(4.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight((gestureVolumeRatio * 2f).coerceIn(0f, 1f))
                        .width(4.dp)
                        .background(Color(0xFF2196F3), RoundedCornerShape(2.dp))
                )
                val whiteRatio = ((gestureVolumeRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (whiteRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(whiteRatio)
                            .width(4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Isolated Brightness HUD slider capsule for real-time swipe & on-screen track dragging.
 */
@Composable
fun BrightnessHudCapsule(
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(140.dp)
            .width(38.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(19.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                }
            }
    ) {
        Text(
            text = "${(currentBrightness.coerceIn(0f, 1f) * 100).roundToInt()}%",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .width(38.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            onDragStateChange(true)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onDragStateChange(true)
                            val dragRatio = -dragAmount / 90.dp.toPx()
                            val newVal = (currentBrightness + dragRatio).coerceIn(0.01f, 1f)
                            onBrightnessChange(newVal)
                        },
                        onDragEnd = {
                            onDragStateChange(false)
                        },
                        onDragCancel = {
                            onDragStateChange(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(currentBrightness.coerceIn(0f, 1f))
                        .width(4.dp)
                        .background(Color(0xFF2196F3), RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = Icons.Filled.LightMode,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Isolated Seek HUD badge for swipe scrub time-offset presentation.
 */
@Composable
fun SeekHudCapsule(
    seekOffsetSec: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val sign = if (seekOffsetSec >= 0) "+" else ""
        Text(
            text = "$sign${seekOffsetSec}s",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
        )
    }
}

/**
 * Isolated generic gesture text feedback badge.
 */
@Composable
fun GestureTextHudCapsule(
    gestureText: String,
    modifier: Modifier = Modifier
) {
    if (gestureText.isEmpty()) return
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = gestureText,
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
        )
    }
}

/**
 * Isolated center Play / Pause flash animation indicator.
 */
@Composable
fun PlayPauseFlashHud(
    showFlash: Boolean,
    flashIsPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showFlash,
        enter = fadeIn(animationSpec = tween(100)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Icon(
            imageVector = if (flashIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(72.dp)
        )
    }
}

/**
 * Isolated battery percentage, charging state, and current clock HUD row.
 */
@Composable
fun BatteryTimeHudRow(
    timeStr: String,
    batteryPct: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 0.dp)
    ) {
        Text(
            text = timeStr,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
            contentDescription = if (isCharging) "Charging" else "Battery",
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "$batteryPct%",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
