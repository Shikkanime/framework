package fr.shikkanime.ktor.session

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class SessionClientPluginTest {

    private class RecordingStore : SessionStore {
        val events = CopyOnWriteArrayList<SessionEvent>()

        override fun append(event: SessionEvent) {
            events.add(event)
        }

        override suspend fun find(sessionId: String, limit: Int): SessionTrace? =
            null

        override suspend fun sessions(limit: Int): List<SessionSummary> =
            emptyList()

        override fun flush() = Unit

        override fun close() = Unit
    }

    @Test
    fun `should forward the session and record the outgoing call`() =
        runBlocking {
            // Given
            val store = RecordingStore()
            val capturedHeaders = AtomicReference<Headers>()
            val engine = MockEngine { request ->
                capturedHeaders.set(request.headers)
                respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
            }
            val client = HttpClient(engine)
            client.installSessionPropagation()
            val scope = SessionScope("session-1", "request-1", service = "test", store = store)

            // When
            withContext(SessionContextElement(scope)) {
                client.get("http://catalog.test/v1/items/42")
            }
            client.close()

            // Then
            val headers = capturedHeaders.get()
            assertEquals("session-1", headers[SessionHeaders.SESSION_ID])
            assertEquals("request-1", headers[SessionHeaders.PARENT_REQUEST_ID])
            val requestId = headers[SessionHeaders.REQUEST_ID]
            assertTrue(SessionIds.isValid(requestId))
            val event = store.events.single()
            assertEquals(SessionEventKind.OUT, event.kind)
            assertEquals("session-1", event.sessionId)
            assertEquals("request-1", event.parentRequestId)
            assertEquals(requestId, event.requestId)
            assertEquals("GET", event.method)
            assertEquals("/v1/items/42", event.path)
            assertEquals("catalog.test", event.target)
            assertEquals(200, event.status)
        }

    @Test
    fun `should leave an untracked call untouched`() =
        runBlocking {
            // Given
            val capturedHeaders = AtomicReference<Headers>()
            val engine = MockEngine { request ->
                capturedHeaders.set(request.headers)
                respond("ok", HttpStatusCode.OK)
            }
            val client = HttpClient(engine)
            client.installSessionPropagation()

            // When
            client.get("http://catalog.test/v1/items/42")
            client.close()

            // Then
            assertNull(capturedHeaders.get()[SessionHeaders.SESSION_ID])
        }
}
