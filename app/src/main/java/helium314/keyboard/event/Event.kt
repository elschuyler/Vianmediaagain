package helium314.keyboard.event

/**
 * Encapsulates a keyboard input event with spatial coordinates and key metadata.
 */
class Event(
    val keyCode: Int,
    val x: Int,
    val y: Int,
    val flags: Int = 0,
    val isKeyRepeat: Boolean = false
) {
    companion object {
        fun createSoftwareKeypressEvent(
            keyCode: Int,
            x: Int,
            y: Int,
            flags: Int,
            isKeyRepeat: Boolean
        ): Event {
            return Event(keyCode, x, y, flags, isKeyRepeat)
        }
    }
}
