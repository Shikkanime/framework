package fr.shikkanime.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.logging.Level
import java.util.logging.LogRecord

private class LoggerHolder {
    val logger = LoggerFactory.getLogger()
    fun loggerName(): String = logger.name
}

private class CachedLoggerHolder {
    val logger1 = LoggerFactory.getLogger()
    val logger2 = LoggerFactory.getLogger()
}

private class CompanionLoggerHolder {
    companion object {
        val logger = LoggerFactory.getLogger()
    }
}

private object ObjectLoggerHolder {
    val logger = LoggerFactory.getLogger()
}

class LoggerFactoryTest {

    @Nested
    @DisplayName("tests for LoggerFactory instance management")
    inner class LoggerRetrievalTests {
        @Test
        fun `should return same logger instance for given class`() {
            // Given
            val clazz = LoggerFactoryTest::class.java

            // When
            val logger1 = LoggerFactory.getLogger(clazz)
            val logger2 = LoggerFactory.getLogger(clazz)

            // Then
            assertSame(logger1, logger2)
            assertEquals(clazz.name, logger1.name)
        }

        @ParameterizedTest
        @ValueSource(strings = ["custom.logger.one", "custom.logger.two", "service.user"])
        fun `should return same logger instance for given parameterized logger names`(loggerName: String) {
            // Given / When
            val logger1 = LoggerFactory.getLogger(loggerName)
            val logger2 = LoggerFactory.getLogger(loggerName)

            // Then
            assertSame(logger1, logger2)
            assertEquals(loggerName, logger1.name)
        }
    }

    @Nested
    @DisplayName("tests for LoggerFactory caller-class retrieval")
    inner class CallerClassRetrievalTests {
        @Test
        fun `should name the logger after the calling class when called without arguments`() {
            // Given
            val holder = LoggerHolder()

            // When
            val loggerName = holder.loggerName()

            // Then
            assertEquals(LoggerHolder::class.java.name, loggerName)
        }

        @Test
        fun `should return the same cached logger instance for repeated no-argument calls`() {
            // Given
            val holder = CachedLoggerHolder()

            // When / Then
            assertSame(holder.logger1, holder.logger2)
            assertEquals(CachedLoggerHolder::class.java.name, holder.logger1.name)
        }

        @Test
        fun `should name the logger after the enclosing class when called from a companion object`() {
            // Given / When
            val logger = CompanionLoggerHolder.logger

            // Then
            assertEquals(CompanionLoggerHolder::class.java.name, logger.name)
        }

        @Test
        fun `should name the logger after the object class when called from an object declaration`() {
            // Given / When
            val logger = ObjectLoggerHolder.logger

            // Then
            assertEquals(ObjectLoggerHolder::class.java.name, logger.name)
        }

        @Test
        fun `should return a logger equivalent to the explicit class variant for the calling class`() {
            // Given / When
            val inferred = LoggerHolder().logger
            val explicit = LoggerFactory.getLogger(LoggerHolder::class.java)

            // Then
            assertSame(explicit, inferred)
        }
    }

    @Nested
    @DisplayName("tests for LogFormatter formatting")
    inner class LogFormatterTests {
        @Test
        fun `should format log record cleanly with date level logger name and message`() {
            // Given
            val formatter = LoggerFactory.LogFormatter()
            val record = LogRecord(Level.INFO, "Test log message").apply {
                loggerName = "test.logger"
            }

            // When
            val formattedOutput = formatter.format(record)

            // Then
            assertTrue(formattedOutput.contains("INFO"))
            assertTrue(formattedOutput.contains("test.logger"))
            assertTrue(formattedOutput.contains("Test log message"))
        }

        @Test
        fun `should append stack trace when record contains a throwable`() {
            // Given
            val formatter = LoggerFactory.LogFormatter()
            val exception = RuntimeException("Test exception error")
            val record = LogRecord(Level.SEVERE, "Failed operation").apply {
                loggerName = "test.logger"
                thrown = exception
            }

            // When
            val formattedOutput = formatter.format(record)

            // Then
            assertTrue(formattedOutput.contains("Failed operation"))
            assertTrue(formattedOutput.contains("RuntimeException: Test exception error"))
        }
    }
}
