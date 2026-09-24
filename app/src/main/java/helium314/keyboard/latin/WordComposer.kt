package helium314.keyboard.latin

import com.example.foundation.common.Constants
import com.example.foundation.common.UnicodeUtils
import com.example.foundation.utils.CoordinateUtils
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode

/**
 * Word composer managing composing spans, key codes, and coordinate tracking.
 */
class WordComposer {
    private val codePoints = ArrayList<Int>()
    private var coordinateArray: IntArray = CoordinateUtils.newCoordinateArray(0)

    val isComposingWord: Boolean
        get() = codePoints.isNotEmpty()

    val typedWord: String
        get() {
            if (codePoints.isEmpty()) return ""
            val cpArray = codePoints.toIntArray()
            return UnicodeUtils.codePointsToString(cpArray)
        }

    val coordinates: IntArray
        get() = coordinateArray

    fun getCodePoints(): IntArray = codePoints.toIntArray()

    fun size(): Int = codePoints.size

    fun reset() {
        codePoints.clear()
        coordinateArray = CoordinateUtils.newCoordinateArray(0)
    }

    fun setComposingWord(newCodePoints: IntArray, newCoordinates: IntArray) {
        codePoints.clear()
        for (cp in newCodePoints) {
            codePoints.add(cp)
        }
        coordinateArray = newCoordinates.clone()
    }

    fun processEvent(event: Event): Event {
        return event
    }

    fun applyProcessedEvent(event: Event) {
        val code = event.keyCode
        when (code) {
            KeyCode.DELETE -> {
                if (codePoints.isNotEmpty()) {
                    codePoints.removeAt(codePoints.size - 1)
                    val newCoords = CoordinateUtils.newCoordinateArray(codePoints.size)
                    CoordinateUtils.copyCoordinates(coordinateArray, newCoords, codePoints.size)
                    coordinateArray = newCoords
                }
            }
            else -> {
                if (code > 0) {
                    codePoints.add(code)
                    val newCoords = CoordinateUtils.newCoordinateArray(codePoints.size)
                    CoordinateUtils.copyCoordinates(coordinateArray, newCoords, codePoints.size - 1)
                    CoordinateUtils.setCoordinates(newCoords, codePoints.size - 1, event.x, event.y)
                    coordinateArray = newCoords
                }
            }
        }
    }
}
