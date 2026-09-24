package helium314.keyboard.latin

import java.util.ArrayList

/**
 * Encapsulates suggestion candidates produced by the dictionary and prediction engine.
 */
class SuggestedWords(
    val mSuggestedWordInfoList: ArrayList<SuggestedWordInfo> = ArrayList(),
    val mWillAutoCorrect: Boolean = false,
    val mIsPunctuationSuggestions: Boolean = false
) {
    val size: Int
        get() = mSuggestedWordInfoList.size

    fun size(): Int = mSuggestedWordInfoList.size

    val isEmpty: Boolean
        get() = mSuggestedWordInfoList.isEmpty()

    @JvmName("isEmptyMethod")
    fun isEmpty(): Boolean = mSuggestedWordInfoList.isEmpty()

    fun getWord(index: Int): String {
        if (index in mSuggestedWordInfoList.indices) {
            return mSuggestedWordInfoList[index].mWord
        }
        return ""
    }

    fun getInfo(index: Int): SuggestedWordInfo? {
        return mSuggestedWordInfoList.getOrNull(index)
    }

    class SuggestedWordInfo(
        val mWord: String,
        val mScore: Int = 0,
        val mKind: Int = KIND_CORRECTION
    ) {
        companion object {
            const val KIND_TYPED = 0
            const val KIND_CORRECTION = 1
            const val KIND_PREDICTION = 2
            const val KIND_COMPLETION = 3
        }
    }

    companion object {
        const val INDEX_OF_AUTO_CORRECTION = 0
        const val INPUT_STYLE_TYPING = 0
        const val INPUT_STYLE_PREDICTION = 1
        const val INPUT_STYLE_APPLICATION_SPECIFIED = 2
        const val INPUT_STYLE_RECORRECTION = 3

        val EMPTY = SuggestedWords(ArrayList(), false, false)

        fun create(words: List<String>, willAutoCorrect: Boolean = false): SuggestedWords {
            val list = ArrayList<SuggestedWordInfo>(words.size)
            for (w in words) {
                list.add(SuggestedWordInfo(w))
            }
            return SuggestedWords(list, willAutoCorrect, false)
        }

        fun createWithScores(
            wordScores: List<Pair<String, Int>>,
            willAutoCorrect: Boolean = false
        ): SuggestedWords {
            val list = ArrayList<SuggestedWordInfo>(wordScores.size)
            for ((w, score) in wordScores) {
                list.add(SuggestedWordInfo(w, score))
            }
            return SuggestedWords(list, willAutoCorrect, false)
        }
    }
}
