package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.LogKeeper
import com.example.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object CompressionStatus {
    var isRunning by mutableStateOf(false)
    var totalFiles by mutableStateOf(0)
    var currentFile by mutableStateOf(0)

    fun stop(context: Context) {
        isRunning = false
        val intent = Intent(context, CompressionService::class.java).apply {
            action = "STOP"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {}
    }
}

class CompressionService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val CHANNEL_ID = "CompressionServiceChannel"
    @Volatile private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            isCancelled = true
            serviceJob.cancel()
            CompressionStatus.isRunning = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(1)
            } catch (e: Exception) {}
            stopSelf()
            return START_NOT_STICKY
        }
        
        val uris = intent?.getStringArrayListExtra("uris") ?: return START_NOT_STICKY
        isCancelled = false
        val maxWidth = intent.getIntExtra("maxWidth", -1)
        val maxHeight = intent.getIntExtra("maxHeight", -1)
        val quality = intent.getIntExtra("quality", 80)
        val formatStr = intent.getStringExtra("format") ?: "JPEG"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compressing Images")
            .setContentText("0 / ${uris.size} completed")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()

        startForeground(1, notification)

        // Resource Exclusivity: Pause active video playback to prevent memory pressure/OOM during heavy bitmap compression
        try {
            com.example.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {
            LogKeeper.logError("Compressor", "Could not pause player: ${e.message}", e)
        }

        CompressionStatus.isRunning = true
        CompressionStatus.totalFiles = uris.size
        CompressionStatus.currentFile = 0

        serviceScope.launch {
            LogKeeper.log("Starting batch compression for ${uris.size} images...", "Compressor")
            processImages(uris, maxWidth, maxHeight, quality, formatStr)
            LogKeeper.log("Completed batch compression for ${uris.size} images.", "Compressor")
            CompressionStatus.isRunning = false
            stopForeground(true)
            stopSelfResult(startId)
        }

        return START_NOT_STICKY
    }

    private fun getOriginalFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        if (result == null) {
            result = uri.path?.let { java.io.File(it).name }
        }
        return result?.substringBeforeLast(".") ?: "compressed_${System.currentTimeMillis()}"
    }

    private suspend fun processImages(uris: List<String>, maxWidth: Int, maxHeight: Int, quality: Int, formatStr: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsManager = SettingsManager.getInstance(applicationContext)
        val outputUriStr = settingsManager.outputFolderUri.value

        var count = 0
        for (uriStr in uris) {
            if (isCancelled || !serviceJob.isActive) {
                LogKeeper.log("Batch compression cancelled/interrupted - halting loop.", "Compressor")
                break
            }
            val uri = Uri.parse(uriStr)
            var sourceBitmap: Bitmap? = null
            var outBitmap: Bitmap? = null
            try {
                
                // Safely calculate sample size if downscaling is requested
                val options = BitmapFactory.Options()
                if (maxWidth > 0 && maxHeight > 0) {
                    options.inJustDecodeBounds = true
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    val maxTargetDim = maxOf(maxWidth, maxHeight)
                    val maxSourceDim = maxOf(options.outWidth, options.outHeight)
                    var sampleSize = 1
                    while (maxSourceDim / (sampleSize * 2) >= maxTargetDim) {
                        sampleSize *= 2
                    }
                    options.inSampleSize = sampleSize
                    options.inJustDecodeBounds = false
                }

                if (isCancelled || !serviceJob.isActive) break

                contentResolver.openInputStream(uri)?.use { stream ->
                    sourceBitmap = BitmapFactory.decodeStream(stream, null, options)
                }

                if (isCancelled || !serviceJob.isActive) break

                if (sourceBitmap != null) {
                    val bitmap = sourceBitmap!!
                    outBitmap = bitmap
                    if (maxWidth > 0 && maxHeight > 0) {
                        val maxDim = maxOf(maxWidth, maxHeight).toFloat()
                        val minDim = minOf(maxWidth, maxHeight).toFloat()
                        
                        val targetW = if (bitmap.width > bitmap.height) maxDim else minDim
                        val targetH = if (bitmap.width > bitmap.height) minDim else maxDim
                        
                        val ratio = minOf(targetW / bitmap.width, targetH / bitmap.height)
                        
                        if (ratio < 1f) {
                            val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                            val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                            outBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                        }
                    }
                    
                    val compressFormat = when (formatStr) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val ext = formatStr.lowercase()
                    
                    val origName = getOriginalFileName(uri)
                    val fileName = "${origName}_compressed.${ext}"
                    val outStream: OutputStream? = getOutputStream(outputUriStr, fileName, ext)
                    if (outStream != null) {
                        outBitmap?.compress(compressFormat, quality, outStream)
                        outStream.close()
                    }
                }
            } catch (oom: OutOfMemoryError) {
                LogKeeper.logError("CompressionService", "OOM error compressing $uriStr", oom)
                System.gc()
                isCancelled = true
                break
            } catch (e: Exception) {
                LogKeeper.logError("CompressionService", "Failed to compress $uriStr", e)
            } finally {
                if (outBitmap != null && outBitmap != sourceBitmap && !outBitmap!!.isRecycled) {
                    outBitmap?.recycle()
                }
                if (sourceBitmap != null && !sourceBitmap!!.isRecycled) {
                    sourceBitmap?.recycle()
                }
                // Zero-Memory: Automatically delete temporary editor files (e.g. edited_*.jpg)
                if (uri.scheme == "file" || uriStr.startsWith("file://") || uriStr.contains("edited_")) {
                    try {
                        val path = uri.path ?: uriStr.removePrefix("file://")
                        val file = java.io.File(path)
                        if (file.exists() && (file.parentFile?.absolutePath == cacheDir.absolutePath || file.name.startsWith("edited_"))) {
                            file.delete()
                        }
                    } catch (e: Exception) {}
                }
            }
            if (isCancelled || !serviceJob.isActive) break
            count++
            val notification = NotificationCompat.Builder(this@CompressionService, CHANNEL_ID)
                .setContentTitle("Compressing Images")
                .setContentText("$count / ${uris.size} completed")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(uris.size, count, false)
                .build()
            notificationManager.notify(1, notification)
            CompressionStatus.currentFile = count
            
            if (count % 5 == 0 || count == uris.size) {
                LogKeeper.log("Progress: $count / ${uris.size} processed.", "Compressor")
            }
        }
    }

    private fun getOutputStream(outputUriStr: String?, fileName: String, ext: String): OutputStream? {
        if (outputUriStr != null) {
            try {
                val treeUri = Uri.parse(outputUriStr)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val newUri = android.provider.DocumentsContract.createDocument(
                    contentResolver,
                    docUri,
                    "image/${ext}",
                    fileName
                )
                if (newUri != null) {
                    return contentResolver.openOutputStream(newUri)
                }
            } catch (e: Exception) {
                LogKeeper.logError("CompressionService", "Failed SAF create", e)
            }
        }

        // Fallback to media store
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/${ext}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Compressed")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = contentResolver.insert(collection, contentValues)
        return uri?.let { contentResolver.openOutputStream(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Compression Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isCancelled = true
        CompressionStatus.isRunning = false
        serviceJob.cancel()
    }
}
