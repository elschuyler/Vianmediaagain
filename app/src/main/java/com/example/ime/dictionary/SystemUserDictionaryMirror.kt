package com.example.ime.dictionary

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.UserDictionary
import com.example.logger.LogKeeper
import com.example.logger.LogLevel
import java.util.Locale

/**
 * Handles selective synchronization of Partition 1 (NORMAL) entries to the system's
 * UserDictionary.Words content provider, so that normal personal words/shortcuts remain
 * visible to other apps, keyboards, and Android system settings.
 *
 * CRITICAL SECURITY INVARIANT:
 * Partition 2 (PRIVACY_VAULT) entries are STRICTLY AIR-GAPPED and are NEVER mirrored here.
 */
object SystemUserDictionaryMirror {

    private const val TAG = "SystemDictMirror"

    fun mirrorNormalEntry(context: Context, entry: PersonalDictionaryEntry) {
        if (entry.partition != DictionaryPartition.NORMAL) {
            LogKeeper.logEvent(TAG, "Security guard: blocked vault entry from system mirror", LogLevel.WARN)
            return
        }

        try {
            val contentResolver = context.contentResolver
            val locale = entry.locale ?: Locale.getDefault().toString()

            // Remove previous version if existing to avoid duplicates
            contentResolver.delete(
                UserDictionary.Words.CONTENT_URI,
                "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.SHORTCUT} = ?",
                arrayOf(entry.phrase, entry.shortcut)
            )

            val values = ContentValues().apply {
                put(UserDictionary.Words.WORD, entry.phrase)
                put(UserDictionary.Words.SHORTCUT, entry.shortcut)
                put(UserDictionary.Words.FREQUENCY, entry.weight.coerceIn(1, 250))
                put(UserDictionary.Words.LOCALE, locale)
                put(UserDictionary.Words.APP_ID, 0)
            }

            val resultUri: Uri? = contentResolver.insert(UserDictionary.Words.CONTENT_URI, values)
            LogKeeper.logEvent(TAG, "Mirrored normal entry '${entry.phrase}' (${entry.shortcut}) to system: $resultUri", LogLevel.INFO)
        } catch (t: Throwable) {
            // Some devices or ROMs might restrict direct ContentResolver access; fail gracefully
            LogKeeper.logError(TAG, "MIRROR_INSERT_ERR", t.message ?: "Failed to write to UserDictionary provider")
        }
    }

    fun removeNormalEntry(context: Context, shortcut: String, phrase: String) {
        try {
            val contentResolver = context.contentResolver
            val count = contentResolver.delete(
                UserDictionary.Words.CONTENT_URI,
                "${UserDictionary.Words.WORD} = ? AND ${UserDictionary.Words.SHORTCUT} = ?",
                arrayOf(phrase, shortcut)
            )
            LogKeeper.logEvent(TAG, "Removed normal entry from system mirror: $count row(s) deleted", LogLevel.INFO)
        } catch (t: Throwable) {
            LogKeeper.logError(TAG, "MIRROR_DELETE_ERR", t.message ?: "Failed to delete from UserDictionary provider")
        }
    }

    fun resyncAllNormalEntries(context: Context, normalEntries: List<PersonalDictionaryEntry>) {
        for (entry in normalEntries) {
            if (entry.partition == DictionaryPartition.NORMAL) {
                mirrorNormalEntry(context, entry)
            }
        }
    }
}
