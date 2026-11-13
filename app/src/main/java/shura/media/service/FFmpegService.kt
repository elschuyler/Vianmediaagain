package shura.media.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import shura.media.LogKeeper
import shura.media.data.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    companion object {
        private const val ACTION_CANCEL_FFMPEG = "shura.media.ACTION_CANCEL_FFMPEG"

        @Volatile
        private var activeInstance: FFmpegService? = null

        fun requestCancel() {
            activeInstance?.let { svc ->
                svc.batchRunId.incrementAndGet()
                svc.isCancelled = true
                svc.activeJob?.cancel()
            }
            FFmpegKit.cancel()
            Handler(Looper.getMainLooper()).post {
                FFmpegStatus.isRunning = false
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    @Volatile private var activeJob: Job? = null
    private val batchRunId = java.util.concurrent.atomic.AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "FFmpegServiceChannel"
    @Volatile private var isCancelled = false

    private val cancelReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_FFMPEG) {
                requestCancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            cancelReceiver,
            android.content.IntentFilter(ACTION_CANCEL_FFMPEG),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Cleanup orphaned temp files from previous crashed/killed sessions
        cacheDir.listFiles()?.forEach { file ->
            val n = file.name
            if (n.startsWith("ffmpeg_") || n.startsWith("editor_") || n.startsWith("join_") || n.startsWith("preconverted_") || n.startsWith("audio_add_")) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }

        createNotificationChannel()
    }

    private fun startEmptyForegroundAndStop(startId: Int) {
        val emptyNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing Video(s)")
            .setContentText("Nothing to process")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, emptyNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2, emptyNotif)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            batchRunId.incrementAndGet()
            isCancelled = true
            activeJob?.cancel()
            FFmpegKit.cancel()
            FFmpegStatus.isRunning = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val uris = intent?.getStringArrayListExtra("uris")
        val commandArgs = intent?.getStringArrayListExtra("commandArgs")
        if (uris.isNullOrEmpty() || commandArgs.isNullOrEmpty()) {
            LogKeeper.logError("FFmpegService", "Invalid FFmpeg batch request: missing URIs or commandArgs", null)
            startEmptyForegroundAndStop(startId)
            return START_NOT_STICKY
        }

        val previousJob = activeJob
        if (previousJob != null && previousJob.isActive) {
            isCancelled = true
            FFmpegKit.cancel()
            previousJob.cancel()
        }

        val originalNames = intent.getStringArrayListExtra("original_names")
        val outputExt = intent.getStringExtra("outputExt") ?: "mp4"

        val cancelIntent = Intent(ACTION_CANCEL_FFMPEG).setPackage(packageName)
        val cancelPendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            0,
            cancelIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing Video(s)")
            .setContentText("0 / ${uris.size} completed")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2, notification)
        }

        // Resource Exclusivity: Pause active video playback to yield CPU cores and decoders to FFmpeg
        try {
            shura.media.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {
            LogKeeper.logError("FFmpegService", "Could not pause player: ${e.message}", e)
        }

        val myRunId = batchRunId.incrementAndGet()
        FFmpegStatus.isRunning = true
        FFmpegStatus.lastOutputUri = null
        FFmpegStatus.totalFiles = uris.size
        FFmpegStatus.currentFile = 0
        FFmpegStatus.currentProgress = "Starting..."

        activeJob = serviceScope.launch {
            try {
                previousJob?.join()
                if (batchRunId.get() != myRunId) return@launch
                isCancelled = false
                LogKeeper.log("Starting FFmpeg batch for ${uris.size} file(s)", "FFmpegService")
                val (succeeded, failed) = processFiles(uris, originalNames, commandArgs, outputExt)
                LogKeeper.log("Completed FFmpeg batch: $succeeded succeeded, $failed failed (of ${uris.size})", "FFmpegService")
            } catch (e: CancellationException) {
                LogKeeper.log("FFmpeg batch coroutine cancelled", "FFmpegService")
            } catch (e: Exception) {
                LogKeeper.logError("FFmpegService", "Unexpected exception during FFmpeg batch: ${e.message}", e)
            } finally {
                if (batchRunId.get() == myRunId) {
                    withContext(Dispatchers.Main.immediate) {
                        FFmpegStatus.isRunning = false
                    }
                    ServiceCompat.stopForeground(this@FFmpegService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun processFiles(uris: List<String>, originalNames: List<String>?, commandArgs: List<String>, outputExt: String): Pair<Int, Int> {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsManager = SettingsManager.getInstance(applicationContext)
        val outputUriStr = settingsManager.outputFolderUri.value

        var processedCount = 0
        var succeededCount = 0
        var failedCount = 0
        for ((index, uriStr) in uris.withIndex()) {
            if (isCancelled || !currentCoroutineContext().isActive) {
                LogKeeper.log("FFmpeg batch cancelled.", "FFmpegService")
                break
            }
            val uri = Uri.parse(uriStr)
            val isFileUri = uri.scheme == "file" || uriStr.startsWith("file:") || uriStr.startsWith("/")

            // 1. Copy to cache for FFmpeg processing
            val tempInFile = java.io.File(cacheDir, "ffmpeg_in_${System.currentTimeMillis()}.${getFileExtension(uri)}")
            try {
                shura.media.data.MediaUriResolver.openInputStreamSafely(this@FFmpegService, uriStr).use { input ->
                    tempInFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                LogKeeper.logError("FFmpegService", "Failed to copy input file", e)
                failedCount++
                processedCount++
                val updatedProcessed = processedCount
                mainHandler.post {
                    FFmpegStatus.currentFile = updatedProcessed
                }
                continue
            }

            var actualInputFile = tempInFile
            
            val inputMimeType = if (isFileUri) {
                val p = uri.path ?: uriStr
                val ext = p.substringAfterLast('.', "").lowercase()
                getMimeType(ext)
            } else {
                contentResolver.getType(uri) ?: "video/mp4"
            }
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
                    val session = FFmpegKit.executeWithArguments(
                        arrayOf(
                            "-y",
                            "-framerate", calculatedFps.toString(),
                            "-i", "${framesDir.absolutePath}/frame_%04d.png",
                            "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                            "-vcodec", "libx264",
                            "-crf", "23",
                            "-preset", "ultrafast",
                            "-pix_fmt", "yuv420p",
                            "-metadata:s:v:0", "rotate=0",
                            preConvertedFile.absolutePath
                        )
                    )
                    if (ReturnCode.isSuccess(session.returnCode) && preConvertedFile.exists()) {
                        actualInputFile = preConvertedFile
                    }
                    framesDir.deleteRecursively()
                }
            } else if (inputMimeType == "image/gif") {
                val preConvertedFile = java.io.File(cacheDir, "preconverted_${System.currentTimeMillis()}.mp4")
                val session = FFmpegKit.executeWithArguments(
                    arrayOf(
                        "-y",
                        "-i", actualInputFile.absolutePath,
                        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                        "-vcodec", "libx264",
                        "-crf", "23",
                        "-preset", "ultrafast",
                        "-pix_fmt", "yuv420p",
                        "-metadata:s:v:0", "rotate=0",
                        preConvertedFile.absolutePath
                    )
                )
                if (ReturnCode.isSuccess(session.returnCode) && preConvertedFile.exists()) {
                    actualInputFile = preConvertedFile
                }
            }

            val tempOutFile = java.io.File(cacheDir, "ffmpeg_out_${System.currentTimeMillis()}.$outputExt")
            val resolvedArgs = commandArgs.map { token ->
                when (token) {
                    "%INPUT%" -> actualInputFile.absolutePath
                    "%OUTPUT%" -> tempOutFile.absolutePath
                    else -> token
                }
            }.toTypedArray()

            LogKeeper.log("Executing FFmpeg command (${resolvedArgs.size} args)", "FFmpegService")

            FFmpegKitConfig.enableStatisticsCallback { statistics ->
                val timeSec = statistics.time / 1000
                val minutes = timeSec / 60
                val seconds = timeSec % 60
                val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

                val sizeMb = statistics.size / (1024.0 * 1024.0)
                val sizeStr = String.format(java.util.Locale.US, "%.2f MB", sizeMb)

                val speed = statistics.speed
                mainHandler.post {
                    FFmpegStatus.currentProgress = "Time: $timeStr | Size: $sizeStr | Speed: ${speed}x"
                }
            }

            // Execute FFmpeg via tokenized arguments
            val session = try {
                FFmpegKit.executeWithArguments(resolvedArgs)
            } finally {
                FFmpegKitConfig.enableStatisticsCallback(null)
            }
            val returnCode = session.returnCode

            if (isCancelled || !currentCoroutineContext().isActive) {
                LogKeeper.log("FFmpeg processing cancelled.", "FFmpegService")
                FFmpegKit.cancel(session.sessionId)
                break
            }

            var itemExportSucceeded = false
            if (ReturnCode.isSuccess(returnCode)) {
                LogKeeper.log("FFmpeg processing succeeded.", "FFmpegService")

                // 2. Move out to SAF output folder
                val origName = originalNames?.getOrNull(index) ?: getOriginalFileName(uri)
                val fileName = "${origName}_edited.$outputExt"
                val (finalUri, outStream) = getOutputStreamAndUri(outputUriStr, fileName, getMimeType(outputExt))
                if (outStream != null) {
                    try {
                        mainHandler.post { FFmpegStatus.currentProgress = "Saving output file..." }
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
                                        mainHandler.post { FFmpegStatus.currentProgress = "Saving: $percent%" }
                                        lastReportTime = now
                                    }
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && finalUri != null) {
                            try {
                                val cv = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                                }
                                contentResolver.update(finalUri, cv, null, null)
                            } catch (e: Exception) {}
                        }
                        try {
                            finalUri?.path?.let { p ->
                                android.media.MediaScannerConnection.scanFile(this@FFmpegService, arrayOf(p), arrayOf(getMimeType(outputExt)), null)
                            }
                        } catch (e: Exception) {}
                        LogKeeper.log("Saved to output folder", "FFmpegService")
                        val uriStringOut = finalUri?.toString()
                        mainHandler.post { FFmpegStatus.lastOutputUri = uriStringOut }
                        itemExportSucceeded = true
                        succeededCount++
                    } catch (e: Exception) {
                        LogKeeper.logError("FFmpegService", "Failed to copy output file to SAF", e)
                        failedCount++
                    }
                } else {
                    failedCount++
                }
            } else if (ReturnCode.isCancel(returnCode)) {
                LogKeeper.log("FFmpeg processing cancelled by user.", "FFmpegService")
            } else {
                val tailLogs = LogKeeper.sanitize(session.allLogsAsString.orEmpty().lines().takeLast(25).joinToString("\n"))
                LogKeeper.logError("FFmpegService", "FFmpeg failed with code ${returnCode?.value ?: "null"}\nLogs:\n$tailLogs", Exception(session.failStackTrace))
                failedCount++
            }

            // Cleanup temps
            if (tempInFile.exists()) tempInFile.delete()
            if (actualInputFile != tempInFile && actualInputFile.exists()) actualInputFile.delete()
            if (tempOutFile.exists()) tempOutFile.delete()
            // Zero-Memory: Delete any internal editor converted temp files ONLY if export succeeded
            if (itemExportSucceeded && isFileUri) {
                try {
                    val p = when {
                        uriStr.startsWith("file://") -> uriStr.removePrefix("file://")
                        uriStr.startsWith("file:/") -> uriStr.removePrefix("file:")
                        uriStr.startsWith("file:") -> uriStr.removePrefix("file:")
                        else -> uri.path ?: uriStr
                    }
                    val candidate1 = java.io.File(p)
                    val candidate2 = java.io.File(Uri.decode(p))
                    val candidate3 = uri.path?.let { java.io.File(it) }
                    val candidate4 = uri.path?.let { java.io.File(Uri.decode(it)) }
                    val f = listOfNotNull(candidate1, candidate2, candidate3, candidate4).firstOrNull { it.exists() }
                    if (f != null && f.name.startsWith("editor_converted_") && f.parentFile?.absolutePath == cacheDir.absolutePath) {
                        f.delete()
                    }
                } catch (e: Exception) {}
            }

            processedCount++
            val notification = NotificationCompat.Builder(this@FFmpegService, CHANNEL_ID)
                .setContentTitle("Processing Video(s)")
                .setContentText("$processedCount / ${uris.size} completed")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(uris.size, processedCount, false)
                .build()
            notificationManager.notify(2, notification)
            val updatedCount = processedCount
            mainHandler.post { FFmpegStatus.currentFile = updatedCount }
        }
        return Pair(succeededCount, failedCount)
    }
    
    private fun getFileExtension(uri: Uri): String {
        val uriStr = uri.toString()
        if (uri.scheme == "file" || uriStr.startsWith("file:") || uriStr.startsWith("/")) {
            val p = uri.path ?: uriStr
            val ext = p.substringAfterLast('.', "").substringBefore('?').lowercase()
            if (ext.isNotBlank()) return ext
        }
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
        val isImage = mimeType.startsWith("image/")
        val isAudio = mimeType.startsWith("audio/")
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = when {
                    isAudio -> android.os.Environment.DIRECTORY_MUSIC
                    isImage -> android.os.Environment.DIRECTORY_PICTURES
                    else -> android.os.Environment.DIRECTORY_MOVIES
                }
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/Edited")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                isAudio -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                isImage -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            when {
                isAudio -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                isImage -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
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
        if (activeInstance === this) activeInstance = null
        try { unregisterReceiver(cancelReceiver) } catch (_: Exception) {}
        batchRunId.incrementAndGet()
        isCancelled = true
        FFmpegKit.cancel()
        activeJob?.cancel()
        serviceJob.cancel()
        mainHandler.post { FFmpegStatus.isRunning = false }
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
