package fr.shikkanime.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HealthControllerTest {
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
}
