package fr.shikkanime.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

    @Nested
    @DisplayName("tests for the binary, redirect and not-modified factories")
    inner class ContentFactoryTests {

        @Test
        fun `should serve a binary payload with its cache metadata`() {
            // Given
            val payload = byteArrayOf(1, 2, 3)

            // When
            val response = ResponseEntity.binary(payload, ContentType.Image.PNG, "v1", "max-age=60")

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(payload.toList(), (response.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().toList())
            assertEquals("\"v1\"", response.body.headers[HttpHeaders.ETag])
            assertEquals("max-age=60", response.body.headers[HttpHeaders.CacheControl])
        }

        @Test
        fun `should redirect to the given location`() {
            // When
            val response = ResponseEntity.redirect("/v1/images/a/b/c.webp")

            // Then
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/v1/images/a/b/c.webp", response.body.headers[HttpHeaders.Location])
        }

        @Test
        fun `should answer not modified with the revalidated validator`() {
            // When
            val response = ResponseEntity.notModified("v1")

            // Then
            assertEquals(HttpStatusCode.NotModified, response.status)
            assertEquals("\"v1\"", response.body.headers[HttpHeaders.ETag])
        }
    }
}
