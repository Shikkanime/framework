package fr.shikkanime.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HealthControllerTest {
    /** Not auto-registered by [configureDefaultModules]: registered manually in the coexistence test only. */
    @RestController("/api/pings")
    class PingController : IController {
        @GetMapping
        fun ping(): String =
            "pong"
    }

    private fun ApplicationTestBuilder.configureApplication() {
        application {
            configureDefaultModules()
        }
    }

    @Nested
    @DisplayName("tests for the auto-registered health endpoint")
    inner class HealthEndpointTests {
        @Test
        fun `should respond OK with HTTP 200 when only configureDefaultModules is applied`() =
            testApplication {
                // Given / When
                configureApplication()
                val response = client.get("/health")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("OK", response.bodyAsText())
            }
    }

    @Nested
    @DisplayName("tests for route coexistence with consumer controllers")
    inner class RouteCoexistenceTests {
        @Test
        fun `should keep serving consumer routes registered in a separate routing block`() =
            testApplication {
                // Given
                configureApplication()

                // When
                application {
                    routing { ControllerBinder.register(this, listOf(PingController())) }
                }
                val healthResponse = client.get("/health")
                val pingResponse = client.get("/api/pings")

                // Then
                assertEquals(HttpStatusCode.OK, healthResponse.status)
                assertEquals("OK", healthResponse.bodyAsText())
                assertEquals(HttpStatusCode.OK, pingResponse.status)
                assertEquals("pong", pingResponse.bodyAsText())
            }
    }
}
