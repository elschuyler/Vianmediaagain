package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis

class MyBitmapLoader(val context: Context) : BitmapLoader {
    override fun supportsMimeType(mimeType: String) = true

    private fun decodeDownsampledBitmap(data: ByteArray, maxDim: Int = 256): Bitmap? {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, options)
        var sampleSize = 1
        val maxSide = maxOf(options.outWidth, options.outHeight)
        while (maxSide / (sampleSize * 2) >= maxDim) {
            sampleSize *= 2
        }
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val bmp = decodeDownsampledBitmap(data, 256) ?: android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        if (bmp != null) future.set(bmp) else future.setException(Exception("Failed to decode bitmap"))
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Try Coil image loading (downsampled to 256x256)
                try {
                    val req = ImageRequest.Builder(context)
                        .data(uri)
                        .size(256)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                    val result = context.imageLoader.execute(req)
                    val dr = result.drawable
                    if (dr is android.graphics.drawable.BitmapDrawable) {
                        future.set(dr.bitmap)
                        return@launch
                    }
                } catch (e: Exception) {}

                // 2. Try MediaMetadataRetriever (embedded artwork or video frame)
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val pic = retriever.embeddedPicture
                    if (pic != null) {
                        val bmp = decodeDownsampledBitmap(pic, 256)
                        if (bmp != null) {
                            future.set(bmp)
                            return@launch
                        }
                    }
                    val frame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(1000000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
                            ?: retriever.frameAtTime
                    } else {
                        retriever.frameAtTime
                    }
                    if (frame != null) {
                        val scaled = if (frame.width > 256 || frame.height > 256) {
                            Bitmap.createScaledBitmap(frame, 256, (256f * frame.height / frame.width).toInt().coerceAtLeast(1), true)
                        } else {
                            frame
                        }
                        future.set(scaled)
                        return@launch
                    }
                } catch (e: Exception) {
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }

                // 3. Try ContentResolver loadThumbnail on Android 10+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val thumb = context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                        if (thumb != null) {
                            future.set(thumb)
                            return@launch
                        }
                    } catch (e: Exception) {}
                }

                future.setException(Exception("No bitmap available for $uri"))
            } catch(e: Exception) { 
                future.setException(e) 
            }
        }
        return future
    }
}
