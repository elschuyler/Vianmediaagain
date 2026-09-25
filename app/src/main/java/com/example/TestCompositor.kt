package com.example
import com.facebook.animated.webp.WebPImage
import com.facebook.imagepipeline.animated.impl.AnimatedDrawableBackendImpl
import com.facebook.imagepipeline.animated.impl.AnimatedImageCompositor
import com.facebook.imagepipeline.animated.util.AnimatedDrawableUtil
import com.facebook.imagepipeline.animated.base.AnimatedImageResult
import android.graphics.Rect
import android.graphics.Bitmap
import com.facebook.common.references.CloseableReference

fun testCompositor(bytes: ByteArray) {
    val img = WebPImage.createFromByteArray(bytes, com.facebook.imagepipeline.common.ImageDecodeOptions.defaults())
    val result = AnimatedImageResult.forAnimatedImage(img)
    val backend = AnimatedDrawableBackendImpl(AnimatedDrawableUtil(), result, Rect(0, 0, img.width, img.height), false)
    val compositor = AnimatedImageCompositor(backend, false, object : AnimatedImageCompositor.Callback {
        override fun onIntermediateResult(frameNumber: Int, bitmap: Bitmap) {}
        override fun getCachedBitmap(frameNumber: Int): CloseableReference<Bitmap>? = null
    })
    val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
    compositor.renderFrame(0, bmp)
    img.dispose()
}
