package com.android.inputmethod.latin.utils

import com.android.inputmethod.latin.BinaryDictionary
import com.example.logger.LogKeeper
import com.example.logger.LogTags

/**
 * JNI wrapper for BinaryDictionaryUtils in libjni_latinime.so.
 */
object BinaryDictionaryUtils {
    init {
        BinaryDictionary.loadNativeLibraryIfNeeded()
    }

    fun calcNormalizedScore(before: IntArray, after: IntArray, score: Int): Float {
        if (!BinaryDictionary.sNativeLoaded) return 0f
        return try {
            calcNormalizedScoreNative(before, after, score)
        } catch (t: Throwable) {
            LogKeeper.logError(LogTags.JNI, "CALC_SCORE_ERR", "${t.message}")
            0f
        }
    }

    fun createEmptyDictFile(
        filePath: String,
        dictVersion: Long = 403L,
        locale: String,
        attributeKeys: Array<String> = arrayOf("locale", "dictionary"),
        attributeValues: Array<String> = arrayOf(locale, "user_history")
    ): Boolean {
        if (!BinaryDictionary.sNativeLoaded) return false
        return try {
            createEmptyDictFileNative(
                filePath,
                dictVersion,
                locale,
                attributeKeys,
                attributeValues
            )
        } catch (t: Throwable) {
            LogKeeper.logError(LogTags.JNI, "CREATE_EMPTY_DICT_ERR", "${t.message}")
            false
        }
    }

    @JvmStatic
    private external fun createEmptyDictFileNative(
        filePath: String,
        dictVersion: Long,
        locale: String,
        attributeKeyStringArray: Array<String>,
        attributeValueStringArray: Array<String>
    ): Boolean

    @JvmStatic
    private external fun calcNormalizedScoreNative(
        before: IntArray,
        after: IntArray,
        score: Int
    ): Float

    @JvmStatic
    private external fun setCurrentTimeForTestNative(currentTime: Int): Int
}
