package com.example

import android.app.Application
import com.example.logger.LogKeeper

class VianApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogKeeper.initialize(this)
        LogKeeper.logComponentStart("VianApplication")

        // Uncaught exception crash catcher
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                LogKeeper.logError(
                    component = "UncaughtCrashHandler",
                    errorCode = "FATAL_CRASH",
                    errorDetails = "${throwable.javaClass.name}: ${throwable.message}\n$stackTrace"
                )
                // Instantly persist and dump to device Download/ folder
                LogKeeper.dropCurrentLogToDownloads(reason = "CRASH_DUMP")
            } catch (e: Exception) {
                // Safety net in crash handler
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LogKeeper.logEvent("MemoryTrim", "Trim level: $level")
        com.example.ime.engine.TextEngineBridge.activeInstance?.onTrimMemory(level)
    }
}
