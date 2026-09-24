package com.example.ime.security

import com.example.logger.LogKeeper
import com.example.logger.LogLevel

object VaultSessionManager {
    const val PRIVACY_SESSION_DEFAULT_MS = 5 * 60 * 1000L  // 5 minutes
    const val SECURITY_SESSION_DEFAULT_MS = 3 * 60 * 1000L // 3 minutes

    @Volatile
    private var privacyUnlockedUntilMs: Long = 0L

    @Volatile
    private var securityUnlockedUntilMs: Long = 0L

    fun isPrivacyUnlocked(): Boolean {
        return System.currentTimeMillis() < privacyUnlockedUntilMs
    }

    fun isSecurityUnlocked(): Boolean {
        return System.currentTimeMillis() < securityUnlockedUntilMs
    }

    fun unlockPrivacy(durationMs: Long = PRIVACY_SESSION_DEFAULT_MS) {
        privacyUnlockedUntilMs = System.currentTimeMillis() + durationMs
        LogKeeper.logEvent("VaultSession", "Privacy vault session unlocked for ${durationMs / 1000}s", LogLevel.INFO)
    }

    fun unlockSecurity(durationMs: Long = SECURITY_SESSION_DEFAULT_MS) {
        securityUnlockedUntilMs = System.currentTimeMillis() + durationMs
        LogKeeper.logEvent("VaultSession", "Security vault session unlocked for ${durationMs / 1000}s", LogLevel.INFO)
    }

    fun lockPrivacy() {
        privacyUnlockedUntilMs = 0L
        LogKeeper.logEvent("VaultSession", "Privacy vault session locked", LogLevel.INFO)
    }

    fun lockSecurity() {
        securityUnlockedUntilMs = 0L
        LogKeeper.logEvent("VaultSession", "Security vault session locked", LogLevel.INFO)
    }

    fun lockAll() {
        privacyUnlockedUntilMs = 0L
        securityUnlockedUntilMs = 0L
        LogKeeper.logEvent("VaultSession", "All vault sessions locked", LogLevel.INFO)
    }

    fun getRemainingSecuritySeconds(): Long {
        val rem = securityUnlockedUntilMs - System.currentTimeMillis()
        return if (rem > 0) rem / 1000 else 0L
    }

    fun getRemainingPrivacySeconds(): Long {
        val rem = privacyUnlockedUntilMs - System.currentTimeMillis()
        return if (rem > 0) rem / 1000 else 0L
    }
}
