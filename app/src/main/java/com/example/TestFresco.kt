package com.example
import com.facebook.animated.webp.WebPImage
fun test(bytes: ByteArray) {
    val img = WebPImage.createFromByteArray(bytes, com.facebook.imagepipeline.common.ImageDecodeOptions.defaults())
    val fc = img.frameCount
    if (fc > 0) {
        val frame = img.getFrame(0)
        val x = frame.xOffset
        val y = frame.yOffset
        frame.dispose()
    }
}
