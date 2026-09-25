package com.example

import org.junit.Test
import androidx.media3.effect.Presentation

class TestPresentation {
    @Test
    fun testConsts() {
        val clazz = Presentation::class.java
        val sb = StringBuilder()
        for (f in clazz.declaredFields) {
            if (f.name.startsWith("LAYOUT_")) {
                sb.append(f.name).append("=")
                f.isAccessible = true
                sb.append(f.get(null)).append("\n")
            }
        }
        throw RuntimeException("CONSTANTS:\n" + sb.toString())
    }
}
