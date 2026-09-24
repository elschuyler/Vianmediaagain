package com.example.foundation.utils

import com.example.foundation.common.Constants
import kotlin.math.sqrt

/**
 * High-performance coordinate array operations and spatial math for touch
 * tracking and spatial proximity scoring in the typing engine.
 */
object CoordinateUtils {

    const val INDEX_X = 0
    const val INDEX_Y = 1
    private const val COORDINATE_PAIR_SIZE = 2

    /**
     * Allocates a new packed coordinate array with [elementCount] (x, y) pairs.
     * Each pair is initialized with [defaultX] and [defaultY].
     */
    fun newCoordinateArray(
        elementCount: Int,
        defaultX: Int = Constants.NOT_A_COORDINATE,
        defaultY: Int = Constants.NOT_A_COORDINATE
    ): IntArray {
        val array = IntArray(elementCount * COORDINATE_PAIR_SIZE)
        for (i in 0 until elementCount) {
            val base = i * COORDINATE_PAIR_SIZE
            array[base + INDEX_X] = defaultX
            array[base + INDEX_Y] = defaultY
        }
        return array
    }

    /**
     * Extracts the X coordinate at [index] from a packed coordinate array.
     */
    fun xFromCoordinates(coordinates: IntArray, index: Int): Int {
        val pos = index * COORDINATE_PAIR_SIZE + INDEX_X
        return if (pos in coordinates.indices) coordinates[pos] else Constants.NOT_A_COORDINATE
    }

    /**
     * Extracts the Y coordinate at [index] from a packed coordinate array.
     */
    fun yFromCoordinates(coordinates: IntArray, index: Int): Int {
        val pos = index * COORDINATE_PAIR_SIZE + INDEX_Y
        return if (pos in coordinates.indices) coordinates[pos] else Constants.NOT_A_COORDINATE
    }

    /**
     * Sets the (x, y) coordinates at [index] in a packed coordinate array.
     */
    fun setCoordinates(coordinates: IntArray, index: Int, x: Int, y: Int) {
        val base = index * COORDINATE_PAIR_SIZE
        if (base + INDEX_Y < coordinates.size) {
            coordinates[base + INDEX_X] = x
            coordinates[base + INDEX_Y] = y
        }
    }

    /**
     * Copies packed coordinates between arrays without boxing.
     */
    fun copyCoordinates(source: IntArray, destination: IntArray, pairCount: Int) {
        val length = (pairCount * COORDINATE_PAIR_SIZE).coerceAtMost(source.size).coerceAtMost(destination.size)
        System.arraycopy(source, 0, destination, 0, length)
    }

    /**
     * Computes the squared Euclidean distance between two points (avoids square root).
     */
    fun calculateDistanceSquared(x1: Int, y1: Int, x2: Int, y2: Int): Int {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    /**
     * Computes the Euclidean distance between two points.
     */
    fun calculateDistance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        return sqrt(calculateDistanceSquared(x1, y1, x2, y2).toFloat())
    }
}
