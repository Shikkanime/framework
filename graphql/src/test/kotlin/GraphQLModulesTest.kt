package fr.shikkanime.graphql

import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.ktor.DefaultKtorGraphQLContextFactory
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.minutes

class GraphQLModulesTest {
    // Fixtures first: everything the tests below rely on.
    // graphql-java rejects a non-public query type, so the fixtures must stay public.
    private val config = GraphQLConfig(
        maxQueryDepth = 6,
        maxQueryComplexity = 100,
        requestBodyLimit = 4 * 1024,
        rateLimit = RateLimitConfig(limit = 5, refillPeriod = 5.minutes)
    )

    class PingQuery : Query {
        fun ping(): String = "pong"
    }

    class NestedQuery : Query {
        fun episodes(): EpisodeQuery = EpisodeQuery()
    }

    class EpisodeQuery {
        fun ping(): String = "pong"
    }

    private fun ApplicationTestBuilder.graphqlApplication(
        config: GraphQLConfig = this@GraphQLModulesTest.config
    ) {
        application {
            configureGraphQL(
                config = config,
                schemaPackages = listOf("fr.shikkanime.graphql"),
                schemaQueries = listOf(PingQuery(), NestedQuery())
            )
        }
    }

    private suspend fun ApplicationTestBuilder.post(body: String): HttpResponse =
        client.post(config.normalizedPath) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.post(body: String, acceptEncoding: String): HttpResponse =
        client.post(config.normalizedPath) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.AcceptEncoding, acceptEncoding)
            setBody(body)
        }

    private fun query(vararg fields: String): String {
        val selection = if (fields.isEmpty()) "ping" else fields.joinToString(" ")
        return """{"query":"{ $selection }"}"""
    }

    private fun aliasFields(count: Int): String =
        (1..count).joinToString(" ") { index -> "a$index: ping" }

    private suspend fun ApplicationTestBuilder.bodyOf(response: HttpResponse): String =
        response.bodyAsText()

    @Nested
    @DisplayName("Given a normal query")
    inner class GivenNormalQuery {
        @Test
        fun `should be answered`() = testApplication {
            // Given
            graphqlApplication()

            // When
            val response = post(query())

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(bodyOf(response).contains("pong"))
        }

        @Test
        fun `should serve a gzip request`() = testApplication {
            // Compression stays on: the body limit measures the decoded stream, so a compressed
            // payload cannot slip past it. Ktor compresses the response, so the client has to
            // decompress it — which also proves the plugin decompressed the *request* correctly
            // rather than handing the engine raw bytes.
            // Given
            graphqlApplication()

            // When
            val response = client.post(config.normalizedPath) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.AcceptEncoding, "gzip")
                setBody(query())
            }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding], "response must be compressed")

            val decompressed = GZIPInputStream(response.bodyAsChannel().toInputStream())
                .readBytes()
                .decodeToString()

            assertTrue(decompressed.contains("pong"), "got $decompressed")
        }
    }

    @Nested
    @DisplayName("Given introspection")
    inner class GivenIntrospection {
        @Test
        fun `should be public`() = testApplication {
            // The Swagger UI is already public, so the GraphQL schema is too.
            // Given
            graphqlApplication()

            // When
            val response = post("""{"query":"{ __schema { types { name } } }"}""")

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(bodyOf(response).contains("__schema"))
        }
    }

    @Nested
    @DisplayName("Given an oversized body")
    inner class GivenOversizedBody {
        @Test
        fun `should be refused`() = testApplication {
            // Given — larger than the configured 4 KiB limit.
            graphqlApplication()

            // When
            val padding = "x".repeat(8 * 1024)
            val response = post("""{"query":"{ ping }","variables":{"pad":"$padding"}}""")

            // Then
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }
    }

    @Nested
    @DisplayName("Given a hostile document")
    inner class GivenHostileDocument {
        @Test
        fun `should refuse an alias bomb`() = testApplication {
            // Given
            graphqlApplication()

            // When
            val response = post(query(*aliasFields(200).split(" ").toTypedArray()))

            // Then
            assertTrue(
                bodyOf(response).contains("errors"),
                "expected a refusal, got ${response.status} ${bodyOf(response)}"
            )
        }

        @Test
        fun `should refuse a deep document`() = testApplication {
            // Given
            graphqlApplication()

            // When
            val deep = "episodes { ".repeat(12) + "ping" + " }".repeat(12)
            val response = post("""{"query":"{ $deep }"}""")

            // Then
            assertTrue(
                bodyOf(response).contains("errors"),
                "expected a refusal, got ${response.status} ${bodyOf(response)}"
            )
        }
    }

    @Nested
    @DisplayName("Given a batch request")
    inner class GivenBatchRequest {
        @Test
        fun `should be refused`() = testApplication {
            // A batch is not one document: graphql-java would execute every element separately, so
            // a check that parses a single document would never see the others.
            // Given
            graphqlApplication()

            // When
            val batch = "[" + (1..10).joinToString(",") { """{"query":"{ ping }"}""" } + "]"
            val response = post(batch)

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(bodyOf(response).contains("Batch"), "got ${bodyOf(response)}")
        }
    }

    @Nested
    @DisplayName("Given an unreadable document")
    inner class GivenUnreadableDocument {
        @Test
        fun `should be refused instead of reaching the engine`() = testApplication {
            // Malformed syntax is the shape an attacker uses to slip past a check that only
            // understands valid documents.
            // Given
            graphqlApplication()

            // When
            val response = post("""{"query":"{ ping "}""")

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(bodyOf(response).contains("could not be read"), "got ${bodyOf(response)}")
        }

        @Test
        fun `should refuse a body carrying no query`() = testApplication {
            // Given
            graphqlApplication()

            // When
            val response = post("""{"variables":{}}""")

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Nested
    @DisplayName("Given introspection disabled")
    inner class GivenIntrospectionDisabled {
        @Test
        fun `should refuse an introspection query`() = testApplication {
            // Given
            graphqlApplication(GraphQLConfig(
                maxQueryDepth = 6,
                maxQueryComplexity = 100,
                requestBodyLimit = 4 * 1024,
                introspectionEnabled = false,
                rateLimit = RateLimitConfig(limit = 5, refillPeriod = 5.minutes)
            ))

            // When
            val response = post("""{"query":"{ __schema { types { name } } }"}""")

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(bodyOf(response).contains("introspection"), "got ${bodyOf(response)}")
        }

        @Test
        fun `should still answer a normal query`() = testApplication {
            // Given
            graphqlApplication(GraphQLConfig(
                maxQueryDepth = 6,
                maxQueryComplexity = 100,
                requestBodyLimit = 4 * 1024,
                introspectionEnabled = false,
                rateLimit = RateLimitConfig(limit = 5, refillPeriod = 5.minutes)
            ))

            // When
            val response = post(query())

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(bodyOf(response).contains("pong"), "got ${bodyOf(response)}")
        }
    }

    @Nested
    @DisplayName("Given the rate limiter")
    inner class GivenRateLimiter {
        @Test
        fun `should answer 429 once the bucket is empty`() = testApplication {
            // The bucket holds `limit` cost units and refills on a 5 minute period, so a ping
            // costs one and the sixth request inside the period has nothing left.
            // Given
            graphqlApplication()

            // When
            val statuses = (1..config.rateLimit.limit + 2).map { post(query()).status }

            // Then
            assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
        }

        @Test
        fun `should charge a heavy document more than a light one`() = testApplication {
            // This is the point of requestWeight: a bucket of 5 units fits five pings but only
            // two nested documents, since each costs two.
            // Given
            graphqlApplication()

            // When
            val lightStatuses = (1..config.rateLimit.limit).map { post(query()).status }
            val heavyStatuses = (1..config.rateLimit.limit).map {
                post(query("episodes { ping }")).status
            }

            // Then
            assertTrue(
                lightStatuses.none { it == HttpStatusCode.TooManyRequests },
                "light queries must fit the bucket, got $lightStatuses"
            )
            assertTrue(
                heavyStatuses.any { it == HttpStatusCode.TooManyRequests },
                "heavy queries must exhaust it faster, got $heavyStatuses"
            )
        }
    }
}
