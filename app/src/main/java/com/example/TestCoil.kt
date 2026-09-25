package com.example

import android.content.Context
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.runBlocking
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

fun testCoil(context: Context) {
    runBlocking {
        val request = ImageRequest.Builder(context)
            .data("dummy")
            .size(512, 512)
            .build()
        val result = Coil.imageLoader(context).execute(request)
        val drawable: Drawable? = result.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
        } else if (drawable != null) {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
            drawable.setBounds(0, 0, w, h)
        }
    }
}
