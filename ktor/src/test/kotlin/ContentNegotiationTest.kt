package fr.shikkanime.ktor

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class ContentNegotiationTest {

    @Serializable
    data class SamplePayload(
        val id: Int,
        val title: String,
        val active: Boolean = true,
        val nullableField: String? = null
    )

    @Serializable
    data class ProtoPayload(
        val id: Int,
        val title: String,
        val active: Boolean = true
    )

    @Serializable
    data class ExtendedPayload(
        val id: Int,
        val title: String,
        val extra: String = "extra"
    )

    @RestController("/api/test-negotiation")
    class NegotiationTestController : IController {
        @GetMapping
        fun getPayload(): ResponseEntity<SamplePayload> =
            ResponseEntity.ok(SamplePayload(id = 1, title = "Default Title"))

        @PostMapping
        fun echoPayload(@RequestBody payload: SamplePayload): ResponseEntity<SamplePayload> =
            ResponseEntity.ok(payload)
    }

    @Nested
    @DisplayName("tests for defaultJson configuration")
    inner class JsonConfigurationTests {

        @Test
        fun `should encode defaults by including default property values`() {
            // Given
            val payload = SamplePayload(id = 1, title = "Test")

            // When
            val serialized = defaultJson.encodeToString(payload)

            // Then
            assertTrue(serialized.contains(""""active":true"""))
        }

        @Test
        fun `should omit explicit nulls when encoding`() {
            // Given
            val payload = SamplePayload(id = 1, title = "Test", nullableField = null)

            // When
            val serialized = defaultJson.encodeToString(payload)

            // Then
            assertFalse(serialized.contains("nullableField"))
        }

        @Test
        fun `should ignore unknown keys when deserializing json`() {
            // Given
            val jsonWithExtraKey = """{"id":1,"title":"Test","unknownKey":"ignoreMe"}"""

            // When
            val deserialized = defaultJson.decodeFromString<SamplePayload>(jsonWithExtraKey)

            // Then
            assertEquals(1, deserialized.id)
            assertEquals("Test", deserialized.title)
            assertTrue(deserialized.active)
        }

        @Test
        fun `should be lenient when parsing json`() {
            // Given
            val lenientJson = """{id:1,title:"Test"}"""

            // When
            val deserialized = defaultJson.decodeFromString<SamplePayload>(lenientJson)

            // Then
            assertEquals(1, deserialized.id)
            assertEquals("Test", deserialized.title)
        }
    }

    @Nested
    @DisplayName("tests for defaultProtoBuf configuration")
    inner class ProtoBufConfigurationTests {

        @Test
        fun `should encode and decode defaults correctly`() {
            // Given
            val payload = ProtoPayload(id = 42, title = "Protobuf")

            // When
            val bytes = defaultProtoBuf.encodeToByteArray(payload)
            val decoded = defaultProtoBuf.decodeFromByteArray<ProtoPayload>(bytes)

            // Then
            assertEquals(payload.id, decoded.id)
            assertEquals(payload.title, decoded.title)
            assertEquals(payload.active, decoded.active)
        }
    }

    @Nested
    @DisplayName("tests for defaultCbor configuration")
    inner class CborConfigurationTests {

        @Test
        fun `should encode and decode defaults correctly`() {
            // Given
            val payload = SamplePayload(id = 100, title = "CBOR")

            // When
            val bytes = defaultCbor.encodeToByteArray(payload)
            val decoded = defaultCbor.decodeFromByteArray<SamplePayload>(bytes)

            // Then
            assertEquals(payload.id, decoded.id)
            assertEquals(payload.title, decoded.title)
            assertEquals(payload.active, decoded.active)
        }

        @Test
        fun `should ignore unknown keys in CBOR format`() {
            // Given
            val extendedBytes = defaultCbor.encodeToByteArray(ExtendedPayload(1, "CBOR Extra", "extra"))

            // When
            val decoded = defaultCbor.decodeFromByteArray<SamplePayload>(extendedBytes)

            // Then
            assertEquals(1, decoded.id)
            assertEquals("CBOR Extra", decoded.title)
        }
    }

    @Nested
    @DisplayName("tests for server-client content negotiation integration")
    inner class ServerClientIntegrationTests {

        @Test
        fun `should exchange JSON body with unknown keys successfully on server`() =
            testApplication {
                // Given
                application {
                    configureDefaultModules()
                    routing {
                        ControllerBinder.register(this, listOf(NegotiationTestController()))
                    }
                }

                // When - sending JSON with unknown key to server
                val response = client.post("/api/test-negotiation") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"id":99,"title":"Incoming","unknownField":123}""")
                }

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                val bodyText = response.bodyAsText()
                assertTrue(bodyText.contains(""""id":99"""))
                assertTrue(bodyText.contains(""""title":"Incoming""""))
                assertTrue(bodyText.contains(""""active":true"""))
            }

        @Test
        fun `should create http client with default configurations`() {
            // Given / When
            val httpClient = createHttpClient()

            // Then
            assertNotNull(httpClient)
            httpClient.close()
        }
    }
}
