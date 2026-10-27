package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerTopBar(
    title: String,
    playbackSpeed: Float,
    onNavigateBack: () -> Unit,
    onSpeedClick: () -> Unit,
    onSpeedLongClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onEditClick: () -> Unit,
    onPlayerSettingsClick: () -> Unit,
    onInteraction: () -> Unit = {},
    onMenuOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showTopMenu by remember { mutableStateOf(false) }

    LaunchedEffect(showTopMenu) {
        onMenuOpenChange(showTopMenu)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures {
                    onInteraction()
                }
            }
            .padding(horizontal = 4.dp, vertical = 0.dp)
    ) {
        IconButton(onClick = {
            onInteraction()
            onNavigateBack()
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .pointerInput(playbackSpeed) {
                    detectTapGestures(
                        onLongPress = {
                            onInteraction()
                            onSpeedLongClick()
                        },
                        onTap = {
                            onInteraction()
                            onSpeedClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (playbackSpeed == 1.0f) {
                Icon(Icons.Filled.Speed, contentDescription = "Speed: 1x (Long press for controls)", tint = Color.White)
            } else {
                val speedDisplay = if (playbackSpeed == 0.25f) "0.25"
                    else if (playbackSpeed == 0.5f) "0.5"
                    else if (playbackSpeed == 1.2f) "1.2"
                    else if (playbackSpeed == 2.0f) "2"
                    else String.format(java.util.Locale.US, "%.1f", playbackSpeed)
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2196F3).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF2196F3), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${speedDisplay}x",
                        color = Color(0xFF2196F3),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        IconButton(onClick = {
            onInteraction()
            onAudioClick()
        }) {
            Icon(Icons.Filled.MusicNote, contentDescription = "Audio track", tint = Color.White)
        }
        IconButton(onClick = {
            onInteraction()
            onSubtitleClick()
        }) {
            Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles", tint = Color.White)
        }
        Box {
            IconButton(onClick = {
                onInteraction()
                showTopMenu = true
            }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
            }
            DropdownMenu(
                expanded = showTopMenu,
                onDismissRequest = { showTopMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Details") },
                    onClick = {
                        showTopMenu = false
                        onInteraction()
                        onDetailsClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        showTopMenu = false
                        onInteraction()
                        onEditClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Player settings") },
                    onClick = {
                        showTopMenu = false
                        onInteraction()
                        onPlayerSettingsClick()
                    }
                )
            }
        }
    }
}
