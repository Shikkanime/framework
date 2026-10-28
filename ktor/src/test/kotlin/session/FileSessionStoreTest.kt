package fr.shikkanime.ktor.session

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

class FileSessionStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun event(sessionId: String, requestId: String = "request-1"): SessionEvent =
        SessionEvent(
            timestamp = Instant.now().toString(),
            sessionId = sessionId,
            requestId = requestId,
            service = "api",
            kind = SessionEventKind.IN,
            method = "GET",
            path = "/api/ok",
            status = 200
        )

    private fun store(
        retention: Duration = Duration.ofDays(1),
        queueCapacity: Int = 4_096
    ): FileSessionStore =
        FileSessionStore(tempDir, "api", retention = retention, queueCapacity = queueCapacity)

    @Nested
    @DisplayName("tests for the append and read cycle")
    inner class ReadWriteTests {

        @Test
        fun `should read the events written by a previous store instance`() =
            runBlocking {
                // Given
                val first = store()
                first.append(event("session-1"))
                first.flush()
                first.close()

                // When
                val second = store()
                second.append(event("session-1", requestId = "request-2"))
                second.flush()
                val trace = second.find("session-1")
                second.close()

                // Then
                assertNotNull(trace)
                assertEquals(2, trace!!.eventCount)
                assertEquals(listOf("request-1", "request-2"), trace.events.map { it.requestId })
            }

        @Test
        fun `should return only the requested tail of a session`() =
            runBlocking {
                // Given
                val store = store()
                repeat(3) { index -> store.append(event("session-1", requestId = "request-$index")) }
                store.flush()

                // When
                val trace = store.find("session-1", 2)
                store.close()

                // Then
                assertNotNull(trace)
                assertEquals(3, trace!!.eventCount)
                assertTrue(trace.truncated)
                assertEquals(listOf("request-1", "request-2"), trace.events.map { it.requestId })
            }

        @Test
        fun `should skip a truncated line after a restart`() =
            runBlocking {
                // Given
                val directory = tempDir.resolve("api")
                Files.createDirectories(directory)
                val file = directory.resolve("session-1.jsonl")
                Files.writeString(file, """{"timestamp":"2026-01-01T00:00:00Z","sessionId":"session-1""")

                // When
                val store = store()
                store.append(event("session-1"))
                store.flush()
                val trace = store.find("session-1")
                store.close()

                // Then
                assertNotNull(trace)
                assertEquals(1, trace!!.eventCount)
            }

        @Test
        fun `should ignore a session id that is not path safe`() {
            // Given
            val store = store()

            // When
            store.append(event("../escape"))
            store.flush()
            store.close()

            // Then
            assertFalse(Files.exists(tempDir.resolve("escape.jsonl")))
            assertFalse(Files.list(tempDir.resolve("api")).use { it.findAny().isPresent })
        }
    }

    @Nested
    @DisplayName("tests for concurrent writers")
    inner class ConcurrencyTests {

        @Test
        fun `should write every event offered by concurrent requests`() =
            runBlocking {
                // Given
                val store = store(queueCapacity = 4_096)
                val executor = Executors.newFixedThreadPool(8)

                // When
                val futures = (1..200).map { index ->
                    executor.submit {
                        store.append(event("session-1", requestId = "request-$index"))
                    }
                }
                futures.forEach { it.get() }
                executor.shutdown()
                store.flush()
                val trace = store.find("session-1", 500)
                store.close()

                // Then
                assertNotNull(trace)
                assertEquals(200, trace!!.eventCount)
                assertEquals(0, store.droppedEvents)
            }
    }

    @Nested
    @DisplayName("tests for retention and listings")
    inner class MaintenanceTests {

        @Test
        fun `should delete a journal older than the retention`() {
            // Given
            val directory = tempDir.resolve("api")
            Files.createDirectories(directory)
            val oldFile = directory.resolve("oldsession1.jsonl")
            Files.writeString(oldFile, SESSION_JSON.encodeToString(event("oldsession1")) + "\n")
            Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)))

            // When
            val store = store(Duration.ofDays(1))
            store.append(event("newsession1"))
            store.flush()
            store.close()

            // Then
            assertFalse(Files.exists(oldFile))
        }

        @Test
        fun `should list the most recently modified sessions first`() =
            runBlocking {
                // Given
                val directory = tempDir.resolve("api")
                Files.createDirectories(directory)
                val first = directory.resolve("sessionone1.jsonl")
                val second = directory.resolve("sessiontwo2.jsonl")
                Files.writeString(first, SESSION_JSON.encodeToString(event("sessionone1")) + "\n")
                Files.writeString(second, SESSION_JSON.encodeToString(event("sessiontwo2")) + "\n")
                Files.setLastModifiedTime(first, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                Files.setLastModifiedTime(second, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)))

                // When
                val store = store()
                val sessions = store.sessions(2)
                store.close()

                // Then
                assertEquals(listOf("sessiontwo2", "sessionone1"), sessions.map { it.sessionId })
            }
    }
}
