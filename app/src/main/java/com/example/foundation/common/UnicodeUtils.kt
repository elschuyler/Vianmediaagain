package com.example.foundation.common

import java.text.Normalizer
import java.util.Locale

/**
 * Unicode and code-point manipulation utilities for IME word composition,
 * diacritic handling, and case conversions.
 */
object UnicodeUtils {

    /**
     * Checks if a code point is a standard 7-bit ASCII character.
     */
    fun isAscii(codePoint: Int): Boolean {
        return codePoint in 0..127
    }

    /**
     * Checks if a code point is an ASCII letter [a-zA-Z].
     */
    fun isAsciiLetter(codePoint: Int): Boolean {
        return (codePoint in 65..90) || (codePoint in 97..122)
    }

    /**
     * Checks if a code point is an ASCII digit [0-9].
     */
    fun isAsciiDigit(codePoint: Int): Boolean {
        return codePoint in 48..57
    }

    /**
     * Checks if a code point is a Unicode letter.
     */
    fun isLetter(codePoint: Int): Boolean {
        return Character.isLetter(codePoint)
    }

    /**
     * Checks if a code point is a Unicode digit.
     */
    fun isDigit(codePoint: Int): Boolean {
        return Character.isDigit(codePoint)
    }

    /**
     * Checks if a code point is whitespace.
     */
    fun isWhitespace(codePoint: Int): Boolean {
        return Character.isWhitespace(codePoint) || codePoint == Constants.CODE_SPACE
    }

    /**
     * Checks if a code point is standard punctuation.
     */
    fun isPunctuation(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()
    }

    /**
     * Converts a code point to uppercase.
     */
    fun toUpperCase(codePoint: Int): Int {
        return Character.toUpperCase(codePoint)
    }

    /**
     * Converts a code point to lowercase.
     */
    fun toLowerCase(codePoint: Int): Int {
        return Character.toLowerCase(codePoint)
    }

    /**
     * Converts a code point to titlecase.
     */
    fun toTitleCase(codePoint: Int): Int {
        return Character.toTitleCase(codePoint)
    }

    /**
     * Removes diacritical marks (accents) from a text string (e.g., "café" -> "cafe").
     */
    fun removeDiacritics(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = Regex("\\p{InCombiningDiacriticalMarks}+")
        return pattern.replace(normalized, "")
    }

    /**
     * Capitalizes a string respecting the provided locale.
     */
    fun capitalize(text: String, locale: Locale): String {
        if (text.isEmpty()) return text
        return text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    }

    /**
     * Counts the number of Unicode code points in a character sequence.
     */
    fun codePointCount(text: CharSequence): Int {
        return Character.codePointCount(text, 0, text.length)
    }

    /**
     * Extracts the code point at a given character index.
     */
    fun codePointAt(text: CharSequence, index: Int): Int {
        return Character.codePointAt(text, index)
    }

    /**
     * Converts an array of code points into a standard String.
     */
    fun codePointsToString(codePoints: IntArray, offset: Int = 0, length: Int = codePoints.size - offset): String {
        if (length <= 0) return ""
        val sb = StringBuilder(length)
        val end = (offset + length).coerceAtMost(codePoints.size)
        for (i in offset until end) {
            val cp = codePoints[i]
            if (cp >= 0) {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }

    /**
     * Converts a string into an array of primitive Unicode code points.
     */
    fun stringToCodePoints(text: String): IntArray {
        val count = Character.codePointCount(text, 0, text.length)
        val codePoints = IntArray(count)
        var charIndex = 0
        var cpIndex = 0
        while (charIndex < text.length) {
            val cp = Character.codePointAt(text, charIndex)
            codePoints[cpIndex++] = cp
            charIndex += Character.charCount(cp)
        }
        return codePoints
    }
}
