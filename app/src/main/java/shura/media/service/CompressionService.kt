package shura.media.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import shura.media.LogKeeper
import shura.media.data.MediaUriResolver
import shura.media.data.SettingsManager
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

object CompressionStatus {
    var isRunning by mutableStateOf(false)
    var totalFiles by mutableStateOf(0)
    var currentFile by mutableStateOf(0)

    fun stop(context: Context) {
        CompressionService.requestCancel()
    }
}

class CompressionService : Service() {

    companion object {
        @Volatile
        private var activeInstance: CompressionService? = null

        fun requestCancel() {
            activeInstance?.let { svc ->
                svc.batchRunId.incrementAndGet()
                svc.isCancelled = true
                svc.activeBatchJob?.cancel()
            }
            Handler(Looper.getMainLooper()).post {
                CompressionStatus.isRunning = false
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    @Volatile private var activeBatchJob: Job? = null
    private val batchRunId = java.util.concurrent.atomic.AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "CompressionServiceChannel"
    @Volatile private var isCancelled = false

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            batchRunId.incrementAndGet()
            isCancelled = true
            activeBatchJob?.cancel()
            CompressionStatus.isRunning = false
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(1)
            } catch (e: Exception) {}
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val uris = intent?.getStringArrayListExtra("uris")
        if (uris.isNullOrEmpty()) {
            val emptyNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Compressing Images")
                .setContentText("No files to process")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, emptyNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, emptyNotif)
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val previousJob = activeBatchJob
        if (previousJob != null && previousJob.isActive) {
            isCancelled = true
            previousJob.cancel()
        }

        val maxWidth = intent.getIntExtra("maxWidth", -1)
        val maxHeight = intent.getIntExtra("maxHeight", -1)
        val quality = intent.getIntExtra("quality", 80)
        val formatStr = intent.getStringExtra("format") ?: "JPEG"
        val sourceUris = intent.getStringArrayListExtra("source_uris")
        val isSingleEdit = intent.getBooleanExtra("is_single_edit", false)
        val outputSuffix = intent.getStringExtra("output_suffix")?.takeIf { it.isNotBlank() } ?: "compressed"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compressing Images")
            .setContentText("0 / ${uris.size} completed")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        // Resource Exclusivity: Pause active video playback to prevent memory pressure/OOM during heavy bitmap compression
        try {
            shura.media.service.PlayerManager.exoPlayer?.pause()
        } catch (e: Exception) {
            LogKeeper.logError("Compressor", "Could not pause player: ${e.message}", e)
        }

        val myRunId = batchRunId.incrementAndGet()
        CompressionStatus.isRunning = true
        CompressionStatus.totalFiles = uris.size
        CompressionStatus.currentFile = 0

        activeBatchJob = serviceScope.launch {
            try {
                previousJob?.join()
                if (batchRunId.get() != myRunId) return@launch
                isCancelled = false
                LogKeeper.log("Starting batch compression for ${uris.size} images...", "Compressor")
                processImages(uris, sourceUris, maxWidth, maxHeight, quality, formatStr, isSingleEdit, outputSuffix)
                LogKeeper.log("Completed batch compression for ${uris.size} images.", "Compressor")
            } catch (e: kotlinx.coroutines.CancellationException) {
                LogKeeper.log("Batch compression job cancelled.", "Compressor")
            } catch (e: Exception) {
                LogKeeper.logError("Compressor", "Unexpected error in batch compression", e)
            } finally {
                if (batchRunId.get() == myRunId) {
                    withContext(Dispatchers.Main.immediate) {
                        CompressionStatus.isRunning = false
                    }
                    ServiceCompat.stopForeground(this@CompressionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                }
            }
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

    private data class ExifOrientationInfo(
        val rotationDegrees: Int = 0,
        val flipHorizontal: Boolean = false,
        val swapDimensions: Boolean = false
    ) {
        val needsTransform: Boolean
            get() = rotationDegrees != 0 || flipHorizontal
    }

    private fun readExifOrientation(uriStr: String): ExifOrientationInfo {
        return try {
            MediaUriResolver.openInputStreamSafely(this@CompressionService, uriStr).use { stream ->
                val exif = android.media.ExifInterface(stream)
                when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> ExifOrientationInfo(rotationDegrees = 90, flipHorizontal = false, swapDimensions = true)
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> ExifOrientationInfo(rotationDegrees = 180, flipHorizontal = false, swapDimensions = false)
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> ExifOrientationInfo(rotationDegrees = 270, flipHorizontal = false, swapDimensions = true)
                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifOrientationInfo(rotationDegrees = 0, flipHorizontal = true, swapDimensions = false)
                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifOrientationInfo(rotationDegrees = 180, flipHorizontal = true, swapDimensions = false)
                    android.media.ExifInterface.ORIENTATION_TRANSPOSE -> ExifOrientationInfo(rotationDegrees = 90, flipHorizontal = true, swapDimensions = true)
                    android.media.ExifInterface.ORIENTATION_TRANSVERSE -> ExifOrientationInfo(rotationDegrees = 270, flipHorizontal = true, swapDimensions = true)
                    else -> ExifOrientationInfo()
                }
            }
        } catch (_: Exception) {
            ExifOrientationInfo()
        }
    }

    private suspend fun processImages(
        uris: List<String>,
        sourceUris: List<String>?,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int,
        formatStr: String,
        isSingleEdit: Boolean,
        outputSuffix: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val settingsManager = SettingsManager.getInstance(applicationContext)
        val outputUriStr = settingsManager.outputFolderUri.value
        val preferOriginalFolder = isSingleEdit && settingsManager.saveSingleEditsToOriginalFolder.value
        var fallbackToastShown = false

        var count = 0
        for ((index, uriStr) in uris.withIndex()) {
            if (isCancelled || !currentCoroutineContext().isActive) {
                LogKeeper.log("Batch compression cancelled/interrupted - halting loop.", "Compressor")
                break
            }
            val uri = Uri.parse(uriStr)
            var sourceBitmap: Bitmap? = null
            var outBitmap: Bitmap? = null
            try {
                val exif = readExifOrientation(uriStr)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                MediaUriResolver.openInputStreamSafely(this@CompressionService, uriStr).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                val rawW = options.outWidth
                val rawH = options.outHeight
                val visualW = if (exif.swapDimensions) rawH else rawW
                val visualH = if (exif.swapDimensions) rawW else rawH

                // Option A: Equal Pixel-Area Budget so horizontal and vertical images compress to comparable pixel counts & sizes
                var scaleRatio = 1.0
                if (maxWidth > 0 && maxHeight > 0 && visualW > 0 && visualH > 0) {
                    val targetPixelBudget = maxWidth.toDouble() * maxHeight.toDouble()
                    val sourcePixels = visualW.toDouble() * visualH.toDouble()
                    val areaRatio = kotlin.math.sqrt(targetPixelBudget / sourcePixels)
                    val maxEdgeClamp = maxOf(maxWidth, maxHeight).toDouble() * 2.0
                    val longVisual = maxOf(visualW, visualH).toDouble()
                    scaleRatio = minOf(areaRatio, maxEdgeClamp / longVisual).coerceAtMost(1.0)

                    val desiredMaxDim = (longVisual * scaleRatio).coerceAtLeast(1.0)
                    var sampleSize = 1
                    while (longVisual / (sampleSize * 2) >= desiredMaxDim) {
                        sampleSize *= 2
                    }
                    options.inSampleSize = sampleSize
                }
                options.inJustDecodeBounds = false

                if (isCancelled || !currentCoroutineContext().isActive) break

                MediaUriResolver.openInputStreamSafely(this@CompressionService, uriStr).use { stream ->
                    sourceBitmap = BitmapFactory.decodeStream(stream, null, options)
                }

                if (isCancelled || !currentCoroutineContext().isActive) break

                if (sourceBitmap != null) {
                    val bitmap = sourceBitmap!!
                    val decW = bitmap.width
                    val decH = bitmap.height
                    val curVisW = if (exif.swapDimensions) decH else decW
                    val curVisH = if (exif.swapDimensions) decW else decH

                    val targetW = if (maxWidth > 0 && maxHeight > 0 && visualW > 0 && visualH > 0 && scaleRatio < 1.0) {
                        (visualW * scaleRatio).toInt().coerceAtLeast(1)
                    } else {
                        curVisW
                    }
                    val targetH = if (maxWidth > 0 && maxHeight > 0 && visualW > 0 && visualH > 0 && scaleRatio < 1.0) {
                        (visualH * scaleRatio).toInt().coerceAtLeast(1)
                    } else {
                        curVisH
                    }

                    outBitmap = when {
                        exif.needsTransform -> {
                            val matrix = android.graphics.Matrix()
                            if (exif.flipHorizontal) {
                                matrix.postScale(-1f, 1f)
                            }
                            if (exif.rotationDegrees != 0) {
                                matrix.postRotate(exif.rotationDegrees.toFloat())
                            }
                            if (targetW != curVisW || targetH != curVisH) {
                                matrix.postScale(
                                    targetW.toFloat() / curVisW.coerceAtLeast(1).toFloat(),
                                    targetH.toFloat() / curVisH.coerceAtLeast(1).toFloat()
                                )
                            }
                            Bitmap.createBitmap(bitmap, 0, 0, decW, decH, matrix, true)
                        }
                        targetW != decW || targetH != decH -> {
                            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                        }
                        else -> bitmap
                    }

                    val compressFormat = when (formatStr) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val ext = formatStr.lowercase()
                    val mimeType = when (ext) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }

                    val origSourceStr = sourceUris?.getOrNull(index) ?: uriStr
                    val origName = getOriginalFileName(Uri.parse(origSourceStr))
                    val fileName = shura.media.data.MediaOutputRouter.buildSuffixedFileName(origName, outputSuffix, ext)
                    val defaultQCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val target = shura.media.data.MediaOutputRouter.openOutputTarget(
                        context = this@CompressionService,
                        sourceUriStr = origSourceStr,
                        fileName = fileName,
                        mimeType = mimeType,
                        preferOriginalFolder = preferOriginalFolder,
                        fallbackOutputTreeUri = outputUriStr,
                        defaultRelativePath = android.os.Environment.DIRECTORY_DOWNLOADS + "/Compressed",
                        defaultCollectionLegacy = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        defaultCollectionQ = defaultQCollection
                    )
                    val outStream = target.stream
                    if (outStream != null) {
                        var compressedOk = false
                        try {
                            outStream.use { s ->
                                compressedOk = outBitmap?.compress(compressFormat, quality, s) == true
                            }
                            if (compressedOk) {
                                shura.media.data.MediaOutputRouter.finalizeOutputTarget(this@CompressionService, target, mimeType)
                                if (preferOriginalFolder && !target.savedToOriginalFolder && !fallbackToastShown) {
                                    fallbackToastShown = true
                                    mainHandler.post {
                                        try {
                                            android.widget.Toast.makeText(
                                                applicationContext,
                                                "Saved to Output Folder (original folder protected by Android)",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } catch (_: Exception) {}
                                    }
                                }
                            } else {
                                shura.media.data.MediaOutputRouter.abortOutputTarget(this@CompressionService, target)
                            }
                        } catch (writeErr: Exception) {
                            shura.media.data.MediaOutputRouter.abortOutputTarget(this@CompressionService, target)
                            throw writeErr
                        }
                    } else {
                        shura.media.data.MediaOutputRouter.abortOutputTarget(this@CompressionService, target)
                    }
                }
            } catch (oom: OutOfMemoryError) {
                LogKeeper.logError("CompressionService", "OOM error compressing item", oom)
                System.gc()
                isCancelled = true
                break
            } catch (e: Exception) {
                LogKeeper.logError("CompressionService", "Failed to compress item", e)
            } finally {
                if (outBitmap != null && outBitmap != sourceBitmap && !outBitmap!!.isRecycled) {
                    outBitmap?.recycle()
                }
                if (sourceBitmap != null && !sourceBitmap!!.isRecycled) {
                    sourceBitmap?.recycle()
                }
                // Zero-Memory: Automatically delete temporary editor files strictly inside cacheDir (e.g. edited_*.jpg)
                if (uri.scheme == "file" || uriStr.startsWith("file:") || uriStr.startsWith("/")) {
                    try {
                        val path = uri.path ?: uriStr.removePrefix("file://").removePrefix("file:")
                        val file = java.io.File(path)
                        if (file.exists() && file.parentFile?.canonicalPath == cacheDir.canonicalPath && file.name.startsWith("edited_")) {
                            file.delete()
                        }
                    } catch (e: Exception) {}
                }
            }
            if (isCancelled || !currentCoroutineContext().isActive) break
            count++
            val notification = NotificationCompat.Builder(this@CompressionService, CHANNEL_ID)
                .setContentTitle("Compressing Images")
                .setContentText("$count / ${uris.size} completed")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(uris.size, count, false)
                .build()
            notificationManager.notify(1, notification)
            val updatedCount = count
            mainHandler.post {
                CompressionStatus.currentFile = updatedCount
            }

            if (count % 5 == 0 || count == uris.size) {
                LogKeeper.log("Progress: $count / ${uris.size} processed.", "Compressor")
            }
        }
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

    override fun onTimeout(startId: Int, fgsType: Int) {
        LogKeeper.logWarn("CompressionService", "Foreground service timeout triggered (type=$fgsType); cancelling gracefully.")
        requestCancel()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) activeInstance = null
        isCancelled = true
        CompressionStatus.isRunning = false
        activeBatchJob?.cancel()
        serviceJob.cancel()
    }
}
