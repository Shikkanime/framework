package fr.shikkanime.graphql

import graphql.parser.Parser
import graphql.parser.exceptions.ParseCancelledTooDeepException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityInstrumentationsTest {
    // Fixtures first.
    private val limits = SecurityLimits(
        maxQueryDepth = 6,
        maxQueryComplexity = 100,
        introspectionEnabled = true
    )

    private fun isRefused(query: String, securityLimits: SecurityLimits = limits): Boolean =
        exceedsLimits(
            document = Parser().parseDocument(query),
            limits = securityLimits
        )

    private fun aliasFields(count: Int): String =
        (1..count).joinToString(" ") { index -> "a$index: ping" }

    private fun aliasBomb(count: Int): String =
        "{ ${aliasFields(count)} }"

    private fun deepQuery(depth: Int): String {
        var query = ""
        repeat(depth) { query += "{ nested" }
        query += " { ping }"
        repeat(depth) { query += " }" }

        return query
    }

    @Nested
    @DisplayName("Given a hostile document")
    inner class GivenHostileDocument {
        @Test
        fun `should refuse a deeply nested query`() {
            // Given
            val query = deepQuery(depth = 20)

            // Then
            assertTrue(isRefused(query), "a depth-20 query must be refused")
        }

        @Test
        fun `should refuse an alias bomb`() {
            // Given — 200 aliases of the same field in a few kilobytes.
            val query = aliasBomb(count = 200)

            // Then
            assertTrue(isRefused(query), "an alias bomb must be refused")
        }

        @Test
        fun `should refuse a fragment bomb`() {
            // Given — nested fragment spreads, the classic graphql-java recursion attack.
            val query = buildString {
                append("query Q { ")
                repeat(60) { append("f$it: episodes { ...F$it } ") }
                append("}")
                repeat(60) { append("fragment F$it on Episode { ...F${it + 1} } ") }
                append("fragment F60 on Episode { title }")
            }

            // Then
            assertTrue(isRefused(query), "a fragment bomb must be refused")
        }
    }

    @Nested
    @DisplayName("Given a legitimate document")
    inner class GivenLegitimateDocument {
        @Test
        fun `should accept a normal query`() {
            // Given
            val query = "{ episodes { number title anime { title } } }"

            // Then
            assertFalse(isRefused(query))
        }

        @Test
        fun `should accept it even with tight limits`() {
            // Given — the limits must not make GraphQL unusable.
            val tight = SecurityLimits(maxQueryDepth = 3, maxQueryComplexity = 50)

            // Then
            assertFalse(isRefused("{ episodes { title } }", tight))
        }
    }

    @Nested
    @DisplayName("Given introspection")
    inner class GivenIntrospection {
        @Test
        fun `should survive the depth and complexity limits`() {
            // The introspection query is deep and complex by nature; refusing it breaks GraphiQL
            // and every codegen client.
            // Given
            val query = "{ __schema { types { name fields { name } } } }"

            // Then
            assertFalse(isRefused(query))
        }

        @Test
        fun `should be detected as introspection`() {
            // Given
            val schemaQuery = Parser().parseDocument("{ __schema { types { name } } }")
            val typeQuery = Parser().parseDocument("{ __type(name: \"X\") { name } }")
            val business = Parser().parseDocument("{ episodes { title } }")

            // Then
            assertTrue(isIntrospectionOnly(schemaQuery))
            assertTrue(isIntrospectionOnly(typeQuery))
            assertFalse(isIntrospectionOnly(business))
        }

        @Test
        fun `should be refused when disabled`() {
            // Given
            val closed = SecurityLimits(
                maxQueryDepth = 6,
                maxQueryComplexity = 100,
                introspectionEnabled = false
            )

            // Then
            assertTrue(isRefused("{ __schema { types { name } } }", closed))
        }

        @Test
        fun `should not exempt a query that mixes introspection and business fields`() {
            // The smuggling case: claiming introspection to skip the limits while actually asking
            // for data. An OR would be a hole; the check must be strict.
            // Given
            val mixed = "{ __schema { types { name } } episodes { title } }"

            // Then
            assertFalse(isIntrospectionOnly(Parser().parseDocument(mixed)))
        }

        @Test
        fun `should refuse the mixed query on its own merits`() {
            // Given — deep enough to break the limit, disguised as introspection.
            val deep = "episodes { ".repeat(12) + "title" + " }".repeat(12)
            val mixed = "{ __schema { types { name } } $deep }"

            // Then
            assertTrue(isRefused(mixed))
        }
    }

    @Nested
    @DisplayName("Given multiple operations in one document")
    inner class GivenMultipleOperations {
        @Test
        fun `should refuse when any operation breaks the limits`() {
            // A client sends one document holding several operations and picks one by name. The
            // bounds must apply to the whole document, not just the selected operation, or an
            // attacker attaches a bomb as a second operation and calls it by `operationName`.
            // Given — the cheap operation first, the bomb second: passing `operationName` must
            // not let the bomb through.
            val bomb = aliasFields(count = 200)
            val document = "query Cheap { ping } query Expensive { $bomb }"

            // Then
            assertTrue(isRefused(document))
        }

        @Test
        fun `should accept when every operation is within the limits`() {
            // Given
            val document = "query A { ping } query B { episodes { title } }"

            // Then
            assertFalse(isRefused(document))
        }
    }

    @Nested
    @DisplayName("Given a mutation")
    inner class GivenMutation {
        @Test
        fun `should be bounded like a query`() {
            // A mutation writes, so it must never be cheaper to abuse than a read. Same document
            // shape as the alias-bomb query, declared as a mutation.
            // Given
            val document = "mutation M { ${aliasFields(count = 200)} }"

            // Then
            assertTrue(isRefused(document))
        }

        @Test
        fun `should accept a normal mutation`() {
            // Given
            val document = "mutation M { saveEpisode(id: 1) { id } }"

            // Then
            assertFalse(isRefused(document))
        }
    }

    @Nested
    @DisplayName("Given a conditional field")
    inner class GivenConditionalField {
        @Test
        fun `should be priced even though a directive may skip it`() {
            // `@include(if: false)` does not run the resolver, but the caller controls the
            // variable, so the field may well run. Pricing the static document is the safe choice:
            // it over-charges a query that may be cheap rather than under-charges one that is not.
            // Given — 200 conditionally included aliases, skippable at will.
            val fields = (1..200).joinToString(" ") { index -> "a$index: ping @include(if: \$show)" }
            val document = "query Q(\$show: Boolean!) { $fields }"

            // Then
            assertTrue(isRefused(document))
        }
    }

    @Nested
    @DisplayName("Given an unbounded nesting")
    inner class GivenUnboundedNesting {
        @Test
        fun `should be refused by the parser before the limits are reached`() {
            // graphql-java caps nesting at 500 grammar levels and cancels parsing, so a document
            // that deep never reaches our checks. The defence holds, just one layer earlier.
            // Given
            val query = deepQuery(depth = 5_000)

            // Then
            assertThrows<ParseCancelledTooDeepException> {
                Parser().parseDocument(query)
            }
        }

        @Test
        fun `should refuse a nesting that parses but breaks the limit`() {
            // Just under the parser cap, so the document is valid and the depth check is what
            // rejects it.
            // Given
            val query = deepQuery(depth = 20)

            // Then
            assertTrue(isRefused(query))
        }
    }
}
