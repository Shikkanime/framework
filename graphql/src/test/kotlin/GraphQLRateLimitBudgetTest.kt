package fr.shikkanime.graphql

import com.expediagroup.graphql.server.operations.Query
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regressions on how the endpoint behaves with the shipped defaults.
 *
 * Each test pins a behaviour that the security stack must not break on the way to refusing hostile
 * documents: introspection is public and must stay usable, and a legitimate query priced above the
 * bucket must still be answerable.
 */
class GraphQLRateLimitBudgetTest {
    // Fixtures.
    private val defaults = GraphQLConfig()

    class PingQuery : Query {
        fun ping(): String = "pong"
    }

    private fun ApplicationTestBuilder.graphql(config: GraphQLConfig) {
        application {
            configureGraphQL(
                config = config,
                schemaPackages = listOf("fr.shikkanime.graphql"),
                schemaQueries = listOf(PingQuery())
            )
        }
    }

    private suspend fun ApplicationTestBuilder.post(body: String): HttpResponse =
        client.post(defaults.normalizedPath) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun aliases(count: Int): String =
        "{ " + (1..count).joinToString(" ") { "a$it: ping" } + " }"

    @Nested
    @DisplayName("Given a schema introspection query")
    inner class GivenIntrospectionQuery {
        @Test
        fun `should be served with the default configuration`() = testApplication {
            // Introspection is exempt from the document bounds because it is deep and expensive by
            // nature, but the exemption must not leave it priced above the rate limit bucket: the
            // weight would then exceed the bucket and every introspection request would be
            // answered with 429, which breaks GraphiQL and every codegen client.
            // Given
            graphql(defaults)

            // When
            val response = post("""{"query":"{ __schema { types { name } } }"}""")

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("__schema"))
        }

        @Test
        fun `should stay served when repeated`() = testApplication {
            // A single request could fit by luck. The bucket must absorb the realistic number of
            // introspection calls a codegen client makes.
            // Given
            graphql(defaults)

            // When
            val statuses = (1..10).map {
                post("""{"query":"{ __schema { types { name } } }"}""").status
            }

            // Then
            assertTrue(
                statuses.none { it == HttpStatusCode.TooManyRequests },
                "introspection must not exhaust the bucket, got $statuses"
            )
        }
    }

    @Nested
    @DisplayName("Given a legitimate query priced above the bucket")
    inner class GivenAffordableQuery {
        @Test
        fun `should not be permanently rejected`() = testApplication {
            // The bounds promise up to `maxQueryComplexity`; the bucket must therefore hold at least
            // one document at that price. Otherwise the advertised range is unusable and the client
            // sees 429 for a query the bounds accept.
            // Given
            graphql(defaults)

            // When — cost 150 sits between the old default bucket and the default complexity bound,
            // repeated to prove the limiter does not lock the client out for good.
            val body = """{"query":"${aliases(150)}"}"""
            val statuses = (1..5).map { post(body).status }

            // Then
            assertTrue(
                statuses.all { it == HttpStatusCode.OK },
                "a query within the complexity bound must stay answerable, got $statuses"
            )
        }

        @Test
        fun `should not lock out a cheap query that follows`() = testApplication {
            // One heavy request must not spend the whole bucket: a client that sends one big query
            // then a trivial one has to get both answered.
            // Given
            graphql(defaults)

            // When
            post("""{"query":"${aliases(150)}}"}""")
            val cheap = post("""{"query":"{ ping }"}""").status

            // Then
            assertEquals(HttpStatusCode.OK, cheap)
        }
    }

    @Nested
    @DisplayName("Given a document the endpoint refuses")
    inner class GivenRefusedDocument {
        @Test
        fun `should not spend the whole bucket`() = testApplication {
            // Charging a refusal the full bucket lets a few hundred bytes of malformed input lock
            // every client behind the same address for a whole refill period.
            // Given
            graphql(defaults)

            // When
            post("""{"query":"${aliases(400)}}"}""")
            val cheap = post("""{"query":"{ ping }"}""").status

            // Then
            assertEquals(HttpStatusCode.OK, cheap)
        }

        @Test
        fun `should not spend the whole bucket on an unreadable body`() = testApplication {
            // An unpriceable body is the cheapest possible input for an attacker, so it must be
            // the least expensive thing the limiter can be asked to absorb.
            // Given
            graphql(defaults)

            // When
            post("""{"variables":{"a":1}}""")
            val cheap = post("""{"query":"{ ping }"}""").status

            // Then
            assertEquals(HttpStatusCode.OK, cheap)
        }
    }
}