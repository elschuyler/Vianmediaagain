package com.example

import org.junit.Test
import androidx.media3.common.VideoSize

class TestVideoSize {
    @Test
    fun test() {
        val size = VideoSize(1920, 1080, 90, 1.0f)
        println("width=${size.width} height=${size.height} unappliedRotationDegrees=${size.unappliedRotationDegrees}")
    }
}
