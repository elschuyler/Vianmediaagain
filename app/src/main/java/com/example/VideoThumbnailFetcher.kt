package com.example

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import android.graphics.drawable.BitmapDrawable

class VideoThumbnailFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val context = options.context
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val size = 512
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        val bmp = context.contentResolver.loadThumbnail(uri, Size(size, size), signal)
                        cont.resume(bmp) { }
                    } catch (e: Exception) {
                        if (e !is android.os.OperationCanceledException) {
                            cont.resume(null) { }
                        } else {
                            cont.cancel(e)
                        }
                    }
                }
            } else {
                val idStr = uri.lastPathSegment
                val id = idStr?.toLongOrNull()
                if (id != null) {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }

        return if (bitmap != null) {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } else {
            null
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val isVideoUri = data.scheme == "content" && data.toString().contains("video")
            return if (isVideoUri) {
                VideoThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }
}
