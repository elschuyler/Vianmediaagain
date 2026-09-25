package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode

object FFmpegStatus {
    var isRunning by mutableStateOf(false)
    var totalFiles by mutableStateOf(0)
    var currentFile by mutableStateOf(0)
    var currentProgress by mutableStateOf("")
    var lastOutputUri by mutableStateOf<String?>(null)
}

class FFmpegService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val CHANNEL_ID = "FFmpegServiceChannel"
    private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        
        // Cleanup orphaned temp files from previous crashed/killed sessions
        cacheDir.listFiles()?.forEach { file ->
            val n = file.name
            if (n.startsWith("ffmpeg_") || n.startsWith("editor_") || n.startsWith("join_") || n.startsWith("preconverted_")) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            isCancelled = true
            FFmpegKit.cancel()
            FFmpegStatus.isRunning = false
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val uris = intent?.getStringArrayListExtra("uris") ?: return START_NOT_STICKY
        val originalNames = intent?.getStringArrayListExtra("original_names")
        val commandTemplate = intent.getStringExtra("commandTemplate") ?: ""
        val outputExt = intent.getStringExtra("outputExt") ?: "mp4"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing Video(s)")
            .setContentText("0 / ${uris.size} completed")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .addAction(android.R.drawable.ic_delete, "Cancel", 
                android.app.PendingIntent.getService(this, 0, Intent(this, FFmpegService::class.java).apply { setAction("STOP") }, android.app.PendingIntent.FLAG_IMMUTABLE))
            .build()

        startForeground(2, notification)

        // Resource Exclusivity: Pause active video playback to yield CPU cores and decoders to FFmpeg
        try {
            com.example.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {
            LogKeeper.logError("FFmpegService", "Could not pause player: ${e.message}", e)
        }

        FFmpegStatus.isRunning = true
        FFmpegStatus.lastOutputUri = null
        FFmpegStatus.totalFiles = uris.size
        FFmpegStatus.currentFile = 0
        FFmpegStatus.currentProgress = "Starting..."
        isCancelled = false

        serviceScope.launch {
            LogKeeper.log("Starting FFmpeg batch for ${uris.size} file(s)", "FFmpegService")
            processFiles(uris, originalNames, commandTemplate, outputExt)
            LogKeeper.log("Completed FFmpeg batch for ${uris.size} file(s)", "FFmpegService")
            FFmpegStatus.isRunning = false
            stopForeground(true)
            stopSelfResult(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun processFiles(uris: List<String>, originalNames: List<String>?, commandTemplate: String, outputExt: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsManager = SettingsManager.getInstance(applicationContext)
        val outputUriStr = settingsManager.outputFolderUri.value

        var count = 0
        for (uriStr in uris) {
            if (isCancelled) {
                LogKeeper.log("FFmpeg batch cancelled.", "FFmpegService")
                break
            }
            val uri = Uri.parse(uriStr)
            
            // 1. Copy to cache for FFmpeg processing
            val tempInFile = java.io.File(cacheDir, "ffmpeg_in_${System.currentTimeMillis()}.${getFileExtension(uri)}")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    tempInFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                LogKeeper.logError("FFmpegService", "Failed to copy input file: $uriStr", e)
                count++
                continue
            }

            var actualInputFile = tempInFile
            val pngFramesDir: java.io.File? = null
            
            val inputMimeType = contentResolver.getType(uri) ?: "video/mp4"
            if (inputMimeType == "image/webp") {
                val framesDir = java.io.File(cacheDir, "ffmpeg_frames_${System.currentTimeMillis()}")
                framesDir.mkdirs()
                var frameCount = 0
                var calculatedFps = 30
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    try {
                        val bytes = actualInputFile.readBytes()
                        try {
                            val clazz = Class.forName("com.facebook.soloader.nativeloader.NativeLoader")
                            val isInitializedMethod = clazz.getMethod("isInitialized")
                            val isInit = isInitializedMethod.invoke(null) as Boolean
                            if (!isInit) {
                                val delegateClazz = Class.forName("com.facebook.soloader.nativeloader.SystemDelegate")
                                val delegate = delegateClazz.newInstance()
                                val delegateInterface = Class.forName("com.facebook.soloader.nativeloader.NativeLoaderDelegate")
                                val initMethod = clazz.getMethod("init", delegateInterface)
                                initMethod.invoke(null, delegate)
                            }
                        } catch (e: Exception) {}
                        val webpImage = com.facebook.animated.webp.WebPImage.createFromByteArray(bytes, com.facebook.imagepipeline.common.ImageDecodeOptions.defaults())
                        frameCount = webpImage.frameCount
                        val durations = webpImage.frameDurations
                        val averageDurationMs = if (frameCount > 0) durations.sum() / frameCount else 33
                        calculatedFps = if (averageDurationMs > 0) 1000 / averageDurationMs else 30
                        
                        
                        var lastCachedFrame: android.graphics.Bitmap? = null
                        var lastCachedFrameIndex = -1

                        val result = com.facebook.imagepipeline.animated.base.AnimatedImageResult.forAnimatedImage(webpImage)
                        val backend = com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl(
                            com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil(), 
                            result, 
                            android.graphics.Rect(0, 0, webpImage.width, webpImage.height), 
                            false
                        )
                        val compositor = com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor(
                            backend, 
                            false, 
                            object : com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor.Callback {
                                override fun onIntermediateResult(frameNumber: Int, bitmap: android.graphics.Bitmap) {}
                                override fun getCachedBitmap(frameNumber: Int): com.facebook.common.references.CloseableReference<android.graphics.Bitmap>? {
                                    return if (frameNumber == lastCachedFrameIndex && lastCachedFrame != null) {
                                        com.facebook.common.references.CloseableReference.of(lastCachedFrame!!, com.facebook.common.references.ResourceReleaser { })
                                    } else null
                                }
                            }
                        )

                        var previousFrameToRecycle: android.graphics.Bitmap? = null
                        for (i in 0 until frameCount) {
                            val w = webpImage.width
                            val h = webpImage.height
                            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                            compositor.renderFrame(i, bmp)
                            java.io.File(framesDir, "frame_%04d.png".format(i))
                                .outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            
                            previousFrameToRecycle?.recycle()
                            lastCachedFrame = bmp
                            lastCachedFrameIndex = i
                            previousFrameToRecycle = bmp
                        }
                        previousFrameToRecycle?.recycle()
                        webpImage.dispose()
                    } catch (e: Exception) {
                        LogKeeper.logError("FFmpegService", "Frame extraction failed: ${e.message}", e)
                    }
                }
                if (frameCount > 0) {
                    val preConvertedFile = java.io.File(cacheDir, "preconverted_${System.currentTimeMillis()}.mp4")
                    val preCmd = "-y -framerate $calculatedFps -i '${framesDir.absolutePath}/frame_%04d.png' -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -vcodec libx264 -crf 23 -preset ultrafast -pix_fmt yuv420p -metadata:s:v:0 rotate=0 '${preConvertedFile.absolutePath}'"
                    val session = FFmpegKit.execute(preCmd)
                    if (ReturnCode.isSuccess(session.returnCode) && preConvertedFile.exists()) {
                        actualInputFile = preConvertedFile
                    }
                    framesDir.deleteRecursively()
                }
            } else if (inputMimeType == "image/gif") {
                val preConvertedFile = java.io.File(cacheDir, "preconverted_${System.currentTimeMillis()}.mp4")
                val preCmd = "-y -i '${actualInputFile.absolutePath}' -vf \"scale=trunc(iw/2)*2:trunc(ih/2)*2\" -vcodec libx264 -crf 23 -preset ultrafast -pix_fmt yuv420p -metadata:s:v:0 rotate=0 '${preConvertedFile.absolutePath}'"
                val session = FFmpegKit.execute(preCmd)
                if (ReturnCode.isSuccess(session.returnCode) && preConvertedFile.exists()) {
                    actualInputFile = preConvertedFile
                }
            }

            val tempOutFile = java.io.File(cacheDir, "ffmpeg_out_${System.currentTimeMillis()}.$outputExt")
            
            val inputArg = if (pngFramesDir != null) {
                "-framerate 10 -i '${actualInputFile.absolutePath}'"
            } else {
                "-i '${actualInputFile.absolutePath}'"
            }
            
            // Replace placeholders in command
            val cmd = commandTemplate
                .replace("-i %INPUT%", inputArg)
                .replace("%OUTPUT%", "'${tempOutFile.absolutePath}'")

            LogKeeper.log("Executing FFmpeg: $cmd", "FFmpegService")

            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                val timeSec = statistics.time / 1000
                val minutes = timeSec / 60
                val seconds = timeSec % 60
                val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                
                val sizeMb = statistics.size / (1024.0 * 1024.0)
                val sizeStr = String.format(java.util.Locale.US, "%.2f MB", sizeMb)
                
                val speed = statistics.speed
                FFmpegStatus.currentProgress = "Time: $timeStr | Size: $sizeStr | Speed: ${speed}x"
            }

            // Execute FFmpeg
            val session = FFmpegKit.execute(cmd)
            val returnCode = session.returnCode
            
            FFmpegKitConfig.enableStatisticsCallback(null) // clear callback

            if (isCancelled) {
                LogKeeper.log("FFmpeg processing cancelled.", "FFmpegService")
                FFmpegKit.cancel(session.sessionId)
                break
            }

            if (ReturnCode.isSuccess(returnCode)) {
                LogKeeper.log("FFmpeg processing succeeded.", "FFmpegService")
            
            // 2. Move out to SAF output folder
            val origName = originalNames?.getOrNull(count) ?: getOriginalFileName(uri)
            val fileName = "${origName}_edited.$outputExt"
            val (finalUri, outStream) = getOutputStreamAndUri(outputUriStr, fileName, getMimeType(outputExt))
            if (outStream != null) {
                try {
                    FFmpegStatus.currentProgress = "Saving output file..."
                    tempOutFile.inputStream().use { input ->
                        outStream.use { output ->
                            val buffer = ByteArray(8192)
                            var bytesCopied: Long = 0
                            val totalBytes = tempOutFile.length()
                            var lastReportTime = System.currentTimeMillis()
                            while (true) {
                                val bytes = input.read(buffer)
                                if (bytes < 0) break
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 500) {
                                    val percent = if (totalBytes > 0) (bytesCopied * 100 / totalBytes).toInt() else 0
                                    FFmpegStatus.currentProgress = "Saving: $percent%"
                                    lastReportTime = now
                                }
                            }
                        }
                    }
                    LogKeeper.log("Saved to output folder: $fileName", "FFmpegService")
                    FFmpegStatus.lastOutputUri = finalUri?.toString()
                } catch (e: Exception) {
                    LogKeeper.logError("FFmpegService", "Failed to copy output file to SAF", e)
                }
            }
            } else if (ReturnCode.isCancel(returnCode)) {
                LogKeeper.log("FFmpeg processing cancelled by user.", "FFmpegService")
            } else {
                LogKeeper.logError("FFmpegService", "FFmpeg failed with code ${returnCode?.value ?: "null"}\nLogs: ${session.allLogsAsString}", Exception(session.failStackTrace))
            }

            // Cleanup temps
            if (tempInFile.exists()) tempInFile.delete()
            if (actualInputFile != tempInFile && actualInputFile.exists()) actualInputFile.delete()
            pngFramesDir?.deleteRecursively()
            if (tempOutFile.exists()) tempOutFile.delete()
            // Zero-Memory: Delete any internal cache temp files passed as input (e.g. editor_converted_*)
            if (uri.scheme == "file" || uriStr.startsWith("file://")) {
                try {
                    val p = uri.path ?: uriStr.removePrefix("file://")
                    val f = java.io.File(p)
                    if (f.exists() && f.parentFile?.absolutePath == cacheDir.absolutePath) {
                        f.delete()
                    }
                } catch (e: Exception) {}
            }

            count++
            val notification = NotificationCompat.Builder(this@FFmpegService, CHANNEL_ID)
                .setContentTitle("Processing Video(s)")
                .setContentText("$count / ${uris.size} completed")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(uris.size, count, false)
                .build()
            notificationManager.notify(2, notification)
            FFmpegStatus.currentFile = count
        }
    }
    
    private fun getFileExtension(uri: Uri): String {
        val mimeType = contentResolver.getType(uri) ?: return "mp4"
        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
    }

    private fun getMimeType(ext: String): String {
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
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
        return result?.substringBeforeLast(".") ?: "edited_${System.currentTimeMillis()}"
    }

    private fun getOutputStreamAndUri(outputUriStr: String?, fileName: String, mimeType: String): Pair<Uri?, java.io.OutputStream?> {
        if (outputUriStr != null) {
            try {
                val treeUri = Uri.parse(outputUriStr)
                val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val newUri = android.provider.DocumentsContract.createDocument(
                    contentResolver,
                    docUri,
                    mimeType,
                    fileName
                )
                if (newUri != null) {
                    return Pair(newUri, contentResolver.openOutputStream(newUri))
                }
            } catch (e: Exception) {
                LogKeeper.logError("FFmpegService", "Failed SAF create", e)
            }
        }
        // Fallback to media store
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = if (mimeType.startsWith("audio")) android.os.Environment.DIRECTORY_MUSIC else android.os.Environment.DIRECTORY_MOVIES
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/Edited")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mimeType.startsWith("audio")) android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            if (mimeType.startsWith("audio")) android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = contentResolver.insert(collection, contentValues)
        return Pair(uri, uri?.let { contentResolver.openOutputStream(it) })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "FFmpeg Service Channel",
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
        serviceJob.cancel()
        cacheDir.listFiles()?.forEach { file ->
            val n = file.name
            if (n.startsWith("ffmpeg_") || n.startsWith("join_")) {
                try {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                } catch (e: Exception) {}
            }
        }
    }
}
