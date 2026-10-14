package com.example.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout

@Composable
fun PlayerRightTools(
    repeatMode: Int,
    onRepeatModeClick: () -> Unit,
    backgroundPlayEnabled: Boolean,
    onBackgroundPlayClick: () -> Unit,
    resizeMode: Int,
    onResizeModeClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onFloatingPlayerClick: () -> Unit,
    onInteraction: () -> Unit = {},
    isPortrait: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showToolsStack by remember { mutableStateOf(false) }

    val ToolsContent: @Composable () -> Unit = {
        // Repeat mode toggle
        IconButton(modifier = Modifier.size(36.dp), onClick = {
            onInteraction()
            onRepeatModeClick()
        }) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repeat",
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) Color(0xFF2196F3) else Color.White
            )
        }

        // Background play toggle
        IconButton(modifier = Modifier.size(36.dp), onClick = {
            onInteraction()
            onBackgroundPlayClick()
        }) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Filled.Headphones,
                contentDescription = "Background play",
                tint = if (backgroundPlayEnabled) Color(0xFF2196F3) else Color.White
            )
        }

        // Resize / Aspect ratio jump
        IconButton(modifier = Modifier.size(36.dp), onClick = {
            onInteraction()
            onResizeModeClick()
        }) {
            val resizeIcon = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Filled.AspectRatio
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.Fullscreen
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Filled.Crop
                else -> Icons.Filled.FullscreenExit
            }
            Icon(resizeIcon, modifier = Modifier.size(20.dp), contentDescription = "Aspect Ratio", tint = Color.White)
        }

        // Mini player
        IconButton(modifier = Modifier.size(36.dp), onClick = {
            onInteraction()
            onMiniPlayerClick()
        }) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = "Mini Player",
                tint = Color.White
            )
        }

        // Floating player
        IconButton(modifier = Modifier.size(36.dp), onClick = {
            onInteraction()
            onFloatingPlayerClick()
        }) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = com.example.R.drawable.ic_pip),
                contentDescription = "Floating Player",
                tint = Color.White
            )
        }
    }

    Box(modifier = modifier) {
        if (isPortrait) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(0, 0) {
                            placeable.place(-placeable.width / 2, -placeable.height - 8.dp.roundToPx())
                        }
                    }
                ) {
                    AnimatedVisibility(
                        visible = showToolsStack,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                    ) {
                        Column(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ToolsContent()
                        }
                    }
                }
                IconButton(onClick = {
                    onInteraction()
                    showToolsStack = !showToolsStack
                }) {
                    Icon(
                        if (showToolsStack) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = "More tools",
                        tint = Color.White
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolsContent()
            }
        }
    }
}
