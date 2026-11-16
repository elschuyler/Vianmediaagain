package shura.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.imageLoader
import coil.request.ImageRequest

class MyBitmapLoader(val context: Context) : BitmapLoader {
    override fun supportsMimeType(mimeType: String) = true

    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        loaderScope.launch {
            try {
                // 1. Check Coil ImageLoader (reuses Library VideoThumbnailFetcher RAM & 100MB disk cache)
                try {
                    val req = ImageRequest.Builder(context)
                        .data(uri)
                        .size(256)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build()
                    val result = context.imageLoader.execute(req)
                    val dr = result.drawable
                    if (dr is android.graphics.drawable.BitmapDrawable && dr.bitmap != null) {
                        future.set(dr.bitmap)
                        return@launch
                    }
                } catch (_: Exception) {}

                val scheme = uri.scheme?.lowercase()
                val isLocalUri = scheme == null || scheme == "content" || scheme == "file"

                // 2. Try ContentResolver loadThumbnail on Android 10+ (OS media thumbnail store, no app-side video decoder)
                if (isLocalUri && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        val thumb = context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
                        if (thumb != null) {
                            future.set(thumb)
                            return@launch
                        }
                    } catch (_: Exception) {}
                }

                // 3. Try MediaMetadataRetriever strictly for local/content audio embedded album art
                if (isLocalUri && shura.media.service.PlayerManager.isAudioOnlyHint(context, uri)) {
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
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                }

                future.setException(Exception("No bitmap available for $uri"))
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
