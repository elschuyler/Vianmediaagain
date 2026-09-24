package helium314.keyboard.keyboard

class Key(
    val label: String?,
    val icon: Any?,
    val code: Int,
    val outputText: String?,
    val hintLabel: String?,
    val actionFlags: Int,
    val moreKeys: Any?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val visualInsetsLeft: Int,
    val visualInsetsRight: Int
)
