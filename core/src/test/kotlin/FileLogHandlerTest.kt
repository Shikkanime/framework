package fr.shikkanime.core

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.logging.Level
import java.util.logging.LogRecord

class FileLogHandlerTest {

    @TempDir
    lateinit var logDir: Path

    @Nested
    @DisplayName("file format")
    inner class FormatTests {

        @Test
        fun `should write formatted records with no blank line between them`() {
            // Given
            val handler = newHandler(clockAt("2026-08-10T10:00:00Z"))

            // When
            handler.publish(record(Level.INFO, "hello", "svc"))
            handler.publish(record(Level.INFO, "world", "svc"))
            handler.flush()

            // Then
            val lines = Files.readAllLines(logDir.resolve("2026-08-10.log"))
            assertEquals(2, lines.size)
            assertTrue(lines[0].contains("INFO") && lines[0].contains("svc") && lines[0].contains("hello"))
            assertTrue(lines[1].contains("world"))
        }

        @Test
        fun `should record full stack trace when a throwable is attached`() {
            // Given
            val handler = newHandler(clockAt("2026-08-10T10:00:00Z"))
            val exception = RuntimeException("boom").apply {
                stackTrace = arrayOf(StackTraceElement("fr.shikkanime.core.Svc", "run", "Svc.kt", 12))
            }

            // When
            handler.publish(LogRecord(Level.SEVERE, "failed").apply { loggerName = "svc"; thrown = exception })
            handler.flush()

            // Then
            val content = Files.readString(logDir.resolve("2026-08-10.log"))
            assertTrue(content.contains("failed"))
            assertTrue(content.contains("RuntimeException: boom"))
            assertTrue(content.contains("at fr.shikkanime.core.Svc.run"))
        }
    }

    @Nested
    @DisplayName("rotation")
    inner class RotationTests {

        @Test
        fun `should rotate to a new file when the size limit is reached`() {
            // Given a tiny limit to force rotation
            val handler = newHandler(clockAt("2026-08-10T10:00:00Z"), maxFileBytes = 120L)

            // When
            repeat(30) { handler.publish(record(Level.INFO, "line-$it", "svc")) }
            handler.flush()

            // Then: multiple segments exist and no record is lost or split across them.
            val files = filesIn(logDir)
            assertTrue(files.contains("2026-08-10.log"))
            assertTrue(files.any { it == "2026-08-10.1.log" || it == "2026-08-10.2.log" })
            val totalLines = Files.list(logDir).use { stream ->
                stream.filter { it.fileName.toString().startsWith("2026-08-10") }
                    .map { Files.readAllLines(it).size }
                    .toList()
                    .sum()
            }
            assertEquals(30, totalLines)
        }

        @Test
        fun `should roll to a new day file when the date changes mid-run`() {
            // Given
            val clock = MutableClock(Instant.parse("2026-08-10T23:59:00Z"), ZoneOffset.UTC)
            val handler = newHandler(clock, maxFileBytes = Long.MAX_VALUE)
            handler.publish(record(Level.INFO, "day-1", "svc"))

            // When
            clock.advanceTo(Instant.parse("2026-08-11T00:00:30Z"))
            handler.publish(record(Level.INFO, "day-2", "svc"))
            handler.flush()

            // Then
            assertTrue(Files.exists(logDir.resolve("2026-08-10.log")))
            assertTrue(Files.exists(logDir.resolve("2026-08-11.log")))
            assertTrue(Files.readString(logDir.resolve("2026-08-11.log")).contains("day-2"))
        }
    }

    @Nested
    @DisplayName("restart")
    inner class RestartTests {

        @Test
        fun `should append to existing day file after a writer restart`() {
            // Given
            val clock = clockAt("2026-08-10T10:00:00Z")
            val first = newHandler(clock)
            first.publish(record(Level.INFO, "before-restart", "svc"))
            first.close()

            // When: a fresh writer re-opens the same day directory.
            val second = newHandler(clock)
            second.publish(record(Level.INFO, "after-restart", "svc"))
            second.flush()

            // Then: the existing file is appended to, not truncated, and resumes from current size.
            val content = Files.readString(logDir.resolve("2026-08-10.log"))
            assertTrue(content.contains("before-restart"))
            assertTrue(content.contains("after-restart"))
        }
    }

    @Nested
    @DisplayName("retention")
    inner class RetentionTests {

        @Test
        fun `should delete files older than retention and keep recent ones`() {
            // Given
            val clock = clockAt("2026-08-10T10:00:00Z")
            val old = logDir.resolve("2026-08-05.log")
            Files.writeString(old, "old")
            Files.setLastModifiedTime(old, FileTime.fromMillis(clock.millis() - Duration.ofDays(4).toMillis()))
            val recent = logDir.resolve("2026-08-09.log")
            Files.writeString(recent, "recent")

            // When
            newHandler(clock, retention = Duration.ofHours(72)).publish(record(Level.INFO, "x", "svc"))

            // Then
            assertFalse(Files.exists(old))
            assertTrue(Files.exists(recent))
        }
    }

    @Nested
    @DisplayName("robustness")
    inner class RobustnessTests {

        @Test
        fun `should write a single record even if larger than the size limit`() {
            // Given
            val handler = newHandler(clockAt("2026-08-10T10:00:00Z"), maxFileBytes = 50L)
            val message = "x".repeat(500)

            // When
            assertDoesNotThrow { handler.publish(record(Level.INFO, message, "svc")) }
            handler.flush()

            // Then
            assertTrue(Files.readString(logDir.resolve("2026-08-10.log")).contains(message))
        }

        @Test
        fun `should not throw and degrade when the log directory cannot be created`() {
            // Given: the configured dir is an existing file, so createDirectories fails reliably.
            val blocked = logDir.resolve("blocked")
            Files.writeString(blocked, "I am a file")
            val handler = FileLogHandler(LogFileWriter(configuredLogDirectory = blocked, clock = clockAt("2026-08-10T10:00:00Z")))

            // When
            assertDoesNotThrow { handler.publish(record(Level.INFO, "x", "svc")) }
            assertDoesNotThrow { handler.publish(record(Level.INFO, "y", "svc")) }

            // Then (no assertion needed: nothing may throw and the app keeps running console-only).
        }
    }

    private fun newHandler(
        clock: Clock,
        maxFileBytes: Long = Long.MAX_VALUE,
        retention: Duration = Duration.ofHours(72),
    ): FileLogHandler =
        FileLogHandler(LogFileWriter(logDir, maxFileBytes, retention, clock))

    private fun record(level: Level, message: String, loggerName: String): LogRecord =
        LogRecord(level, message).apply { this.loggerName = loggerName }

    private fun clockAt(iso: String): Clock =
        MutableClock(Instant.parse(iso), ZoneOffset.UTC)

    private fun filesIn(dir: Path): List<String> =
        Files.list(dir).use { it.map { path -> path.fileName.toString() }.toList() }

    private class MutableClock(
        private var now: Instant,
        private val zone: ZoneId,
    ) : Clock() {

        fun advanceTo(instant: Instant) {
            now = instant
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    }
}
