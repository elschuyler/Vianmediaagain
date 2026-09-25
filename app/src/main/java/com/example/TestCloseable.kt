package com.example
import android.graphics.Bitmap
import com.facebook.common.references.CloseableReference
import com.facebook.common.references.ResourceReleaser

fun testRef(bmp: Bitmap): CloseableReference<Bitmap> {
    return CloseableReference.of(bmp, ResourceReleaser { })
}
