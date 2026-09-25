package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.service.CompressionStatus
import com.example.service.FFmpegStatus
import com.example.ui.components.FFmpegBatchDialog
import com.example.ui.components.CompressionOptionsDialog
import com.example.ui.theme.MyApplicationTheme

class BatchActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var initialUris: List<String> = emptyList()
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val arrayList = intent?.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
            if (arrayList != null) {
                val uris = mutableListOf<String>()
                for (parcel in arrayList) {
                    (parcel as? Uri)?.let { uris.add(it.toString()) }
                }
                initialUris = uris
            }
        } else if (intent?.action == Intent.ACTION_SEND) {
            (intent?.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                initialUris = listOf(uri.toString())
            }
        }

        if (initialUris.isEmpty()) {
            finish()
            return
        }

        val mimeType = intent?.type ?: ""

        setContent {
            MyApplicationTheme {
                var startedProcessing by remember { mutableStateOf(false) }
                var isAnimatedImageState by remember { mutableStateOf<Boolean?>(null) }
                
                LaunchedEffect(initialUris) {
                    val isAnim = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        initialUris.any { uriStr ->
                            val uri = Uri.parse(uriStr)
                            val mime = contentResolver.getType(uri) ?: ""
                            if (mime == "image/gif" || mime == "image/webp") {
                                var animated = false
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    try {
                                        val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                                        val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                                        animated = drawable is android.graphics.drawable.AnimatedImageDrawable
                                    } catch(e: Exception) {}
                                }
                                animated
                            } else {
                                false
                            }
                        }
                    }
                    isAnimatedImageState = isAnim
                }

                val isVideoOrAudio = intent?.getBooleanExtra("force_ffmpeg", false) == true || mimeType.startsWith("video/") || mimeType.startsWith("audio/") || initialUris.any { 
                    it.lowercase().endsWith(".mp4") 
                } || isAnimatedImageState == true
                
                LaunchedEffect(CompressionStatus.isRunning, FFmpegStatus.isRunning) {
                    if (startedProcessing && !CompressionStatus.isRunning && !FFmpegStatus.isRunning) {
                        finish()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (startedProcessing) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("Processing...") },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (CompressionStatus.isRunning) {
                                        Text("${CompressionStatus.currentFile} / ${CompressionStatus.totalFiles} Completed")
                                    } else if (FFmpegStatus.isRunning) {
                                        Text("${FFmpegStatus.currentFile} / ${FFmpegStatus.totalFiles} Completed")
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(FFmpegStatus.currentProgress)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val stopIntent1 = Intent(this@BatchActionActivity, com.example.service.CompressionService::class.java).apply {
                                        action = "STOP"
                                    }
                                    try { startService(stopIntent1) } catch (e: Exception) {}
                                    val stopIntent2 = Intent(this@BatchActionActivity, com.example.service.FFmpegService::class.java).apply {
                                        action = "STOP"
                                    }
                                    try { startService(stopIntent2) } catch (e: Exception) {}
                                    CompressionStatus.isRunning = false
                                    FFmpegStatus.isRunning = false
                                    finish()
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    } else if (isAnimatedImageState == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        if (isVideoOrAudio) {
                            FFmpegBatchDialog(
                                uris = initialUris,
                                onDismiss = { finish() },
                                onStartProcessing = { uris, cmdTemplate, format ->
                                    val serviceIntent = Intent(this@BatchActionActivity, com.example.service.FFmpegService::class.java).apply {
                                        action = "START_BATCH"
                                        putStringArrayListExtra("uris", ArrayList(uris))
                                        putExtra("outputExt", format)
                                        putExtra("commandTemplate", cmdTemplate)
                                    }
                                    androidx.core.content.ContextCompat.startForegroundService(this@BatchActionActivity, serviceIntent)
                                    startedProcessing = true
                                }
                            )
                        } else {
                            CompressionOptionsDialog(
                                uris = initialUris,
                                onDismiss = { finish() },
                                onStartCompression = { uris, maxWidth, maxHeight, quality, format ->
                                    val serviceIntent = Intent(this@BatchActionActivity, com.example.service.CompressionService::class.java).apply {
                                        action = "START_BATCH"
                                        putStringArrayListExtra("uris", ArrayList(uris))
                                        putExtra("maxWidth", maxWidth)
                                        putExtra("maxHeight", maxHeight)
                                        putExtra("quality", quality)
                                        putExtra("format", format)
                                    }
                                    androidx.core.content.ContextCompat.startForegroundService(this@BatchActionActivity, serviceIntent)
                                    startedProcessing = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val stopIntent1 = Intent(this, com.example.service.CompressionService::class.java).apply {
                action = "STOP"
            }
            try { startService(stopIntent1) } catch (e: Exception) {}
            val stopIntent2 = Intent(this, com.example.service.FFmpegService::class.java).apply {
                action = "STOP"
            }
            try { startService(stopIntent2) } catch (e: Exception) {}
        }
    }
}
