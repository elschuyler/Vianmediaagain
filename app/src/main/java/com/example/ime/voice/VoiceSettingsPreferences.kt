package com.example.ime.voice

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized settings preferences for Voice Input.
 * Configures inference flags, decoding options, and FUTO-aligned user preferences:
 * - Suppress non-speech annotations ([cough], [music], etc.)
 * - Verbose Mode
 * - Beam Search vs Greedy Search
 * - Allow Undertrained Languages
 * - Legacy 30-Second Limit
 * - Haptic Feedback
 */
object VoiceSettingsPreferences {
    private const val PREFS_NAME = "vian_voice_prefs"

    const val KEY_SUPPRESS_NON_SPEECH = "voice_suppress_non_speech"
    const val KEY_VERBOSE_MODE = "voice_verbose_mode"
    const val KEY_USE_BEAM_SEARCH = "voice_use_beam_search"
    const val KEY_ALLOW_UNDERTRAINED_LANGUAGES = "voice_allow_undertrained_languages"
    const val KEY_LEGACY_30S_LIMIT = "voice_legacy_30s_limit"
    const val KEY_HAPTIC_FEEDBACK = "voice_haptic_feedback"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isSuppressNonSpeechEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SUPPRESS_NON_SPEECH, true)

    fun setSuppressNonSpeechEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_SUPPRESS_NON_SPEECH, enabled).apply()

    fun isVerboseModeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_VERBOSE_MODE, false)

    fun setVerboseModeEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_VERBOSE_MODE, enabled).apply()

    fun isUseBeamSearchEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_USE_BEAM_SEARCH, true)

    fun setUseBeamSearchEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_USE_BEAM_SEARCH, enabled).apply()

    fun isAllowUndertrainedLanguagesEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ALLOW_UNDERTRAINED_LANGUAGES, false)

    fun setAllowUndertrainedLanguagesEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_ALLOW_UNDERTRAINED_LANGUAGES, enabled).apply()

    fun isLegacy30sLimitEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LEGACY_30S_LIMIT, false)

    fun setLegacy30sLimitEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_LEGACY_30S_LIMIT, enabled).apply()

    fun isHapticFeedbackEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, true)

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
}
