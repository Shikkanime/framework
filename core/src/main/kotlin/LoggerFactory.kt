package fr.shikkanime.core

import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.*

/**
 * A factory class for creating and managing logger instances with a custom log formatting
 * and configuration. Provides functionality to retrieve or create loggers by name or class.
 */
class LoggerFactory {
    /**
     * A custom log formatter for formatting log records into a specific string representation.
     *
     * This formatter outputs log records in the following format:
     * [date and time] [log level] [logger name] - [message][stack trace (if applicable)]
     *
     * The date and time format used is "yyyy-MM-dd HH:mm:ss.SSS".
     *
     * In cases where an exception is associated with the log record, the stack trace
     * is appended to the log output following the message.
     */
    class LogFormatter : Formatter() {
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /**
         * Formats a log record into a string representation for logging purposes.
         *
         * The formatted string includes the current date and time, log level, logger name,
         * log message, and, if an exception is attached to the log record, the corresponding stack trace.
         * The date and time are formatted using the pattern "yyyy-MM-dd HH:mm:ss.SSS".
         *
         * @param record The log record to be formatted. Maybe null, in which case a default format
         *               with only the date and time will be returned.
         * @return A string representation of the log record, including any attached exception stack trace.
         */
        override fun format(record: LogRecord?): String {
            val message = formatMessage(record)

            val throwable = record?.thrown?.let {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println()
                it.printStackTrace(pw)
                pw.close()
                sw.toString()
            } ?: System.lineSeparator()

            // %d{YYYY-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
            // Use this format for logback
            return "${ZonedDateTime.now().format(dateTimeFormatter)} ${record?.level?.localizedName} ${record?.loggerName} - $message$throwable"
        }
    }

    companion object {
        private val map = ConcurrentHashMap<String, Logger>()

        // Shared writer and a single shared handler, so every logger appends into the same
        // coherent daily files and the file sink has one lifecycle + one failure state.
        private val fileWriter = LogFileWriter()
        private val fileHandler = FileLogHandler(fileWriter)

        var level: Level = Level.ALL

        /**
         * Builds and configures a new logger instance with a custom log formatter, a console handler
         * and an always-on file handler.
         *
         * @param name The name of the logger to create.
         * @return The configured logger instance.
         */
        private fun buildLogger(name: String): Logger {
            val logger = Logger.getLogger(name)
            logger.useParentHandlers = false
            val consoleHandler = ConsoleHandler()
            consoleHandler.formatter = LogFormatter()
            consoleHandler.level = this.level
            logger.addHandler(consoleHandler)
            logger.level = this.level

            // File storage is always on, via the single shared handler. The guard keeps that one
            // handler attached exactly once per logger (defence-in-depth against duplicates).
            if (logger.handlers.none { it === fileHandler }) {
                fileHandler.level = this.level
                logger.addHandler(fileHandler)
            }
            return logger
        }

        /**
         * Retrieves a logger instance associated with the given class. If a logger is not already
         * created for the class, a new logger is built and configured atomically, then stored.
         *
         * @param clazz The class for which the logger is to be retrieved or created.
         * @return The logger instance associated with the provided class.
         */
        fun getLogger(clazz: Class<*>): Logger =
            map.computeIfAbsent(clazz.name) { buildLogger(it) }

        /**
         * Retrieves a logger instance associated with the given name. If a logger is not already
         * created for that name, a new logger is built and configured atomically, then stored.
         *
         * @param name The name of the logger to retrieve or create.
         * @return The logger instance associated with the provided name.
         */
        fun getLogger(name: String): Logger =
            map.computeIfAbsent(name) { buildLogger(it) }
    }
}