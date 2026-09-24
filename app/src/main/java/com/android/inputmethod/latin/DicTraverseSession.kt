package com.android.inputmethod.latin

import com.example.logger.LogKeeper
import com.example.logger.LogTags
import java.util.Locale

/**
 * JNI wrapper for DicTraverseSession in libjni_latinime.so.
 */
class DicTraverseSession(locale: Locale, dictSize: Long) {
    var nativeSession: Long = 0L
        private set

    val isValid: Boolean
        get() = nativeSession != 0L

    init {
        BinaryDictionary.loadNativeLibraryIfNeeded()
        if (BinaryDictionary.sNativeLoaded) {
            try {
                nativeSession = setDicTraverseSessionNative(locale.toString(), dictSize)
            } catch (t: Throwable) {
                LogKeeper.logError(LogTags.JNI, "TRAVERSE_SESSION_ERR", "${t.message}")
                nativeSession = 0L
            }
        }
    }

    fun init(dictionary: Long, prevWord: IntArray?) {
        if (nativeSession == 0L || !BinaryDictionary.sNativeLoaded) return
        try {
            initDicTraverseSessionNative(
                nativeSession,
                dictionary,
                prevWord,
                prevWord?.size ?: 0
            )
        } catch (t: Throwable) {
            LogKeeper.logError(LogTags.JNI, "INIT_TRAVERSE_ERR", "${t.message}")
        }
    }

    fun close() {
        if (nativeSession != 0L && BinaryDictionary.sNativeLoaded) {
            try {
                releaseDicTraverseSessionNative(nativeSession)
            } catch (t: Throwable) {
                LogKeeper.logError(LogTags.JNI, "REL_TRAVERSE_ERR", "${t.message}")
            } finally {
                nativeSession = 0L
            }
        }
    }

    companion object {
        @JvmStatic
        private external fun setDicTraverseSessionNative(localeJStr: String, dictSize: Long): Long

        @JvmStatic
        private external fun initDicTraverseSessionNative(
            traverseSession: Long,
            dictionary: Long,
            previousWord: IntArray?,
            previousWordLength: Int
        )

        @JvmStatic
        private external fun releaseDicTraverseSessionNative(traverseSession: Long)
    }
}
