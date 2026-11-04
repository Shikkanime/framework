package fr.shikkanime.ktor

import fr.shikkanime.core.pagination.Page
import fr.shikkanime.ktor.pagination.PageDto
import fr.shikkanime.ktor.pagination.PageQuery
import fr.shikkanime.ktor.pagination.toDto
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("tests for the paged responses")
class PaginationTest {

    @Serializable
    data class Book(val id: Int, val title: String)

    @Suppress("unused") // Controller methods are bound reflectively by ControllerBinder.
    @RestController("/api/paged-books")
    class PagedBookController : IController {
        @GetMapping
        fun page(): PageDto<Book> =
            Page(
                data = listOf(Book(1, "Dune"), Book(2, "Foundation")),
                page = 2,
                size = 2,
                totalElements = 5
            ).toDto { it }
    }

    @Suppress("unused") // Controller methods are bound reflectively by ControllerBinder.
    @RestController("/api/paged-query")
    class PagedQueryController : IController {
        @GetMapping
        fun page(@Valid @ModelAttribute query: PageQuery): String =
            "${query.page}/${query.size}"
    }

    private fun ApplicationTestBuilder.registerController() {
        application {
            configureDefaultModules()
            routing { ControllerBinder.register(this, listOf(PagedBookController())) }
        }
    }

    @Nested
    @DisplayName("tests for a controller returning a page")
    inner class ControllerPageTests {
        @Test
        @DisplayName("should serialize the elements and the pagination bounds")
        fun `should serialize the elements and the pagination bounds`() =
            testApplication {
                // Given
                registerController()

                // When
                val response = client.get("/api/paged-books")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    """{"data":[{"id":1,"title":"Dune"},{"id":2,"title":"Foundation"}],""" +
                        """"page":2,"size":2,"totalPages":3,"totalElements":5}""",
                    response.bodyAsText()
                )
            }
    }

    @Nested
    @DisplayName("tests for the page mapping")
    inner class PageMappingTests {
        @Test
        @DisplayName("should map the elements and keep the bounds")
        fun `should map the elements and keep the bounds`() {
            // Given
            val page = Page(data = listOf(1, 2, 3), page = 2, size = 3, totalElements = 8)

            // When
            val dto = page.toDto { it.toString() }

            // Then
            assertEquals(listOf("1", "2", "3"), dto.data)
            assertEquals(2, dto.page)
            assertEquals(3, dto.size)
            assertEquals(3, dto.totalPages)
            assertEquals(8L, dto.totalElements)
        }
    }

    @Nested
    @DisplayName("tests for the bound page query")
    inner class PageQueryTests {

        private fun ApplicationTestBuilder.registerPagedQueryController() {
            application {
                configureDefaultModules()
                routing { ControllerBinder.register(this, listOf(PagedQueryController())) }
            }
        }

        @Test
        fun `should bind the page and size query parameters`() =
            testApplication {
                // Given / When
                registerPagedQueryController()
                val response = client.get("/api/paged-query?page=3&size=50")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("3/50", response.bodyAsText())
            }

        @Test
        fun `should keep the defaults when the parameters are absent`() =
            testApplication {
                // Given / When
                registerPagedQueryController()
                val response = client.get("/api/paged-query")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("1/20", response.bodyAsText())
            }

        @Test
        fun `should refuse a page below one`() =
            testApplication {
                // Given / When
                registerPagedQueryController()
                val response = client.get("/api/paged-query?page=0")

                // Then
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }

        @Test
        fun `should refuse a size above the maximum`() =
            testApplication {
                // Given / When
                registerPagedQueryController()
                val response = client.get("/api/paged-query?size=500")

                // Then
                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
    }
}
