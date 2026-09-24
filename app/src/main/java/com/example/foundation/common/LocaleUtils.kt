package com.example.foundation.common

import java.util.Locale

/**
 * Robust locale parsing, canonicalization, and comparison utilities for bilingual
 * and multilingual keyboard pipelines.
 */
object LocaleUtils {

    private val LOCALE_CACHE = HashMap<String, Locale>()

    /**
     * Constructs a [Locale] object from a BCP-47 tag or standard POSIX locale string
     * (e.g. "en", "en-US", "en_US", "fr", "fr_CA", "sr-Latn-RS").
     */
    fun constructLocale(localeString: String?): Locale {
        if (localeString.isNullOrBlank()) {
            return Locale.getDefault()
        }

        synchronized(LOCALE_CACHE) {
            LOCALE_CACHE[localeString]?.let { return it }

            val normalized = localeString.replace('-', '_')
            val parts = normalized.split('_')

            val locale = when (parts.size) {
                1 -> Locale(parts[0])
                2 -> Locale(parts[0], parts[1])
                3 -> Locale(parts[0], parts[1], parts[2])
                else -> {
                    // Attempt BCP-47 Language Tag parser
                    try {
                        Locale.forLanguageTag(localeString.replace('_', '-'))
                    } catch (_: Exception) {
                        Locale(parts[0], parts.getOrNull(1) ?: "")
                    }
                }
            }

            LOCALE_CACHE[localeString] = locale
            return locale
        }
    }

    /**
     * Returns true if both locales represent the same language code, regardless of country.
     */
    fun isSameLanguage(locale1: Locale?, locale2: Locale?): Boolean {
        if (locale1 == null || locale2 == null) return false
        return locale1.language.equals(locale2.language, ignoreCase = true)
    }

    /**
     * Returns true if both locales represent the exact same language and country.
     */
    fun isSameLocale(locale1: Locale?, locale2: Locale?): Boolean {
        if (locale1 === locale2) return true
        if (locale1 == null || locale2 == null) return false
        return locale1.language.equals(locale2.language, ignoreCase = true) &&
                locale1.country.equals(locale2.country, ignoreCase = true)
    }

    /**
     * Returns the human-readable display name for the locale, capitalized.
     */
    fun getLocaleDisplayName(locale: Locale): String {
        val display = locale.getDisplayName(locale)
        return display.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * Returns the 2-letter uppercase country or language indicator for keyboard spacebar display
     * (e.g., "EN", "FR", "ES").
     */
    fun getDisplayIndicator(locale: Locale): String {
        return locale.language.uppercase(Locale.ROOT)
    }

    /**
     * Checks if the given locale is typically written Right-to-Left (RTL).
     */
    fun isRtl(locale: Locale): Boolean {
        val lang = locale.language.lowercase(Locale.ROOT)
        return lang == "ar" || lang == "fa" || lang == "he" || lang == "iw" ||
                lang == "ur" || lang == "yi" || lang == "ps" || lang == "ug"
    }

    /**
     * Extracts language component safely.
     */
    fun getLanguage(localeString: String): String {
        return constructLocale(localeString).language
    }

    /**
     * Extracts country component safely.
     */
    fun getCountry(localeString: String): String {
        return constructLocale(localeString).country
    }
}
