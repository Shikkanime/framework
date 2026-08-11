package fr.shikkanime.core

import java.util.logging.ErrorManager
import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * A [java.util.logging.Handler] that writes formatted log records to daily files.
 *
 * Uses the same [LoggerFactory.LogFormatter] as the console handler, so file output matches the
 * console format exactly (including full stack traces when a throwable is attached).
 *
 * File logging is always on (never gated behind a toggle). Failures never propagate to the
 * application: on the first write error the file sink disables itself and the framework keeps
 * working console-only. The level must be set by the caller to match [LoggerFactory.level]; the
 * Handler default (`Level.ALL`) is used otherwise.
 */
internal class FileLogHandler(
    private val fileWriter: LogFileWriter,
    private val logFormatter: LoggerFactory.LogFormatter = LoggerFactory.LogFormatter(),
) : Handler() {

    @Volatile
    private var disabled = false

    override fun publish(record: LogRecord?) {
        if (disabled || record == null) return
        if (!isLoggable(record)) return

        try {
            fileWriter.write(logFormatter.format(record))
        } catch (error: Exception) {
            disabled = true
            reportError("Disabled file logging after a write failure", error, ErrorManager.WRITE_FAILURE)
        }
    }

    override fun flush() {
        try {
            fileWriter.flush()
        } catch (_: Exception) {
            // Never break the application over file logging.
        }
    }

    override fun close() {
        try {
            fileWriter.close()
        } catch (_: Exception) {
            // Never break the application over file logging.
        }
    }
}
