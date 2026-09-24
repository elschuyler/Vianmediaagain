package helium314.keyboard.keyboard

import com.android.inputmethod.keyboard.ProximityInfo
import helium314.keyboard.keyboard.internal.KeyboardParams

class Keyboard(val mParams: KeyboardParams) {
    val mId: KeyboardId?
        get() = mParams.mId

    val mOccupiedWidth: Int
        get() = mParams.mOccupiedWidth

    val mOccupiedHeight: Int
        get() = mParams.mOccupiedHeight

    val keys: List<Key>
        get() = mParams.mKeys

    var proximityInfo: ProximityInfo? = null
}

