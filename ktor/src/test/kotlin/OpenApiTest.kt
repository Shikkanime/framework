package fr.shikkanime.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OpenApiTest {

    @Serializable
    data class MovieDto(val id: Int, val title: String)

    @RestController("/api/movies")
    class MovieController : IController {

        @Operation(
            summary = "Get movie by ID",
            description = "Returns a single movie object",
            tags = ["Movies"]
        )
        @ApiResponses([
            ApiResponse(status = 200, description = "Movie found", responseType = MovieDto::class),
            ApiResponse(status = 404, description = "Movie not found", responseType = String::class)
        ])
        @GetMapping("/{id}")
        fun getMovie(
            @PathParam("id", required = true) id: Int
        ): ResponseEntity<MovieDto> =
            ResponseEntity.ok(MovieDto(id, "Inception"))

        @Operation(
            summary = "Search movies",
            description = "Search movies by query string"
        )
        @GetMapping("/search")
        fun search(
            @QueryParam("query", required = false) query: String?
        ): ResponseEntity<List<MovieDto>> =
            ResponseEntity.ok(listOf(MovieDto(1, "Inception")))
    }

    @Nested
    @DisplayName("tests for swagger and openapi configuration")
    inner class SwaggerRouteTests {

        @Test
        fun `should register swagger endpoint and return HTTP 200`() =
            testApplication {
                // Given
                application {
                    configureDefaultModules()
                    routing {
                        configureSwaggerRoute("Movie API", "1.0.0")
                        ControllerBinder.register(this, listOf(MovieController()))
                    }
                }

                // When
                val response = client.get("/swagger")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                val text = response.bodyAsText()
                assertTrue(text.isNotEmpty())
            }
    }
}
