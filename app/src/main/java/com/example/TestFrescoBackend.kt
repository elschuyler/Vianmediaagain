package com.example
import com.facebook.animated.webp.WebPImage
import com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl
import com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil
import com.facebook.imagepipeline.animated.base.AnimatedImageResult
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas

fun testBackend(bytes: ByteArray) {
    val img = WebPImage.createFromByteArray(bytes, com.facebook.imagepipeline.common.ImageDecodeOptions.defaults())
    val result = AnimatedImageResult.forAnimatedImage(img)
    val backend = AnimatedDrawableBackendImpl(AnimatedDrawableUtil(), result, Rect(0, 0, img.width, img.height), false)
    val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    backend.renderFrame(0, canvas)
    img.dispose()
}
