package com.example

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object LogCatcher {
    private const val TAG = "LogCatcher"
    private const val PREFS_NAME = "log_catcher_prefs"
    private const val KEY_LOGGER_ENABLED = "logger_enabled"
    private const val LOGS_DIR_NAME = "logs"
    private const val ACTIVE_LOG_FILE_NAME = "active_session.log"
    const val MAX_LOG_SIZE_BYTES: Long = 2L * 1024 * 1024 // 2 MB

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var logFile: File

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Lightweight in-memory ring-buffer for fast UI loading
    private const val MAX_MEMORY_ENTRIES = 250
    private val memoryBuffer = ArrayDeque<LogEntry>(MAX_MEMORY_ENTRIES)
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    private val isDumping = AtomicBoolean(false)
    private val _logSizeBytes = MutableStateFlow(0L)
    val logSizeBytes: StateFlow<Long> = _logSizeBytes.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_LOGGER_ENABLED, true)

        val logsDir = File(appContext.filesDir, LOGS_DIR_NAME)
        if (!logsDir.exists()) logsDir.mkdirs()
        logFile = File(logsDir, ACTIVE_LOG_FILE_NAME)
        _logSizeBytes.value = if (logFile.exists()) logFile.length() else 0L

        // Check if existing file exceeds 2MB on cold launch
        if (_logSizeBytes.value >= MAX_LOG_SIZE_BYTES) {
            triggerAutoDump()
        }

        // Install UncaughtExceptionHandler for fatal crash capture
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logError("CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            dumpCrash(appContext, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        log("LogCatcher initialized. Storage ready.", "System")
    }

    private val URL_REGEX = Regex("""(?i)\b(?:https?|ftp|rtsp|smb|file)://[^\s"'<>]+""")
    private val CONTENT_URI_REGEX = Regex("""(?i)\bcontent://[^\s"'<>]+""")
    private val STORAGE_PATH_REGEX = Regex("""(?i)(?:/storage/emulated/\d+|/data/user/\d+|/data/data/[a-zA-Z0-9_.]+|/sdcard)/[^\s"'<>]+""")
    private val UNIX_PATH_REGEX = Regex("""(?i)/(?:[\w.\-]+/)+[\w.\-]+\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)\b""")
    private val FILE_NAME_REGEX = Regex("""(?i)\b[\w\-.]+\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)\b""")

    fun sanitize(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        var result = message
        result = URL_REGEX.replace(result, "[REDACTED_URL]")
        result = CONTENT_URI_REGEX.replace(result, "[REDACTED_URI]")
        result = STORAGE_PATH_REGEX.replace(result, "[REDACTED_PATH]")
        result = UNIX_PATH_REGEX.replace(result, "[REDACTED_PATH]")
        result = FILE_NAME_REGEX.replace(result, "[REDACTED_FILE]")
        return result
    }

    fun toggleLogger() {
        val newState = !_isEnabled.value
        _isEnabled.value = newState
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_LOGGER_ENABLED, newState).apply()
        }
        log("Logger state changed to: $newState", "System")
    }

    fun log(message: String, tag: String = "App") {
        if (!_isEnabled.value) return
        val sanitizedMessage = sanitize(message)
        val entry = LogEntry(System.currentTimeMillis(), false, tag, sanitizedMessage)
        Log.d(TAG, entry.formattedString)
        processEntry(entry)
    }

    fun logWarn(tag: String, message: String) {
        if (!_isEnabled.value) return
        val sanitizedMessage = sanitize(message)
        val entry = LogEntry(System.currentTimeMillis(), false, "WARN/$tag", sanitizedMessage)
        Log.w(TAG, entry.formattedString)
        processEntry(entry)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (!_isEnabled.value) return
        val sanitizedMessage = sanitize(message)
        val stackTrace = throwable?.let { sanitize(Log.getStackTraceString(it)) }
        val entry = LogEntry(System.currentTimeMillis(), true, tag, sanitizedMessage, stackTrace)
        Log.e(TAG, entry.formattedString)
        processEntry(entry)
    }

    private fun processEntry(entry: LogEntry) {
        // Update in-memory ring buffer for instant UI consumption
        synchronized(memoryBuffer) {
            if (memoryBuffer.size >= MAX_MEMORY_ENTRIES) {
                memoryBuffer.removeFirst()
            }
            memoryBuffer.addLast(entry)
            _recentLogs.value = memoryBuffer.toList()
        }

        // Asynchronously persist to file & evaluate 2MB threshold
        scope.launch {
            try {
                if (!::logFile.isInitialized) return@launch
                val formattedLine = entry.formattedString + "\n"
                val bytes = formattedLine.toByteArray(Charsets.UTF_8)
                
                FileOutputStream(logFile, true).use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
                
                val currentSize = logFile.length()
                _logSizeBytes.value = currentSize

                if (currentSize >= MAX_LOG_SIZE_BYTES) {
                    triggerAutoDump()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to active log file", e)
            }
        }
    }

    private fun triggerAutoDump() {
        if (!isDumping.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (!::logFile.isInitialized || !logFile.exists() || logFile.length() == 0L) {
                    isDumping.set(false)
                    return@launch
                }
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                val dumpFileName = "Vianbrplay_log_auto_$timestamp.txt"
                val content = logFile.readText(Charsets.UTF_8)
                
                val success = writeToDownloads(appContext, dumpFileName, content)
                if (success) {
                    // Truncate active log file back to 0 bytes
                    try {
                        FileOutputStream(logFile, false).close()
                        _logSizeBytes.value = 0L
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing log file after auto-dump", e)
                    }

                    synchronized(memoryBuffer) {
                        memoryBuffer.clear()
                    }
                    val notification = LogEntry(
                        System.currentTimeMillis(),
                        false,
                        "System",
                        "Log reached 2MB threshold. Auto-dumped to Downloads folder: $dumpFileName"
                    )
                    processEntry(notification)
                    Log.d(TAG, "Auto-dump complete: $dumpFileName ($currentLogSizeFormatted)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-dump", e)
            } finally {
                isDumping.set(false)
            }
        }
    }

    val currentLogSizeFormatted: String
        get() {
            val bytes = _logSizeBytes.value
            return when {
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes.toFloat() / (1024 * 1024))
                bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toFloat() / 1024)
                else -> "$bytes B"
            }
        }

    fun getLogSizeBytes(): Long = _logSizeBytes.value

    suspend fun loadAllLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
        if (!::logFile.isInitialized || !logFile.exists()) {
            return@withContext recentLogs.value
        }
        try {
            val lines = logFile.readLines(Charsets.UTF_8)
            val parsedList = mutableListOf<LogEntry>()
            var currentEntry: LogEntry? = null
            var stackTraceBuilder = StringBuilder()

            for (line in lines) {
                // Check if line starts with formatted timestamp [HH:mm:ss.SSS]
                if (line.startsWith("[") && line.length > 14 && line[13] == ']') {
                    if (currentEntry != null) {
                        val st = if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.toString().trimEnd() else null
                        parsedList.add(currentEntry.copy(stackTrace = st))
                        stackTraceBuilder.clear()
                    }
                    currentEntry = parseLogLine(line)
                } else if (currentEntry != null) {
                    // Continuation of stacktrace or multiline message
                    if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.append("\n")
                    stackTraceBuilder.append(line)
                }
            }
            if (currentEntry != null) {
                val st = if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.toString().trimEnd() else null
                parsedList.add(currentEntry.copy(stackTrace = st))
            }
            if (parsedList.isNotEmpty()) parsedList else recentLogs.value
        } catch (e: Exception) {
            Log.e(TAG, "Error loading full log from disk, falling back to memory buffer", e)
            recentLogs.value
        }
    }

    private fun parseLogLine(line: String): LogEntry {
        try {
            val timeEnd = line.indexOf(']')
            val timeStr = if (timeEnd > 1) line.substring(1, timeEnd) else ""
            val remainder = line.substring(timeEnd + 1).trimStart()
            val isError = remainder.startsWith("ERROR ")
            val tagStart = remainder.indexOf('[')
            val tagEnd = remainder.indexOf(']')
            val tag = if (tagStart >= 0 && tagEnd > tagStart) remainder.substring(tagStart + 1, tagEnd) else "App"
            val colonIdx = remainder.indexOf(':', tagEnd.coerceAtLeast(0))
            val message = if (colonIdx >= 0 && colonIdx < remainder.length - 1) remainder.substring(colonIdx + 1).trimStart() else remainder
            
            return LogEntry(
                timestampMs = System.currentTimeMillis(),
                isError = isError,
                tag = tag,
                message = message
            )
        } catch (e: Exception) {
            return LogEntry(System.currentTimeMillis(), false, "App", line)
        }
    }

    fun clearLogs() {
        scope.launch {
            try {
                if (::logFile.isInitialized && logFile.exists()) {
                    FileOutputStream(logFile, false).close()
                    _logSizeBytes.value = 0L
                }
                synchronized(memoryBuffer) {
                    memoryBuffer.clear()
                    _recentLogs.value = emptyList()
                }
                log("Log buffer cleared", "System")
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing logs", e)
            }
        }
    }

    fun dumpCurrentLogs(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!_isEnabled.value) {
            onComplete?.invoke(false, "Logger is disabled")
            return
        }
        scope.launch {
            try {
                val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
                val fileName = "Vianbrplay_logs_$dateStr.txt"
                
                val logsContent = if (::logFile.isInitialized && logFile.exists() && logFile.length() > 0) {
                    logFile.readText(Charsets.UTF_8)
                } else {
                    _recentLogs.value.joinToString("\n") { it.formattedString }
                }

                val exportHeader = """
                    ==============================
                    Vianbrplay Log Export - $dateStr
                    Size: ${currentLogSizeFormatted}
                    ==============================
                    
                """.trimIndent() + "\n"

                val success = writeToDownloads(context, fileName, exportHeader + logsContent)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success, fileName)
                }
                log("Manual log dump to Downloads: success=$success ($fileName)", "System")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write manual log dump", e)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun dumpCrash(context: Context, throwable: Throwable) {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            val fileName = "Vianbrplay_crash_$dateStr.txt"
            val recent = _recentLogs.value.joinToString("\n") { it.formattedString }
            val sanitizedMsg = sanitize(throwable.message ?: "No message")
            val sanitizedStack = sanitize(Log.getStackTraceString(throwable))
            val crashData = """
                ==============================
                Vianbrplay Crash Dump - $dateStr
                Message: $sanitizedMsg
                ==============================
                Stacktrace:
                $sanitizedStack
                ==============================
                Recent Logs (${_recentLogs.value.size} entries):
                $recent
                ==============================
            """.trimIndent()
            
            writeToDownloads(context, fileName, crashData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash dump", e)
        }
    }

    private fun writeToDownloads(context: Context, fileName: String, content: String): Boolean {
        var success = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    }
                    success = true
                    Log.d(TAG, "Successfully written to MediaStore.Downloads: $fileName")
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.writeText(content)
                success = true
                Log.d(TAG, "Successfully written to external storage Downloads: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to primary Downloads: ${e.message}", e)
        }

        // Failsafe backup to app-specific external files dir
        try {
            val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDownloads != null) {
                if (!appDownloads.exists()) appDownloads.mkdirs()
                val backupFile = File(appDownloads, fileName)
                backupFile.writeText(content)
                Log.d(TAG, "Backup written to app external downloads: ${backupFile.absolutePath}")
                success = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to backup app downloads: ${e.message}", e)
        }

        return success
    }
}
