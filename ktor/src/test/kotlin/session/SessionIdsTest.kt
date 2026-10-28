package fr.shikkanime.ktor.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class SessionIdsTest {

    @Nested
    @DisplayName("tests for identifier validation")
    inner class ValidationTests {

        @Test
        fun `should accept a UUID and a short path-safe identifier`() {
            // Given / When / Then
            assertTrue(SessionIds.isValid(UUID.randomUUID().toString()))
            assertTrue(SessionIds.isValid("session-1"))
            assertTrue(SessionIds.isValid("a".repeat(8)))
            assertTrue(SessionIds.isValid("a".repeat(64)))
        }

        @Test
        fun `should reject short, long, and path-unsafe identifiers`() {
            // Given / When / Then
            assertFalse(SessionIds.isValid("short"))
            assertFalse(SessionIds.isValid("a".repeat(65)))
            assertFalse(SessionIds.isValid("../../etc/passwd"))
            assertFalse(SessionIds.isValid("session.id"))
            assertFalse(SessionIds.isValid("session 1"))
            assertFalse(SessionIds.isValid(null))
        }
    }

    @Nested
    @DisplayName("tests for identifier generation")
    inner class GenerationTests {

        @Test
        fun `should keep a valid session identifier`() {
            // Given
            val value = "session-1"

            // When
            val resolved = SessionIds.resolveSession(value)

            // Then
            assertEquals(value, resolved)
        }

        @Test
        fun `should replace an unsafe session identifier`() {
            // Given / When
            val resolved = SessionIds.resolveSession("../escape")

            // Then
            assertNotEquals("../escape", resolved)
            assertTrue(SessionIds.isValid(resolved))
        }

        @Test
        fun `should generate a version 4 request identifier`() {
            // Given / When
            val identifier = UUID.fromString(SessionIds.newRequestId())

            // Then
            assertEquals(4, identifier.version())
            assertEquals(2, identifier.variant())
        }
    }

    @Nested
    @DisplayName("tests for service names")
    inner class ServiceTests {

        @Test
        fun `should keep a simple service name`() {
            // Given / When / Then
            assertEquals("api", SessionIds.service("api"))
            assertEquals("api_v1", SessionIds.service("api_v1"))
        }

        @Test
        fun `should fall back to default for an unsafe service name`() {
            // Given / When / Then
            assertEquals("default", SessionIds.service("api/v1"))
            assertEquals("default", SessionIds.service("a".repeat(33)))
            assertEquals("default", SessionIds.service(null))
        }
    }
}
