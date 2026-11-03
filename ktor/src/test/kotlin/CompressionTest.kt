package fr.shikkanime.ktor

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("tests for the response compression")
class CompressionTest {

    @Test
    @DisplayName("should compress a response above the threshold when the client accepts gzip")
    fun `should compress a response above the threshold when the client accepts gzip`() =
        testApplication {
            application {
                configureDefaultModules()
                routing {
                    get("/large") {
                        call.respondText("x".repeat(512))
                    }
                }
            }

            val response = client.get("/large") {
                header(HttpHeaders.AcceptEncoding, "gzip")
            }

            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])

            val decoded = GZIPInputStream(
                ByteArrayInputStream(response.bodyAsBytes())
            ).readBytes().decodeToString()

            assertEquals(512, decoded.length)
        }

    @Test
    @DisplayName("should not compress a response when the client accepts no encoding")
    fun `should not compress a response when the client accepts no encoding`() =
        testApplication {
            application {
                configureDefaultModules()
                routing {
                    get("/large") {
                        call.respondText("x".repeat(512))
                    }
                }
            }

            val response = client.get("/large") {
                header(HttpHeaders.AcceptEncoding, "identity")
            }

            assertNull(response.headers[HttpHeaders.ContentEncoding])
            assertEquals(512, response.bodyAsBytes().decodeToString().length)
        }
}
