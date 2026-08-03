package fr.shikkanime.ktor

import io.ktor.server.application.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ControllerArgumentResolverTest {

    class SampleController {
        fun sampleEndpoint(
            @QueryParam("name") name: String,
            @PathParam("id") id: Int
        ): ResponseEntity<String> = ResponseEntity.ok("Hello $name ($id)")
    }

    @Nested
    @DisplayName("tests for asynchronous parameter resolution")
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
    }
}
