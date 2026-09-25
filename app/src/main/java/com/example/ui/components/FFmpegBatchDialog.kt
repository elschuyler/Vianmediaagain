package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegBatchDialog(
    uris: List<String>,
    onDismiss: () -> Unit,
    onStartProcessing: (List<String>, String, String) -> Unit
) {
    var format by remember { mutableStateOf("mp4") }
    var resolutionIndex by remember { mutableIntStateOf(4) }
    var fpsIndex by remember { mutableIntStateOf(1) }
    var quality by remember { mutableFloatStateOf(0.5f) }
    var fastExport by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export & Quality Control (${uris.size} files)") },
        text = {
            Column {
                Text("Resolution")
                Slider(
                    value = resolutionIndex.toFloat(),
                    onValueChange = { resolutionIndex = it.toInt() },
                    valueRange = 0f..6f,
                    steps = 5
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Og", style = if (resolutionIndex == 0) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("144p", style = if (resolutionIndex == 1) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("240p", style = if (resolutionIndex == 2) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("360p", style = if (resolutionIndex == 3) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("480p", style = if (resolutionIndex == 4) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("720p", style = if (resolutionIndex == 5) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("1080p", style = if (resolutionIndex == 6) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Frame Rate")
                Slider(
                    value = fpsIndex.toFloat(),
                    onValueChange = { fpsIndex = it.toInt() },
                    valueRange = 0f..2f,
                    steps = 1
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("24 fps", style = if (fpsIndex == 0) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("30 fps", style = if (fpsIndex == 1) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                    Text("60 fps", style = if (fpsIndex == 2) MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Quality")
                Slider(value = quality, onValueChange = { quality = it })
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fastExport, onCheckedChange = { fastExport = it })
                    Text("Fast Export (ultrafast preset)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Converter Format")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = format == "mp4", onClick = { format = "mp4" }, label= { Text("mp4")})
                    FilterChip(selected = format == "mp3", onClick = { format = "mp3" }, label= { Text("mp3")})
                    FilterChip(selected = format == "gif", onClick = { format = "gif" }, label= { Text("gif")})
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val res = when (resolutionIndex) {
                    0 -> "Original"
                    1 -> "256x144"
                    2 -> "426x240"
                    3 -> "640x360"
                    4 -> "854x480"
                    5 -> "1280x720"
                    else -> "1920x1080"
                }
                val fps = when (fpsIndex) {
                    0 -> "24"
                    1 -> "30"
                    else -> "60"
                }
                val crf = (35 - (quality * 17)).toInt()
                
                val presetArg = if (fastExport) "ultrafast" else "medium"
                val resArg = if (res == "Original" || format != "mp4") "" else {
                    val parts = res.split("x")
                    val targetW = parts[0].toInt()
                    val targetH = parts[1].toInt()
                    "-vf \"scale=w='if(gte(iw,ih),$targetW,$targetH)':h='if(gte(iw,ih),$targetH,$targetW)':force_original_aspect_ratio=decrease,pad='if(gte(iw,ih),$targetW,$targetH)':'if(gte(iw,ih),$targetH,$targetW)':(ow-iw)/2:(oh-ih)/2\""
                }
                
                val cmd = when(format) {
                    "mp4" -> "-y -i %INPUT% $resArg -r $fps -vcodec libx264 -crf $crf -preset $presetArg -metadata:s:v:0 rotate=0 %OUTPUT%"
                    "mp3" -> "-y -i %INPUT% -vn -acodec libmp3lame -q:a 2 %OUTPUT%"
                    "gif" -> "-y -i %INPUT% -vf \"fps=15,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\" -loop 0 %OUTPUT%"
                    else -> "-y -i %INPUT% %OUTPUT%"
                }
                onStartProcessing(uris, cmd, format)
            }) {
                Text("SAVE (Render)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
