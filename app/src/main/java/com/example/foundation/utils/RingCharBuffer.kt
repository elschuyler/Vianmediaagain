package com.example.foundation.utils

/**
 * High-performance circular character buffer for tracking composing spans,
 * recent keystrokes, and cursor contexts with zero heap allocations during typing.
 */
class RingCharBuffer(val capacity: Int) {
    private val buffer = CharArray(capacity)
    private var head: Int = 0
    private var count: Int = 0

    val size: Int
        get() = count

    fun isEmpty(): Boolean = count == 0

    fun isFull(): Boolean = count == capacity

    /**
     * Appends a character to the ring buffer, overwriting the oldest character if full.
     */
    fun push(c: Char) {
        buffer[head] = c
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    /**
     * Removes and returns the most recently pushed character, or null if empty.
     */
    fun pop(): Char? {
        if (count == 0) return null
        head = (head - 1 + capacity) % capacity
        count--
        return buffer[head]
    }

    /**
     * Peeks at the most recently pushed character without removing it.
     */
    fun peek(): Char? {
        if (count == 0) return null
        val idx = (head - 1 + capacity) % capacity
        return buffer[idx]
    }

    /**
     * Retrieves up to [requestedCount] recent characters as a String in chronological order.
     */
    fun getLastCharacters(requestedCount: Int): String {
        val n = requestedCount.coerceIn(0, count)
        if (n == 0) return ""

        val chars = CharArray(n)
        val startIdx = (head - n + capacity) % capacity
        for (i in 0 until n) {
            chars[i] = buffer[(startIdx + i) % capacity]
        }
        return String(chars)
    }

    /**
     * Clears all contents from the ring buffer.
     */
    fun clear() {
        head = 0
        count = 0
    }
}
