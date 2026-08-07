package fr.shikkanime.ktor

import fr.shikkanime.validator.exceptions.ObjectNotValidException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ControllerRegistrationTest {

    @Serializable
    data class Book(val id: Int, val title: String)

    @RestController("/api/books")
    class BookController : IController {
        @GetMapping("/{id}")
        fun getBook(@PathParam("id") id: Int): ResponseEntity<Book> =
            ResponseEntity.ok(Book(id, "Dune"))

        @GetMapping("/search")
        fun search(@QueryParam("q") q: String): ResponseEntity<List<Book>> =
            ResponseEntity.ok(listOf(Book(1, "Dune"), Book(2, "Foundation")))

        @GetMapping("/book")
        fun book(): Book =
            Book(1, "Dune")

        @PostMapping
        fun create(@RequestBody book: Book): ResponseEntity<Book> =
            ResponseEntity.created(book)

        @PatchMapping("/{id}")
        fun update(@PathParam("id") id: Int, @RequestBody book: Book): ResponseEntity<Book> =
            ResponseEntity.ok(book.copy(id = id))

        @GetMapping("/ok")
        fun ok(): ResponseEntity<Book> =
            ResponseEntity.ok(Book(1, "Dune"))

        @GetMapping("/created")
        fun created(): ResponseEntity<Book> =
            ResponseEntity.created(Book(2, "Foundation"))

        @GetMapping("/bad")
        fun bad(): ResponseEntity<String> =
            ResponseEntity.badRequest("Invalid input")

        @GetMapping("/notfound")
        fun notFound(): ResponseEntity<String> =
            ResponseEntity.notFound("Missing book")

        @GetMapping("/conflict")
        fun conflict(): ResponseEntity<String> =
            ResponseEntity.conflict("Book already exists")

        @GetMapping("/error")
        fun error(): ResponseEntity<String> =
            ResponseEntity.internalServerError("Unexpected")

        @GetMapping("/plain")
        fun plain(): String =
            "Hello World"

        @GetMapping("/boom")
        fun boom(): String =
            throw RuntimeException("boom")

        @GetMapping("/invalid")
        fun invalid(): String =
            throw ObjectNotValidException("title must not be blank")
    }

    /** Not annotated with [RestController]: must not be registered. */
    class PlainController {
        fun get(): ResponseEntity<String> =
            ResponseEntity.ok("nope")
    }

    /** [RestController] with a method that has no mapping annotation: must not be bound. */
    @RestController("/api/extra")
    class ExtraController : IController {
        fun unannotated(): String =
            "hidden"
    }

    private fun ApplicationTestBuilder.registerControllers(controllers: List<Any>) {
        application {
            configureDefaultModules()
            routing { ControllerBinder.register(this, controllers) }
        }
    }

    private fun HttpResponse.contentTypeValue(): String? =
        headers[HttpHeaders.ContentType]?.substringBefore(";")

    @Nested
    @DisplayName("tests for controller registration")
    inner class RegistrationTests {

        @Test
        fun `should register every mapped endpoint of an annotated controller`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))

                // Then
                assertEquals(HttpStatusCode.OK, client.get("/api/books/ok").status)
                assertEquals(HttpStatusCode.Created, client.get("/api/books/created").status)
                assertEquals(HttpStatusCode.BadRequest, client.get("/api/books/bad").status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/books/notfound").status)
                assertEquals(HttpStatusCode.Conflict, client.get("/api/books/conflict").status)
                assertEquals(HttpStatusCode.InternalServerError, client.get("/api/books/error").status)
            }

        @Test
        fun `should not register a class without rest controller annotation`() =
            testApplication {
                // Given / When
                registerControllers(listOf(PlainController()))

                // Then
                assertEquals(HttpStatusCode.NotFound, client.get("/plain").status)
            }

        @Test
        fun `should not bind a method without a mapping annotation`() =
            testApplication {
                // Given / When
                registerControllers(listOf(ExtraController()))

                // Then
                assertEquals(HttpStatusCode.NotFound, client.get("/api/extra/unannotated").status)
            }
    }

    @Nested
    @DisplayName("tests for registered responses")
    inner class ResponseTests {

        @Test
        fun `should serialize ok response with HTTP 200 and a JSON body`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/ok")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json", response.contentTypeValue())
                assertEquals("""{"id":1,"title":"Dune"}""", response.bodyAsText())
            }

        @Test
        fun `should serialize a directly returned entity with HTTP 200 and a JSON body`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/book")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json", response.contentTypeValue())
                assertEquals("""{"id":1,"title":"Dune"}""", response.bodyAsText())
            }

        @Test
        fun `should return created response with HTTP 201`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/created")

                // Then
                assertEquals(HttpStatusCode.Created, response.status)
                assertEquals("""{"id":2,"title":"Foundation"}""", response.bodyAsText())
            }

        @Test
        fun `should return badRequest response with HTTP 400`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/bad")

                // Then
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("Invalid input", response.bodyAsText())
            }

        @Test
        fun `should return notFound response with HTTP 404`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/notfound")

                // Then
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertEquals("Missing book", response.bodyAsText())
            }

        @Test
        fun `should return conflict response with HTTP 409`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/conflict")

                // Then
                assertEquals(HttpStatusCode.Conflict, response.status)
                assertEquals("Book already exists", response.bodyAsText())
            }

        @Test
        fun `should return internalServerError response with HTTP 500`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/error")

                // Then
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertEquals("Unexpected", response.bodyAsText())
            }

        @Test
        fun `should respond plain value with HTTP 200`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/plain")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Hello World", response.bodyAsText())
            }

        @Test
        fun `should return error message on unexpected exception with HTTP 500`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/boom")

                // Then
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertTrue(response.bodyAsText().contains("An unexpected error occurred"))
            }

        @Test
        fun `should return error message on validation failure with HTTP 400`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/invalid")

                // Then
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("title must not be blank"))
            }
    }

    @Nested
    @DisplayName("tests for parameter binding and HTTP methods")
    inner class ParameterAndMethodTests {

        @Test
        fun `should bind a path parameter`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/42")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("""{"id":42,"title":"Dune"}""", response.bodyAsText())
            }

        @Test
        fun `should bind query parameters`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/search?q=Dune")

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    """[{"id":1,"title":"Dune"},{"id":2,"title":"Foundation"}]""",
                    response.bodyAsText()
                )
            }

        @Test
        fun `should create resource with POST and a JSON body`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.post("/api/books") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"id":5,"title":"The Martian"}""")
                }

                // Then
                assertEquals(HttpStatusCode.Created, response.status)
                assertEquals("""{"id":5,"title":"The Martian"}""", response.bodyAsText())
            }

        @Test
        fun `should update resource with PATCH and a JSON body`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.patch("/api/books/7") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"id":7,"title":"Neuromancer"}""")
                }

                // Then
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("""{"id":7,"title":"Neuromancer"}""", response.bodyAsText())
            }
    }

    @Nested
    @DisplayName("tests for returned content types")
    inner class ContentTypeTests {

        @Test
        fun `should return entity as JSON when accepted`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/book") { accept(ContentType.Application.Json) }

                // Then
                assertEquals("application/json", response.contentTypeValue())
                assertEquals("""{"id":1,"title":"Dune"}""", response.bodyAsText())
            }

        @Test
        fun `should return entity as protobuf when accepted`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/2") { accept(ContentType.Application.ProtoBuf) }

                // Then
                assertTrue(response.contentTypeValue()!!.startsWith("application/protobuf"))
                assertTrue(response.bodyAsBytes().isNotEmpty())
            }

        @Test
        fun `should return entity as cbor when accepted`() =
            testApplication {
                // Given / When
                registerControllers(listOf(BookController()))
                val response = client.get("/api/books/3") { accept(ContentType.parse("application/cbor")) }

                // Then
                assertTrue(response.contentTypeValue()!!.startsWith("application/cbor"))
                assertTrue(response.bodyAsBytes().isNotEmpty())
            }
    }
}
