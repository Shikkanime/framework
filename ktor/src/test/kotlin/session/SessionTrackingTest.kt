package fr.shikkanime.ktor.session

import fr.shikkanime.ktor.ControllerBinder
import fr.shikkanime.ktor.GetMapping
import fr.shikkanime.ktor.IController
import fr.shikkanime.ktor.ResponseEntity
import fr.shikkanime.ktor.RestController
import fr.shikkanime.ktor.configureDefaultModules
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class SessionTrackingTest {

    class RecordingStore : SessionStore {
        val events = CopyOnWriteArrayList<SessionEvent>()

        override fun append(event: SessionEvent) {
            events.add(event)
        }

        override suspend fun find(sessionId: String, limit: Int): SessionTrace? {
            val matching = events.filter { it.sessionId == sessionId }
            if (matching.isEmpty()) return null
            val selected = matching.takeLast(limit)
            return SessionTrace(
                sessionId = sessionId,
                service = "test",
                firstSeen = matching.first().timestamp,
                lastSeen = matching.last().timestamp,
                eventCount = matching.size,
                truncated = selected.size < matching.size,
                events = selected
            )
        }

        override suspend fun sessions(limit: Int): List<SessionSummary> =
            events.groupBy { it.sessionId }.entries.take(limit).map { (sessionId, events) ->
                SessionSummary(
                    sessionId = sessionId,
                    service = "test",
                    firstSeen = events.first().timestamp,
                    lastSeen = events.last().timestamp,
                    sizeBytes = events.size.toLong()
                )
            }

        override fun flush() = Unit

        override fun close() = Unit
    }

    @Suppress("unused") // Controller methods are bound reflectively by ControllerBinder.
    @RestController("/api")
    class TrackingController : IController {
        @GetMapping("/ok")
        fun ok(): ResponseEntity<String> =
            ResponseEntity.ok("ok")

        @GetMapping("/fail")
        fun fail(): String =
            throw IllegalStateException("boom")

        @GetMapping("/session")
        fun session(): ResponseEntity<String> =
            ResponseEntity.ok(SessionContext.current()?.sessionId ?: "none")
    }

    private fun ApplicationTestBuilder.configure(
        store: SessionStore,
        config: SessionConfig = SessionConfig(store = store, service = "test", apiToken = "secret")
    ) {
        application {
            configureDefaultModules(session = config)
            routing { ControllerBinder.register(this, listOf(TrackingController())) }
        }
    }

    @Nested
    @DisplayName("tests for inbound correlation")
    inner class InboundTests {

        @Test
        fun `should reuse the session and request identifiers of the client`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                val response = client.get("/api/ok") {
                    header(SessionHeaders.SESSION_ID, "session-1")
                    header(SessionHeaders.REQUEST_ID, "request-1")
                }

                // Then
                assertEquals("session-1", response.headers[SessionHeaders.SESSION_ID])
                assertEquals("request-1", response.headers[SessionHeaders.REQUEST_ID])
                val event = store.events.single()
                assertEquals(SessionEventKind.IN, event.kind)
                assertEquals("session-1", event.sessionId)
                assertEquals("request-1", event.requestId)
                assertEquals("test", event.service)
                assertEquals("GET", event.method)
                assertEquals("/api/ok", event.path)
                assertEquals(200, event.status)
                assertNotNull(event.durationMs)
                assertNotNull(event.size)
            }

        @Test
        fun `should generate identifiers when the client sends none`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                val response = client.get("/api/ok")

                // Then
                val sessionId = response.headers[SessionHeaders.SESSION_ID]
                val requestId = response.headers[SessionHeaders.REQUEST_ID]
                assertNotNull(sessionId)
                assertNotNull(requestId)
                assertTrue(SessionIds.isValid(sessionId))
                assertTrue(SessionIds.isValid(requestId))
                assertEquals(sessionId, store.events.single().sessionId)
            }

        @Test
        fun `should replace an unsafe session identifier`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                val response = client.get("/api/ok") {
                    header(SessionHeaders.SESSION_ID, "../../etc/passwd")
                }

                // Then
                val sessionId = response.headers[SessionHeaders.SESSION_ID]
                assertNotNull(sessionId)
                assertTrue(SessionIds.isValid(sessionId))
                assertNotEquals("../../etc/passwd", sessionId)
                assertEquals(sessionId, store.events.single().sessionId)
            }

        @Test
        fun `should expose the scope inside the controller`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                val response = client.get("/api/session") {
                    header(SessionHeaders.SESSION_ID, "session-1")
                }

                // Then
                assertEquals("session-1", response.bodyAsText().trim('"'))
            }

        @Test
        fun `should record the exception of a failing controller`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                val response = client.get("/api/fail") {
                    header(SessionHeaders.SESSION_ID, "session-1")
                }

                // Then
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                val event = store.events.single()
                assertEquals(500, event.status)
                assertNotNull(event.error)
                assertTrue(event.error!!.contains("IllegalStateException"))
                assertTrue(event.error!!.contains("boom"))
            }
    }

    @Nested
    @DisplayName("tests for excluded requests")
    inner class ExclusionTests {

        @Test
        fun `should ignore health and preflight requests`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)

                // When
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                client.options("/api/ok")

                // Then
                assertTrue(store.events.isEmpty())
            }

        @Test
        fun `should not track a request without a session when generation is disabled`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(
                    store,
                    SessionConfig(store = store, service = "test", generateIfMissing = false)
                )

                // When
                val response = client.get("/api/ok")

                // Then
                assertNull(response.headers[SessionHeaders.SESSION_ID])
                assertTrue(store.events.isEmpty())
            }
    }

    @Nested
    @DisplayName("tests for the session read API")
    inner class ReadApiTests {

        @Test
        fun `should protect and serve the session read API`() =
            testApplication {
                // Given
                val store = RecordingStore()
                configure(store)
                client.get("/api/ok") { header(SessionHeaders.SESSION_ID, "session-1") }

                // When / Then
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    client.get("/v1/session-traces/session-1").status
                )

                val trace = client.get("/v1/session-traces/session-1") {
                    header(HttpHeaders.Authorization, "Bearer secret")
                }
                assertEquals(HttpStatusCode.OK, trace.status)
                assertTrue(trace.bodyAsText().contains(""""sessionId":"session-1""""))

                val sessions = client.get("/v1/session-traces") {
                    header(HttpHeaders.Authorization, "Bearer secret")
                }
                assertEquals(HttpStatusCode.OK, sessions.status)
                assertTrue(sessions.bodyAsText().contains("session-1"))

                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("/v1/session-traces/unknown-session") {
                        header(HttpHeaders.Authorization, "Bearer secret")
                    }.status
                )
                assertEquals(
                    HttpStatusCode.BadRequest,
                    client.get("/v1/session-traces/bad!id") {
                        header(HttpHeaders.Authorization, "Bearer secret")
                    }.status
                )
                assertEquals(1, store.events.size)
            }
    }
}
