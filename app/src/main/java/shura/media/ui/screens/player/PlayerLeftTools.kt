package shura.media.ui.screens.player

import androidx.compose.foundation.layout.*
import shura.media.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerLeftTools(
    abRepeatStart: Long? = null,
    abRepeatEnd: Long? = null,
    onAbRepeatClick: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    onSleepTimerClick: () -> Unit = {},
    isBrightnessSliderVisible: Boolean = false,
    onBrightnessClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        // A-B Repeat Icon
        IconButton(onClick = {
            onInteraction()
            onAbRepeatClick()
        }) {
            val tint = if (abRepeatStart != null) Color(0xFF2196F3) else Color.White
            val text = if (abRepeatStart != null && abRepeatEnd == null) "A" else if (abRepeatEnd != null) "A-B" else ""
            Box(contentAlignment = Alignment.Center) {
                Icon(AppIcons.Loop, contentDescription = "A-B Repeat", tint = tint)
                if (text.isNotEmpty()) {
                    Text(
                        text = text,
                        color = tint,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }

        // Sleep Timer Icon
        IconButton(onClick = {
            onInteraction()
            onSleepTimerClick()
        }) {
            Icon(
                AppIcons.Timer,
                contentDescription = "Sleep Timer",
                tint = if (isSleepTimerActive) Color(0xFF2196F3) else Color.White
            )
        }

        // Brightness Toggle Icon
        IconButton(onClick = {
            onInteraction()
            onBrightnessClick()
        }) {
            Icon(
                AppIcons.LightMode,
                contentDescription = "Brightness",
                tint = if (isBrightnessSliderVisible) Color(0xFF2196F3) else Color.White
            )
        }

        // Rotation Toggle Icon
        IconButton(onClick = {
            onInteraction()
            onRotateClick()
        }) {
            Icon(AppIcons.ScreenRotation, contentDescription = "Rotation", tint = Color.White)
        }

        // Screenshot Icon
        IconButton(onClick = {
            onInteraction()
            onScreenshotClick()
        }) {
            Icon(AppIcons.Screenshot, contentDescription = "Screenshot", tint = Color.White)
        }
    }
}
