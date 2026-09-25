package com.example

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class LogEntry(
    val timestampMs: Long,
    val isError: Boolean,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
    
    val formattedString: String
        get() {
            var formatted = "[$formattedTime] "
            if (isError) formatted += "ERROR [$tag]: " else formatted += "[$tag] "
            formatted += message
            if (stackTrace != null) {
                formatted += "\n$stackTrace"
            }
            return formatted
        }
}

object LogKeeper {
    val isEnabled: StateFlow<Boolean>
        get() = LogCatcher.isEnabled

    val logs: StateFlow<List<LogEntry>>
        get() = LogCatcher.recentLogs

    val logSizeBytes: StateFlow<Long>
        get() = LogCatcher.logSizeBytes

    val currentLogSizeFormatted: String
        get() = LogCatcher.currentLogSizeFormatted

    fun init(context: Context) {
        LogCatcher.init(context)
    }

    fun toggleLogger() {
        LogCatcher.toggleLogger()
    }

    fun log(message: String, tag: String = "App") {
        LogCatcher.log(message, tag)
    }

    fun logWarn(tag: String, message: String) {
        LogCatcher.logWarn(tag, message)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        LogCatcher.logError(tag, message, throwable)
    }

    fun sanitize(message: String?): String {
        return LogCatcher.sanitize(message)
    }

    fun dumpCurrentLogs(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        LogCatcher.dumpCurrentLogs(context, onComplete)
    }

    fun clearLogs() {
        LogCatcher.clearLogs()
    }

    suspend fun loadAllLogs(): List<LogEntry> {
        return LogCatcher.loadAllLogs()
    }

    fun getLogSizeBytes(): Long {
        return LogCatcher.getLogSizeBytes()
    }
}

