package fr.shikkanime.ktor

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ResponseEntityTest {

    @Nested
    @DisplayName("tests for ResponseEntity factory methods")
    inner class FactoryMethodTests {
        @Test
        fun `should create ok response with HTTP 200`() {
            // Given
            val payload = "Success"

            // When
            val response = ResponseEntity.ok(payload)

            // Then
            assertEquals(payload, response.body)
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `should create created response with HTTP 201`() {
            // Given
            val payload = mapOf("id" to 42)

            // When
            val response = ResponseEntity.created(payload)

            // Then
            assertEquals(payload, response.body)
            assertEquals(HttpStatusCode.Created, response.status)
        }

        @Test
        fun `should create badRequest response with HTTP 400`() {
            // Given
            val errorMsg = "Invalid input"

            // When
            val response = ResponseEntity.badRequest(errorMsg)

            // Then
            assertEquals(errorMsg, response.body)
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `should create notFound response with HTTP 404`() {
            // Given
            val errorMsg = "Resource missing"

            // When
            val response = ResponseEntity.notFound(errorMsg)

            // Then
            assertEquals(errorMsg, response.body)
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        @Test
        fun `should create internalServerError response with HTTP 500`() {
            // Given
            val errorMsg = "Server crash"

            // When
            val response = ResponseEntity.internalServerError(errorMsg)

            // Then
            assertEquals(errorMsg, response.body)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }
}
