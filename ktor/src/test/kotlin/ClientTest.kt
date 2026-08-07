package fr.shikkanime.ktor

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ClientTest {

    @Serializable
    data class TestData(val id: Int, val name: String, val active: Boolean = true)

    @RestController("/api/client-test")
    class ClientTestController : IController {
        @GetMapping
        fun getTestData(): ResponseEntity<TestData> =
            ResponseEntity.ok(TestData(1, "Client Test"))

        @PostMapping
        fun postTestData(@RequestBody body: TestData): ResponseEntity<TestData> =
            ResponseEntity.ok(body.copy(name = body.name + " Received"))
    }

    @Nested
    @DisplayName("tests for createHttpClient initialization and configuration")
    inner class ClientConfigurationTests {

        @Test
        fun `should create http client with timeout and content negotiation plugins`() {
            // Given / When
            val client = createHttpClient()

            // Then
            assertNotNull(client)
            assertNotNull(client.pluginOrNull(HttpTimeout))
            assertNotNull(client.pluginOrNull(ContentNegotiation))
            client.close()
        }
    }

    @Nested
    @DisplayName("tests for client request execution using Ktor server test host")
    inner class ClientRequestTests {

        @Test
        fun `should execute GET request and receive serialized response`() =
            testApplication {
                // Given
                application {
                    configureDefaultModules()
                    routing {
                        ControllerBinder.register(this, listOf(ClientTestController()))
                    }
                }

                val testClient = createClient {
                    install(ContentNegotiation) {
                        json(defaultJson)
                    }
                }

                // When
                val getResponse: HttpResponse = testClient.get("/api/client-test")

                // Then
                assertEquals(HttpStatusCode.OK, getResponse.status)
                val getBody = getResponse.bodyAsText()
                assertTrue(getBody.contains(""""name":"Client Test""""))
            }

        @Test
        fun `should execute POST request with JSON payload and receive serialized response`() =
            testApplication {
                // Given
                application {
                    configureDefaultModules()
                    routing {
                        ControllerBinder.register(this, listOf(ClientTestController()))
                    }
                }

                val testClient = createClient {
                    install(ContentNegotiation) {
                        json(defaultJson)
                    }
                }

                // When
                val postResponse: HttpResponse = testClient.post("/api/client-test") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"id":42,"name":"Client Input"}""")
                }

                // Then
                assertEquals(HttpStatusCode.OK, postResponse.status)
                val postBody = postResponse.bodyAsText()
                assertTrue(postBody.contains(""""id":42"""))
                assertTrue(postBody.contains(""""name":"Client Input Received""""))
            }
    }
}
