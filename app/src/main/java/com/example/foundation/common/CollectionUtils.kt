package com.example.foundation.common

/**
 * High-performance array and collection utilities designed for zero-allocation
 * hot-paths in the keyboard rendering and dictionary lookup loops.
 */
object CollectionUtils {

    /**
     * Fast comparison of two primitive IntArrays without boxing.
     */
    fun arrayEquals(a: IntArray?, b: IntArray?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    /**
     * Fast value search in an IntArray without boxing or Iterator allocations.
     */
    fun fastContains(array: IntArray?, target: Int): Boolean {
        if (array == null) return false
        for (v in array) {
            if (v == target) return true
        }
        return false
    }

    /**
     * Slices an IntArray cleanly into a newly allocated segment.
     */
    fun subArray(src: IntArray, start: Int, end: Int): IntArray {
        val length = (end - start).coerceAtLeast(0)
        val dst = IntArray(length)
        System.arraycopy(src, start, dst, 0, length)
        return dst
    }

    /**
     * Safely filters non-null elements into a clean non-null List.
     */
    fun <T : Any> nonNullList(elements: List<T?>): List<T> {
        val result = ArrayList<T>(elements.size)
        for (item in elements) {
            if (item != null) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * Inserts an integer into a sorted array, maintaining uniqueness and order.
     */
    fun insertSortedUnique(array: IntArray, value: Int): IntArray {
        val index = array.binarySearch(value)
        if (index >= 0) return array // Value already present

        val insertIndex = -(index + 1)
        val newArray = IntArray(array.size + 1)
        System.arraycopy(array, 0, newArray, 0, insertIndex)
        newArray[insertIndex] = value
        System.arraycopy(array, insertIndex, newArray, insertIndex + 1, array.size - insertIndex)
        return newArray
    }
}
