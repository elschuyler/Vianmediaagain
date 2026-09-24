package com.example.foundation

import com.example.foundation.common.CollectionUtils
import com.example.foundation.common.Constants
import com.example.foundation.common.LocaleUtils
import com.example.foundation.common.UnicodeUtils
import com.example.foundation.utils.ByteArrayDictBuffer
import com.example.foundation.utils.CoordinateUtils
import com.example.foundation.utils.RingCharBuffer
import com.example.foundation.utils.Subtype
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FoundationBatch1Test {

    @Test
    fun testConstants() {
        assertEquals(10, Constants.CODE_ENTER)
        assertEquals(32, Constants.CODE_SPACE)
        assertEquals(-4, Constants.CODE_DELETE)
        assertEquals(-1, Constants.NOT_A_COORDINATE)
        assertEquals("keyboard", Constants.Subtype.KEYBOARD_MODE)
    }

    @Test
    fun testLocaleUtils() {
        val enLocale = LocaleUtils.constructLocale("en_US")
        assertEquals("en", enLocale.language)
        assertEquals("US", enLocale.country)

        val frLocale = LocaleUtils.constructLocale("fr-FR")
        assertEquals("fr", frLocale.language)
        assertEquals("FR", frLocale.country)

        assertTrue(LocaleUtils.isSameLanguage(enLocale, Locale("en", "GB")))
        assertFalse(LocaleUtils.isSameLocale(enLocale, Locale("en", "GB")))
        assertTrue(LocaleUtils.isRtl(Locale("ar")))
        assertFalse(LocaleUtils.isRtl(enLocale))
        assertEquals("EN", LocaleUtils.getDisplayIndicator(enLocale))
    }

    @Test
    fun testCollectionUtils() {
        val a = intArrayOf(1, 2, 3)
        val b = intArrayOf(1, 2, 3)
        val c = intArrayOf(1, 2, 4)
        assertTrue(CollectionUtils.arrayEquals(a, b))
        assertFalse(CollectionUtils.arrayEquals(a, c))
        assertTrue(CollectionUtils.fastContains(a, 2))
        assertFalse(CollectionUtils.fastContains(a, 5))

        val sliced = CollectionUtils.subArray(a, 1, 3)
        assertArrayEquals(intArrayOf(2, 3), sliced)
    }

    @Test
    fun testUnicodeUtils() {
        assertTrue(UnicodeUtils.isAscii('a'.code))
        assertTrue(UnicodeUtils.isAsciiLetter('Z'.code))
        assertTrue(UnicodeUtils.isAsciiDigit('7'.code))
        assertEquals("cafe", UnicodeUtils.removeDiacritics("café"))
        assertEquals("Hello", UnicodeUtils.capitalize("hello", Locale.ENGLISH))

        val cps = UnicodeUtils.stringToCodePoints("VianBoard")
        assertEquals("VianBoard", UnicodeUtils.codePointsToString(cps))
    }

    @Test
    fun testCoordinateUtils() {
        val coords = CoordinateUtils.newCoordinateArray(3, 10, 20)
        assertEquals(6, coords.size)
        assertEquals(10, CoordinateUtils.xFromCoordinates(coords, 0))
        assertEquals(20, CoordinateUtils.yFromCoordinates(coords, 0))

        CoordinateUtils.setCoordinates(coords, 1, 50, 60)
        assertEquals(50, CoordinateUtils.xFromCoordinates(coords, 1))
        assertEquals(60, CoordinateUtils.yFromCoordinates(coords, 1))

        assertEquals(25, CoordinateUtils.calculateDistanceSquared(0, 0, 3, 4))
        assertEquals(5f, CoordinateUtils.calculateDistance(0, 0, 3, 4), 0.001f)
    }

    @Test
    fun testByteArrayDictBuffer() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78, 'A'.code.toByte(), 'B'.code.toByte(), 0x00)
        val buffer = ByteArrayDictBuffer(bytes)

        assertEquals(0x12, buffer.readUnsignedByte())
        assertEquals(0x3456, buffer.readUnsignedShort())
        assertEquals(0x78, buffer.readUnsignedByte())

        buffer.seek(4)
        val str = buffer.readNullTerminatedString()
        assertEquals("AB", str)
    }

    @Test
    fun testRingCharBuffer() {
        val ring = RingCharBuffer(5)
        assertTrue(ring.isEmpty())
        ring.push('h')
        ring.push('e')
        ring.push('l')
        ring.push('l')
        ring.push('o')
        assertTrue(ring.isFull())
        assertEquals("hello", ring.getLastCharacters(5))
        assertEquals("lo", ring.getLastCharacters(2))

        ring.push('!')
        assertEquals("ello!", ring.getLastCharacters(5))
        assertEquals('!', ring.pop())
        assertEquals("ello", ring.getLastCharacters(4))
    }

    @Test
    fun testSubtypeModel() {
        val subtype = Subtype.DEFAULT
        assertEquals("en_US", subtype.locale)
        assertEquals("qwerty", subtype.keyboardLayoutSet)
        assertEquals("EN", subtype.displayIndicator)
        assertFalse(subtype.isRtl)
    }
}
