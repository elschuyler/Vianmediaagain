package helium314.keyboard.keyboard.internal

import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId

class KeyboardParams {
    var mId: KeyboardId? = null
    var mOccupiedWidth: Int = 0
    var mOccupiedHeight: Int = 0
    var mBaseWidth: Int = 0
    var mBaseHeight: Int = 0
    var mProximityCharsCorrectionEnabled: Boolean = false
    val mKeys: ArrayList<Key> = ArrayList()

    fun onAddKey(key: Key) {
        mKeys.add(key)
    }
}
