package shura.media

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object LogCatcher {
    private const val TAG = "LogCatcher"
    private const val PREFS_NAME = "log_catcher_prefs"
    private const val KEY_LOGGER_ENABLED = "logger_enabled"
    private const val KEY_AUTO_DUMP_RECORDS = "auto_dump_records"
    private const val LOGS_DIR_NAME = "logs"
    private const val ACTIVE_LOG_FILE_NAME = "active_session.log"
    const val MAX_LOG_SIZE_BYTES: Long = 2L * 1024 * 1024 // 2 MB

    private const val MAX_MEMORY_ENTRIES = 250
    private const val DISK_CHANNEL_CAPACITY = 512
    private const val DISK_BATCH_MAX = 64
    private const val COALESCE_WINDOW_MS = 2000L
    private const val UI_THROTTLE_MS = 250L
    private const val AUTO_DUMP_COOLDOWN_MS = 10_000L
    private const val COMPACT_CONTEXT_RADIUS = 15
    private const val COMPACT_RECENT_TAIL_BLOCKS = 250
    private const val ACTIVE_RETENTION_ERROR_TAIL_BLOCKS = 60
    private const val MAX_AUTO_DUMP_FILES = 3
    private const val HARD_CAP_AUTO_DUMP_FILES = 6

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val diskDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val diskScope = CoroutineScope(diskDispatcher + SupervisorJob())
    private val fileLock = ReentrantLock()
    private val bufferLock = Any()
    private val prefsLock = Any()
    private val exportDateFormatThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    }
    private fun formatExportDate(millis: Long): String = exportDateFormatThreadLocal.get()!!.format(Date(millis))

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var logFile: File

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val memoryBuffer = ArrayDeque<LogEntry>(MAX_MEMORY_ENTRIES)
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    private val droppedFromChannel = AtomicInteger(0)
    private val logChannel = Channel<LogEntry>(
        capacity = DISK_CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { droppedFromChannel.incrementAndGet() }
    )
    private val writerStarted = AtomicBoolean(false)

    // Consecutive duplicate coalescing state (guarded by bufferLock)
    private var lastCoalesceEntry: LogEntry? = null
    private var lastCoalesceStartMs: Long = 0L
    private var lastCoalesceCount: Int = 0
    private var lastUiEmitMs: Long = 0L

    private val isDumping = AtomicBoolean(false)
    @Volatile
    private var lastAutoDumpAtMs: Long = 0L
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

        startWriterLoopIfNeeded()

        if (_logSizeBytes.value >= MAX_LOG_SIZE_BYTES) {
            triggerAutoDump()
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logError("CRASH", "Uncaught exception in thread ${thread.name}", throwable)
                dumpCrash(appContext, throwable)
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        log("LogCatcher initialized. Storage ready.", "System")
    }

    private fun startWriterLoopIfNeeded() {
        if (!writerStarted.compareAndSet(false, true)) return
        diskScope.launch {
            val batch = ArrayList<LogEntry>(DISK_BATCH_MAX)
            while (true) {
                try {
                    val first = logChannel.receive()
                    batch.clear()
                    batch.add(first)
                    while (batch.size < DISK_BATCH_MAX) {
                        val next = logChannel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }
                    val dropped = droppedFromChannel.getAndSet(0)
                    writeBatchToDisk(batch, dropped)
                } catch (e: Exception) {
                    Log.e(TAG, "Writer loop error", e)
                }
            }
        }
    }

    private fun writeBatchToDisk(batch: List<LogEntry>, droppedCount: Int) {
        fileLock.withLock {
            try {
                if (!::logFile.isInitialized) return
                BufferedWriter(OutputStreamWriter(FileOutputStream(logFile, true), Charsets.UTF_8)).use { writer ->
                    if (droppedCount > 0) {
                        val note = LogEntry(
                            System.currentTimeMillis(),
                            false,
                            "System",
                            "Coalesced/dropped $droppedCount burst log entries to prevent memory overflow"
                        )
                        writer.write(note.formattedString)
                        writer.newLine()
                    }
                    for (entry in batch) {
                        writer.write(entry.formattedString)
                        writer.newLine()
                    }
                    writer.flush()
                }
                val currentSize = logFile.length()
                _logSizeBytes.value = currentSize
                if (currentSize >= MAX_LOG_SIZE_BYTES) {
                    performAutoDumpLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing batch to active log file", e)
            }
        }
    }

    private val URL_REGEX = Regex("""(?i)\b(?:https?|ftp|rtsp|smb|file)://[^\s"'<>]+""")
    private val CONTENT_URI_REGEX = Regex("""(?i)\bcontent://[^\s"'<>]+""")
    private val SPACED_STORAGE_PATH_REGEX = Regex("""(?i)(?:/storage|/data|/sdcard|/mnt)/[^\r\n"'<>]*?\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)(?=\s|$|["'<>),;:])""")
    private val STORAGE_PATH_REGEX = Regex("""(?i)(?:/storage/[^\s"'<>]+|/data/[^\s"'<>]+|/sdcard/[^\s"'<>]+)""")
    private val QUOTED_FILE_REGEX = Regex("""(?i)(?:['"])[^'"]+?\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)(?:['"])""")
    private val UNIX_PATH_REGEX = Regex("""(?i)/(?:[^\s"'<>\/]+/)+[^\s"'<>\/]+?\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)\b""")
    private val FILE_NAME_REGEX = Regex("""(?i)\b[^\\/:*?"<>|\r\n\t]+?\.(?:mp4|mkv|mov|avi|webm|flv|wmv|3gp|ts|m4s|mp3|m4a|aac|wav|flac|ogg|opus|wma|jpg|jpeg|png|webp|gif|bmp|svg|srt|vtt|ass|sub|txt|json|pdf|log)\b""")

    fun sanitize(message: String?): String {
        if (message.isNullOrEmpty()) return ""
        val hasScheme = message.contains("://")
        val hasSlash = message.indexOf('/') >= 0
        val hasDot = message.indexOf('.') >= 0
        if (!hasScheme && !hasSlash && !hasDot) return message

        var result = message
        if (hasScheme) {
            result = URL_REGEX.replace(result, "[REDACTED_URL]")
            result = CONTENT_URI_REGEX.replace(result, "[REDACTED_URI]")
        }
        if (hasSlash) {
            result = SPACED_STORAGE_PATH_REGEX.replace(result, "[REDACTED_PATH]")
            result = STORAGE_PATH_REGEX.replace(result, "[REDACTED_PATH]")
            result = UNIX_PATH_REGEX.replace(result, "[REDACTED_PATH]")
        }
        if (hasDot) {
            result = QUOTED_FILE_REGEX.replace(result, "[REDACTED_FILE]")
            result = FILE_NAME_REGEX.replace(result, "[REDACTED_FILE]")
        }
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
        var repeatSummaryToFlush: LogEntry? = null
        var shouldSendEntry = true

        synchronized(bufferLock) {
            val prev = lastCoalesceEntry
            val sameAsPrev = prev != null &&
                !entry.isError &&
                entry.stackTrace == null &&
                !prev.isError &&
                prev.stackTrace == null &&
                entry.tag == prev.tag &&
                entry.message == prev.message &&
                (entry.timestampMs - lastCoalesceStartMs) <= COALESCE_WINDOW_MS

            if (sameAsPrev) {
                lastCoalesceCount++
                shouldSendEntry = false
                if (memoryBuffer.isNotEmpty()) {
                    memoryBuffer.removeLast()
                }
                val updatedTail = entry.copy(message = "${entry.message} (x${lastCoalesceCount + 1})")
                memoryBuffer.addLast(updatedTail)
                if (entry.timestampMs - lastUiEmitMs >= UI_THROTTLE_MS) {
                    lastUiEmitMs = entry.timestampMs
                    _recentLogs.value = memoryBuffer.toList()
                }
            } else {
                if (lastCoalesceCount > 0 && prev != null) {
                    repeatSummaryToFlush = LogEntry(
                        entry.timestampMs,
                        false,
                        prev.tag,
                        "${prev.message} (repeated ${lastCoalesceCount}x more)"
                    )
                }
                lastCoalesceEntry = entry
                lastCoalesceStartMs = entry.timestampMs
                lastCoalesceCount = 0

                if (memoryBuffer.size >= MAX_MEMORY_ENTRIES) {
                    memoryBuffer.removeFirst()
                }
                memoryBuffer.addLast(entry)
                lastUiEmitMs = entry.timestampMs
                _recentLogs.value = memoryBuffer.toList()
            }
        }

        repeatSummaryToFlush?.let { logChannel.trySend(it) }
        if (shouldSendEntry) {
            logChannel.trySend(entry)
        }
    }

    private fun triggerAutoDump() {
        diskScope.launch {
            fileLock.withLock {
                performAutoDumpLocked()
            }
        }
    }

    private data class LogBlock(
        val isError: Boolean,
        val text: String
    )

    private fun parseBlocksFromSequence(lines: Sequence<String>): List<LogBlock> {
        val blocks = ArrayList<LogBlock>(256)
        val builder = StringBuilder()
        var currentHeaderLine: String? = null

        fun flushCurrent() {
            val hdr = currentHeaderLine ?: return
            val fullText = builder.toString().trimEnd()
            val hasErr = hdr.contains(" ERROR [") || hdr.contains("[CRASH]") || hdr.startsWith("[") && hdr.contains("] ERROR ")
            blocks.add(LogBlock(isError = hasErr, text = fullText))
            builder.clear()
            currentHeaderLine = null
        }

        for (line in lines) {
            if (line.startsWith("[") && line.length > 14 && line[13] == ']') {
                flushCurrent()
                currentHeaderLine = line
                builder.append(line)
            } else if (currentHeaderLine != null) {
                builder.append('\n').append(line)
            } else if (line.isNotBlank()) {
                blocks.add(LogBlock(isError = line.contains("ERROR") || line.contains("CRASH"), text = line))
            }
        }
        flushCurrent()
        return blocks
    }

    private data class CompactionResult(
        val text: String,
        val hasErrors: Boolean,
        val keptBlocks: List<LogBlock>
    )

    private fun compactLogBlocks(blocks: List<LogBlock>, tailCount: Int = COMPACT_RECENT_TAIL_BLOCKS): CompactionResult {
        if (blocks.isEmpty()) {
            return CompactionResult(text = "", hasErrors = false, keptBlocks = emptyList())
        }

        val keep = BooleanArray(blocks.size)
        var anyErrors = false
        for (i in blocks.indices) {
            if (blocks[i].isError) {
                anyErrors = true
                val start = (i - COMPACT_CONTEXT_RADIUS).coerceAtLeast(0)
                val end = (i + COMPACT_CONTEXT_RADIUS).coerceAtMost(blocks.lastIndex)
                for (j in start..end) {
                    keep[j] = true
                }
            }
        }
        val tailStart = (blocks.size - tailCount).coerceAtLeast(0)
        for (j in tailStart..blocks.lastIndex) {
            keep[j] = true
        }

        val out = StringBuilder()
        val keptBlocks = ArrayList<LogBlock>()
        var skippedRun = 0
        for (i in blocks.indices) {
            if (keep[i]) {
                if (skippedRun > 0) {
                    out.append("--- [Compacted ").append(skippedRun).append(" older non-error log entries] ---\n")
                    skippedRun = 0
                }
                keptBlocks.add(blocks[i])
                out.append(blocks[i].text).append('\n')
            } else {
                skippedRun++
            }
        }
        if (skippedRun > 0) {
            out.append("--- [Compacted ").append(skippedRun).append(" non-error log entries] ---\n")
        }
        return CompactionResult(text = out.toString(), hasErrors = anyErrors, keptBlocks = keptBlocks)
    }

    private fun performAutoDumpLocked() {
        if (!isDumping.compareAndSet(false, true)) return
        try {
            if (!::logFile.isInitialized || !logFile.exists() || logFile.length() < MAX_LOG_SIZE_BYTES) {
                return
            }

            val now = System.currentTimeMillis()
            val blocks = logFile.bufferedReader(Charsets.UTF_8).useLines { seq ->
                parseBlocksFromSequence(seq)
            }
            val compacted = compactLogBlocks(blocks)

            // If we're still within cooldown, compact active_session.log in place so disk doesn't balloon without spamming Downloads
            if (now - lastAutoDumpAtMs < AUTO_DUMP_COOLDOWN_MS) {
                try {
                    logFile.writeText(compacted.text, Charsets.UTF_8)
                    _logSizeBytes.value = logFile.length()
                } catch (e: Exception) {
                    Log.e(TAG, "Error compacting log file in place", e)
                }
                return
            }

            lastAutoDumpAtMs = now
            val timestamp = formatExportDate(now)
            val dumpFileName = "Vianbrplay_log_auto_$timestamp.txt"

            val writtenUri = writeToDownloadsWithUri(appContext, dumpFileName, compacted.text)
            if (writtenUri.success) {
                if (writtenUri.mediaStoreUri != null) {
                    recordAndPrunePublicAutoDumps(appContext, writtenUri.mediaStoreUri, compacted.hasErrors)
                }
                pruneLegacyAndBackupAutoDumps(appContext)

                // Retain recent error + surrounding context blocks (capped) in active_session.log so LoggerScreen stays informative
                val retainedErrorLines = buildRetainedErrorTail(compacted.keptBlocks)
                val notification = LogEntry(
                    now,
                    false,
                    "System",
                    "Compacted & auto-dumped to Downloads ($dumpFileName); older routine logs trimmed."
                )
                try {
                    BufferedWriter(OutputStreamWriter(FileOutputStream(logFile, false), Charsets.UTF_8)).use { writer ->
                        if (retainedErrorLines.isNotEmpty()) {
                            writer.write(retainedErrorLines)
                        }
                        writer.write(notification.formattedString)
                        writer.newLine()
                        writer.flush()
                    }
                    _logSizeBytes.value = logFile.length()
                } catch (e: Exception) {
                    Log.e(TAG, "Error rewriting active log file after auto-dump", e)
                }

                synchronized(bufferLock) {
                    lastCoalesceEntry = null
                    lastCoalesceCount = 0
                    memoryBuffer.clear()
                    memoryBuffer.addLast(notification)
                    _recentLogs.value = memoryBuffer.toList()
                }
                Log.d(TAG, "Auto-dump complete: $dumpFileName ($currentLogSizeFormatted)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-dump", e)
        } finally {
            isDumping.set(false)
        }
    }

    private fun buildRetainedErrorTail(keptBlocks: List<LogBlock>): String {
        if (keptBlocks.none { it.isError }) return ""
        val keepErr = BooleanArray(keptBlocks.size)
        for (i in keptBlocks.indices) {
            if (keptBlocks[i].isError) {
                val start = (i - 5).coerceAtLeast(0)
                val end = (i + 5).coerceAtMost(keptBlocks.lastIndex)
                for (j in start..end) keepErr[j] = true
            }
        }
        val selected = keptBlocks.indices.filter { keepErr[it] }.takeLast(ACTIVE_RETENTION_ERROR_TAIL_BLOCKS)
        if (selected.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("--- [Retained ").append(selected.size).append(" error/context blocks from prior window] ---\n")
        for (idx in selected) {
            sb.append(keptBlocks[idx].text).append('\n')
        }
        return sb.toString()
    }

    private fun recordAndPrunePublicAutoDumps(context: Context, uri: Uri, hasErrors: Boolean) {
        if (!::prefs.isInitialized || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        synchronized(prefsLock) {
            try {
                val raw = prefs.getString(KEY_AUTO_DUMP_RECORDS, "[]") ?: "[]"
                val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
                val records = mutableListOf<Pair<String, Boolean>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("uri", "")
                    val e = obj.optBoolean("err", false)
                    if (u.isNotBlank()) records.add(u to e)
                }
                records.add(uri.toString() to hasErrors)

                // Delete oldest non-error dumps first once over MAX_AUTO_DUMP_FILES; protect error dumps up to HARD_CAP
                while (records.size > MAX_AUTO_DUMP_FILES) {
                    val nonErrorIdx = records.indexOfFirst { !it.second }
                    val removeIdx = when {
                        nonErrorIdx != -1 -> nonErrorIdx
                        records.size > HARD_CAP_AUTO_DUMP_FILES -> 0
                        else -> break
                    }
                    val target = records.removeAt(removeIdx)
                    try {
                        context.contentResolver.delete(Uri.parse(target.first), null, null)
                    } catch (_: Exception) {}
                }

                val outArr = JSONArray()
                for ((u, e) in records) {
                    outArr.put(JSONObject().put("uri", u).put("err", e))
                }
                prefs.edit().putString(KEY_AUTO_DUMP_RECORDS, outArr.toString()).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error pruning MediaStore auto-dumps", e)
            }
        }
    }

    private fun pruneLegacyAndBackupAutoDumps(context: Context) {
        fun pruneDir(dir: File?) {
            if (dir == null || !dir.exists() || !dir.isDirectory) return
            try {
                val autoFiles = dir.listFiles { _, name ->
                    name.startsWith("Vianbrplay_log_auto_") && name.endsWith(".txt")
                }?.sortedBy { it.lastModified() }?.toMutableList() ?: return

                while (autoFiles.size > MAX_AUTO_DUMP_FILES) {
                    val nonErrIdx = autoFiles.indexOfFirst { file ->
                        try {
                            file.bufferedReader(Charsets.UTF_8).use { reader ->
                                reader.lineSequence().none { line ->
                                    line.contains(" ERROR [") || line.contains("[CRASH]")
                                }
                            }
                        } catch (_: Exception) {
                            true
                        }
                    }
                    val removeIdx = when {
                        nonErrIdx != -1 -> nonErrIdx
                        autoFiles.size > HARD_CAP_AUTO_DUMP_FILES -> 0
                        else -> break
                    }
                    autoFiles.removeAt(removeIdx).delete()
                }
            } catch (_: Exception) {}
        }

        pruneDir(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            pruneDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
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

    suspend fun loadAllLogs(): List<LogEntry> = withContext(diskDispatcher) {
        fileLock.withLock {
            if (!::logFile.isInitialized || !logFile.exists()) {
                return@withContext recentLogs.value
            }
            try {
                val nowMs = System.currentTimeMillis()
                val dayStartCal = java.util.Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val midnightMs = dayStartCal.timeInMillis
                val parsedList = mutableListOf<LogEntry>()
                var currentEntry: LogEntry? = null
                val stackTraceBuilder = StringBuilder()

                logFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("[") && line.length > 14 && line[13] == ']') {
                            if (currentEntry != null) {
                                val st = if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.toString().trimEnd() else null
                                parsedList.add(currentEntry!!.copy(stackTrace = st))
                                stackTraceBuilder.clear()
                            }
                            currentEntry = parseLogLine(line, nowMs, midnightMs)
                        } else if (currentEntry != null) {
                            if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.append("\n")
                            stackTraceBuilder.append(line)
                        }
                    }
                }
                if (currentEntry != null) {
                    val st = if (stackTraceBuilder.isNotEmpty()) stackTraceBuilder.toString().trimEnd() else null
                    parsedList.add(currentEntry!!.copy(stackTrace = st))
                }
                if (parsedList.isNotEmpty()) parsedList else recentLogs.value
            } catch (e: Exception) {
                Log.e(TAG, "Error loading full log from disk, falling back to memory buffer", e)
                recentLogs.value
            }
        }
    }

    private fun parseHmsToOffsetMs(timeStr: String): Long? {
        // Expected format: HH:mm:ss.SSS (12 chars)
        if (timeStr.length != 12 || timeStr[2] != ':' || timeStr[5] != ':' || timeStr[8] != '.') return null
        fun d(idx: Int): Int = timeStr[idx] - '0'
        for (idx in intArrayOf(0, 1, 3, 4, 6, 7, 9, 10, 11)) {
            val v = d(idx)
            if (v !in 0..9) return null
        }
        val hours = d(0) * 10 + d(1)
        val minutes = d(3) * 10 + d(4)
        val seconds = d(6) * 10 + d(7)
        val millis = d(9) * 100 + d(10) * 10 + d(11)
        if (hours > 23 || minutes > 59 || seconds > 59) return null
        return ((hours * 3600L + minutes * 60L + seconds) * 1000L) + millis
    }

    private fun parseLogLine(line: String, nowMs: Long, midnightMs: Long): LogEntry {
        try {
            val timeEnd = line.indexOf(']')
            val timeStr = if (timeEnd > 1) line.substring(1, timeEnd) else ""
            val offsetMs = if (timeStr.isNotEmpty()) parseHmsToOffsetMs(timeStr) else null
            val parsedTimestampMs = if (offsetMs != null) {
                val candidate = midnightMs + offsetMs
                if (candidate > nowMs + 60_000L) candidate - 86_400_000L else candidate
            } else {
                nowMs
            }

            val remainder = line.substring(timeEnd + 1).trimStart()
            val isError = remainder.startsWith("ERROR ")
            val tagStart = remainder.indexOf('[')
            val tagEnd = remainder.indexOf(']')
            val tag = if (tagStart >= 0 && tagEnd > tagStart) remainder.substring(tagStart + 1, tagEnd) else "App"
            val colonIdx = remainder.indexOf(':', tagEnd.coerceAtLeast(0))
            val message = if (colonIdx >= 0 && colonIdx < remainder.length - 1) remainder.substring(colonIdx + 1).trimStart() else remainder

            return LogEntry(
                timestampMs = parsedTimestampMs,
                isError = isError,
                tag = tag,
                message = message
            )
        } catch (e: Exception) {
            return LogEntry(nowMs, false, "App", line)
        }
    }

    fun clearLogs() {
        diskScope.launch {
            fileLock.withLock {
                try {
                    if (::logFile.isInitialized && logFile.exists()) {
                        FileOutputStream(logFile, false).close()
                        _logSizeBytes.value = 0L
                    }
                    synchronized(bufferLock) {
                        lastCoalesceEntry = null
                        lastCoalesceCount = 0
                        memoryBuffer.clear()
                        _recentLogs.value = emptyList()
                    }
                    log("Log buffer cleared", "System")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed clearing logs", e)
                }
            }
        }
    }

    fun dumpCurrentLogs(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!_isEnabled.value) {
            onComplete?.invoke(false, "Logger is disabled")
            return
        }
        diskScope.launch {
            try {
                val now = System.currentTimeMillis()
                val dateStr = formatExportDate(now)
                val fileName = "Vianbrplay_logs_$dateStr.txt"

                val logsContent = fileLock.withLock {
                    if (::logFile.isInitialized && logFile.exists() && logFile.length() > 0) {
                        logFile.readText(Charsets.UTF_8)
                    } else {
                        synchronized(bufferLock) {
                            memoryBuffer.joinToString("\n") { it.formattedString }
                        }
                    }
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
                Log.e(TAG, "Failed to write manual log dump: ${sanitize(e.message)}")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, sanitize(e.message ?: "Unknown error"))
                }
            }
        }
    }

    private fun dumpCrash(context: Context, throwable: Throwable) {
        val acquired = try {
            fileLock.tryLock(250L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        try {
            val now = System.currentTimeMillis()
            val dateStr = formatExportDate(now)
            val fileName = "Vianbrplay_crash_$dateStr.txt"
            val recentList = synchronized(bufferLock) {
                memoryBuffer.toList().takeLast(120)
            }
            val recent = recentList.joinToString("\n") { it.formattedString }
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
                Recent Logs (${recentList.size} entries):
                $recent
                ==============================
            """.trimIndent()

            writeToDownloads(context, fileName, crashData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash dump: ${sanitize(e.message)}")
        } finally {
            if (acquired) {
                fileLock.unlock()
            }
        }
    }

    private data class WriteDownloadResult(
        val success: Boolean,
        val mediaStoreUri: Uri? = null
    )

    private fun writeToDownloads(context: Context, fileName: String, content: String): Boolean {
        return writeToDownloadsWithUri(context, fileName, content).success
    }

    private fun writeToDownloadsWithUri(context: Context, fileName: String, content: String): WriteDownloadResult {
        var success = false
        var insertedUri: Uri? = null
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
                    insertedUri = uri
                    success = true
                    Log.d(TAG, "Successfully written log export: ${sanitize(fileName)}")
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.writeText(content)
                success = true
                Log.d(TAG, "Successfully written log export to Downloads: ${sanitize(file.name)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to primary Downloads: ${sanitize(e.message)}")
        }

        // Failsafe backup to app-specific external files dir only if primary Downloads write failed
        if (!success) {
            try {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (appDownloads != null) {
                    if (!appDownloads.exists()) appDownloads.mkdirs()
                    val backupFile = File(appDownloads, fileName)
                    backupFile.writeText(content)
                    Log.d(TAG, "Backup log written to app downloads dir: ${sanitize(backupFile.name)}")
                    success = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to backup app downloads: ${sanitize(e.message)}")
            }
        }

        return WriteDownloadResult(success = success, mediaStoreUri = insertedUri)
    }
}
