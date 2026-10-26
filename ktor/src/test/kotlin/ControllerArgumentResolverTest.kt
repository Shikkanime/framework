package fr.shikkanime.ktor

import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ControllerArgumentResolverTest {

    enum class TestStatus { ACTIVE, INACTIVE }

    class SampleController {
        fun sampleEndpoint(
            @QueryParam("name") name: String,
            @PathParam("id") id: Int
        ): ResponseEntity<String> =
            ResponseEntity.ok("Hello $name ($id)")

        fun typeEndpoint(
            @QueryParam("longVal") longVal: Long,
            @QueryParam("doubleVal") doubleVal: Double,
            @QueryParam("boolVal") boolVal: Boolean,
            @QueryParam("status") status: TestStatus,
            @QueryParam("uuid") uuid: Uuid,
            @QueryParam("optional", required = false) optional: String?
        ): ResponseEntity<String> =
            ResponseEntity.ok("ok")

        fun optionalEndpoint(
            @QueryParam("id", required = false) id: Uuid?
        ): ResponseEntity<String> =
            ResponseEntity.ok(id?.toString() ?: "none")

        fun headerEndpoint(
            @HttpHeader("If-None-Match") ifNoneMatch: String,
            @HttpHeader("If-Modified-Since", required = false) lastModified: String?
        ): ResponseEntity<String> =
            ResponseEntity.ok("$ifNoneMatch / ${lastModified ?: "none"}")
    }

    @Nested
    @DisplayName("tests for parameter resolution and type conversion")
    inner class ResolutionTests {

        @Test
        suspend fun `should resolve query and path parameters natively in suspend test`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.queryParameters["name"] } returns "Shikkanime"
            every { call.parameters["id"] } returns "123"

            val controller = SampleController()
            val function = SampleController::sampleEndpoint

            // When
            val args = ControllerArgumentResolver.resolve(call, function, controller)

            // Then
            assertEquals(3, args.size)
            assertTrue(args.values.contains(controller))
            assertTrue(args.values.contains("Shikkanime"))
            assertTrue(args.values.contains(123))
        }

        @Test
        suspend fun `should convert Long Double Boolean Enum and Uuid parameter types`() {
            // Given
            val targetUuid = Uuid.random()
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.queryParameters["longVal"] } returns "9876543210"
            every { call.request.queryParameters["doubleVal"] } returns "3.14159"
            every { call.request.queryParameters["boolVal"] } returns "true"
            every { call.request.queryParameters["status"] } returns "active"
            every { call.request.queryParameters["uuid"] } returns targetUuid.toString()
            every { call.request.queryParameters["optional"] } returns null

            val controller = SampleController()
            val function = SampleController::typeEndpoint

            // When
            val args = ControllerArgumentResolver.resolve(call, function, controller)

            // Then
            assertTrue(args.values.contains(9876543210L))
            assertTrue(args.values.contains(3.14159))
            assertTrue(args.values.contains(true))
            assertTrue(args.values.contains(TestStatus.ACTIVE))
            assertTrue(args.values.contains(targetUuid))
            assertTrue(args.values.contains(null))
        }

        @Test
        suspend fun `should refuse the request when a numeric or boolean conversion fails`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.queryParameters["longVal"] } returns "invalid_number"
            every { call.request.queryParameters["boolVal"] } returns "not_a_bool"

            val controller = SampleController()
            val function = SampleController::typeEndpoint

            // When
            val exception = assertThrows<InvalidParameterException> {
                ControllerArgumentResolver.resolve(call, function, controller)
            }

            // Then
            assertTrue(exception.message.contains("must be a valid"))
        }

        @Test
        suspend fun `should resolve an optional parameter to null when its value does not match its type`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.queryParameters["id"] } returns "not-a-uuid"

            val controller = SampleController()
            val function = SampleController::optionalEndpoint

            // When
            val args = ControllerArgumentResolver.resolve(call, function, controller)

            // Then
            assertTrue(args.values.contains(null))
        }
    }

    @Nested
    @DisplayName("tests for header parameter resolution")
    inner class HeaderResolutionTests {

        @Test
        suspend fun `should resolve a required header`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.headers["If-None-Match"] } returns "\"a1b2c3d4\""

            // When
            val args = ControllerArgumentResolver.resolve(
                call,
                SampleController::headerEndpoint,
                SampleController()
            )

            // Then
            val ifNoneMatch = SampleController::headerEndpoint.parameters.first { it.name == "ifNoneMatch" }
            assertEquals("\"a1b2c3d4\"", args[ifNoneMatch])
        }

        @Test
        suspend fun `should leave an absent optional header null`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.headers["If-None-Match"] } returns "\"a1b2c3d4\""
            every { call.request.headers["If-Modified-Since"] } returns null

            // When
            val args = ControllerArgumentResolver.resolve(
                call,
                SampleController::headerEndpoint,
                SampleController()
            )

            // Then
            val lastModified = SampleController::headerEndpoint.parameters
                .first { it.name == "lastModified" }
            assertTrue(!args.containsKey(lastModified) || args[lastModified] == null)
        }

        @Test
        suspend fun `should refuse an absent required header`() {
            // Given
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.request.headers["If-None-Match"] } returns null

            // When / Then
            assertThrows<InvalidParameterException> {
                runBlocking {
                    ControllerArgumentResolver.resolve(
                        call,
                        SampleController::headerEndpoint,
                        SampleController()
                    )
                }
            }
        }
    }
}
