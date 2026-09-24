package helium314.keyboard.latin

/**
 * Encapsulates preceding context words for n-gram language modeling.
 */
class NgramContext(val prevWordInfos: List<WordInfo> = emptyList()) {

    constructor(vararg wordInfos: WordInfo) : this(wordInfos.toList())

    val prevWordsCount: Int
        get() = prevWordInfos.size.coerceAtMost(MAX_PREV_WORD_COUNT)

    class WordInfo(
        val mWord: CharSequence?,
        val isBeginningOfSentence: Boolean = false
    ) {
        override fun toString(): String = mWord?.toString() ?: ""
    }

    /**
     * Extracts array of code points for preceding words (most recent first, up to 3).
     */
    fun extractPrevWordsCodePointArrays(): Array<IntArray> {
        val count = prevWordsCount
        if (count == 0) return Array(1) { IntArray(0) }
        val result = Array(count) { IntArray(0) }
        for (i in 0 until count) {
            // Index from most recent backward: index (count - 1 - i)
            val info = prevWordInfos[prevWordInfos.size - 1 - i]
            val word = info.mWord?.toString() ?: ""
            val codePoints = IntArray(word.codePointCount(0, word.length))
            var cpIdx = 0
            var charIdx = 0
            while (charIdx < word.length) {
                val cp = word.codePointAt(charIdx)
                codePoints[cpIdx++] = cp
                charIdx += Character.charCount(cp)
            }
            result[i] = codePoints
        }
        return result
    }

    /**
     * Extracts boolean array indicating whether each preceding word is beginning of sentence.
     */
    fun extractIsBeginningOfSentenceArray(): BooleanArray {
        val count = prevWordsCount
        if (count == 0) return BooleanArray(1) { false }
        val result = BooleanArray(count)
        for (i in 0 until count) {
            val info = prevWordInfos[prevWordInfos.size - 1 - i]
            result[i] = info.isBeginningOfSentence
        }
        return result
    }

    fun getNextNgramContext(word: CharSequence, isBeginningOfSentence: Boolean = false): NgramContext {
        val list = ArrayList(prevWordInfos)
        list.add(WordInfo(word, isBeginningOfSentence))
        while (list.size > MAX_PREV_WORD_COUNT) {
            list.removeAt(0)
        }
        return NgramContext(list)
    }

    companion object {
        const val MAX_PREV_WORD_COUNT = 3
        val EMPTY = NgramContext(emptyList())
        val EMPTY_PREV_WORDS_INFO = EMPTY

        fun getEmpty(): NgramContext = EMPTY

        fun fromWords(words: List<String>): NgramContext {
            val infos = words.takeLast(MAX_PREV_WORD_COUNT).map { WordInfo(it) }
            return NgramContext(infos)
        }
    }
}
