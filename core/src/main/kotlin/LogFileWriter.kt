package fr.shikkanime.core

import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Comparator

/**
 * Thread-safe writer for framework log files.
 *
 * Owns the daily log files produced by [FileLogHandler]:
 *  - one primary file per day (`<yyyy-MM-dd>.log`), started again on each new day;
 *  - a current-day file that reaches [maxFileBytes] rolls to `<yyyy-MM-dd>.N.log` (N = 1, 2, …);
 *  - records older than [retention] are purged (rolling retention, run at most once per day);
 *  - files are written in UTF-8, in append mode, never overwriting an existing segment.
 *
 * All rotation / retention / file-naming decisions derive from [clock] (injected for tests).
 * The log directory is resolved lazily on the first write (read once at first use).
 */
internal class LogFileWriter(
    private val configuredLogDirectory: Path? = null,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val retention: Duration = DEFAULT_RETENTION,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val lock = Any()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private var logDirectory: Path? = null
    private var currentDate: LocalDate? = null
    private var currentSegment = 0
    private var bytesWritten = 0L
    private var writer: BufferedWriter? = null
    private var retentionRunDate: LocalDate? = null
    private var failed = false

    /**
     * Writes a single already-formatted log line.
     *
     * The caller (a [FileLogHandler]) provides the output of [LoggerFactory.LogFormatter.format],
     * which is already newline-terminated, so this method writes it verbatim (no extra separator).
     *
     * @param text The formatted, newline-terminated log line.
     */
    fun write(text: String) {
        synchronized(lock) {
            if (failed) return
            try {
                ensureOpen()

                val today = LocalDate.now(clock)
                if (currentDate != today) {
                    rollToNewDay(today)
                }

                // Soft per-file target: a single oversized record is written as-is (never split).
                val bytes = text.toByteArray(StandardCharsets.UTF_8)
                if (bytesWritten > 0 && bytesWritten + bytes.size > maxFileBytes) {
                    rollSegment(today)
                }

                writer!!.write(text)
                bytesWritten += bytes.size
                writer!!.flush()
            } catch (error: Exception) {
                // Shared across every logger: disable the whole file sink on the first failure.
                failed = true
                throw error
            }
        }
    }

    /** Flushes the current log file, if any. */
    fun flush() {
        synchronized(lock) {
            writer?.flush()
        }
    }

    /**
     * Flushes and releases the current log file handle.
     *
     * Idempotent: multiple handlers may share this writer and JUL calls [close] on every handler
     * at shutdown / [java.util.logging.LogManager.reset]. Closing does not disable the sink: the
     * next [write] transparently re-opens the file (append), so file logging survives a reset.
     */
    fun close() {
        synchronized(lock) {
            writer?.flush()
            writer?.close()
            writer = null
            bytesWritten = 0L
        }
    }

    private fun ensureOpen() {
        if (writer != null) return
        val dir = resolveLogDirectory()
        Files.createDirectories(dir)

        val today = LocalDate.now(clock)
        runRetentionIfNeeded(dir, today)
        val segment = selectActiveSegment(dir, today)
        currentDate = today
        currentSegment = segment
        open(dir, today, segment)
    }

    private fun open(dir: Path, date: LocalDate, segment: Int) {
        val file = fileFor(dir, date, segment)
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        bytesWritten = Files.size(file)
    }

    private fun closeCurrent() {
        writer?.close()
        writer = null
        bytesWritten = 0L
    }

    private fun rollToNewDay(today: LocalDate) {
        closeCurrent()
        val dir = requireLogDirectory()
        currentSegment = 0
        runRetentionIfNeeded(dir, today)
        open(dir, today, 0)
        currentDate = today
    }

    private fun rollSegment(today: LocalDate) {
        closeCurrent()
        val dir = requireLogDirectory()
        var segment = currentSegment + 1
        while (Files.exists(fileFor(dir, today, segment))) {
            segment++
        }
        currentSegment = segment
        open(dir, today, segment)
    }

    private fun selectActiveSegment(dir: Path, date: LocalDate): Int =
        try {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .map { segmentOf(date, it.fileName.toString()) }
                    .filter { it != null }
                    .map { it!! }
                    .max(Comparator.naturalOrder())
                    .orElse(0)
            }
        } catch (_: IOException) {
            0
        }

    private fun segmentOf(date: LocalDate, fileName: String): Int? {
        val prefix = dateFormatter.format(date)
        val match = Regex("^$prefix(?:\\.(\\d+))?\\.$EXTENSION$").matchEntire(fileName) ?: return null
        val segment = match.groupValues[1].takeIf { it.isNotEmpty() }
        return segment?.toIntOrNull() ?: if (segment == null) 0 else null
    }

    private fun runRetentionIfNeeded(dir: Path, today: LocalDate) {
        if (retentionRunDate == today) return
        retentionRunDate = today

        val cutoff = clock.millis() - retention.toMillis()
        try {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().matches(LOG_FILE_NAME) }
                    .filter { isOlderThan(it, cutoff) }
                    .forEach { runCatching { Files.deleteIfExists(it) } }
            }
        } catch (_: IOException) {
            // Ignore scanning errors: file logging must never break the application.
        }
    }

    private fun isOlderThan(file: Path, cutoff: Long): Boolean =
        try {
            Files.getLastModifiedTime(file).toMillis() < cutoff
        } catch (_: IOException) {
            false
        }

    private fun fileFor(dir: Path, date: LocalDate, segment: Int): Path =
        dir.resolve(
            if (segment == 0) "${dateFormatter.format(date)}.$EXTENSION"
            else "${dateFormatter.format(date)}.$segment.$EXTENSION"
        )

    private fun resolveLogDirectory(): Path {
        logDirectory?.let { return it }
        val envValue = System.getenv(LOG_DIRECTORY_ENV)?.takeIf { it.isNotBlank() }
        val propertyValue = System.getProperty(LOG_DIRECTORY_PROPERTY)?.takeIf { it.isNotBlank() }
        val path = configuredLogDirectory
            ?: envValue?.let(Paths::get)
            ?: propertyValue?.let(Paths::get)
            ?: Paths.get(System.getProperty("user.dir"), "logs")
        logDirectory = path
        return path
    }

    private fun requireLogDirectory(): Path =
        logDirectory ?: throw IllegalStateException("Log directory not resolved")

    companion object {
        private const val EXTENSION = "log"
        private const val LOG_DIRECTORY_ENV = "SHIKKANIME_LOGS_DIR"
        private const val LOG_DIRECTORY_PROPERTY = "shikkanime.logs.dir"
        private const val DEFAULT_MAX_FILE_BYTES = 5L * 1024L * 1024L
        private val LOG_FILE_NAME = Regex("^\\d{4}-\\d{2}-\\d{2}(\\.\\d+)?\\.log$")
        private val DEFAULT_RETENTION: Duration = Duration.ofHours(72)
    }
}
