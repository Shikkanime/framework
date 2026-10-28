package fr.shikkanime.ktor.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class SessionContextTest {

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

    private fun scope(store: SessionStore? = null): SessionScope =
        SessionScope("session-1", "request-1", service = "test", store = store)

    @Nested
    @DisplayName("tests for the thread-local scope")
    inner class ThreadLocalTests {

        @Test
        fun `should start without a scope`() {
            // Given / When / Then
            assertNull(SessionContext.current())
        }

        @Test
        fun `should restore the previous scope after a nested block`() {
            // Given
            val outer = scope()
            val inner = outer.copy(requestId = "request-2")

            // When / Then
            SessionContext.with(outer) {
                assertSame(outer, SessionContext.current())
                SessionContext.with(inner) {
                    assertSame(inner, SessionContext.current())
                }
                assertSame(outer, SessionContext.current())
            }
            assertNull(SessionContext.current())
        }

        @Test
        fun `should append an event to the current session`() {
            // Given
            val store = RecordingStore()

            // When
            SessionContext.with(scope(store)) {
                SessionContext.event("cache.miss", mapOf("key" to "anime-1"))
            }

            // Then
            val event = store.events.single()
            assertEquals(SessionEventKind.EVENT, event.kind)
            assertEquals("cache.miss", event.name)
            assertEquals("anime-1", event.data["key"])
            assertEquals("session-1", event.sessionId)
        }
    }

    @Nested
    @DisplayName("tests for coroutine propagation")
    inner class CoroutineTests {

        @Test
        fun `should keep the scope across a dispatcher switch`() =
            runBlocking {
                // Given
                val scope = scope()

                // When / Then
                withContext(SessionContextElement(scope)) {
                    withContext(Dispatchers.IO) {
                        assertSame(scope, SessionContext.current())
                    }
                }
                assertNull(SessionContext.current())
            }
    }
}
