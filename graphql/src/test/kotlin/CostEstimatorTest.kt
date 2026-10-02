package fr.shikkanime.graphql

import graphql.parser.Parser
import graphql.language.Document
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostEstimatorTest {
    private fun parse(query: String): Document =
        Parser().parseDocument(query)

    @Nested
    @DisplayName("Given a trivial query")
    inner class GivenTrivialQuery {
        @Test
        fun `should cost exactly one`() {
            // Given
            val document = parse("{ ping }")

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(1, cost)
        }
    }

    @Nested
    @DisplayName("Given aliased fields")
    inner class GivenAliasedFields {
        @Test
        fun `should count one unit per alias`() {
            // Given — the aliasing bomb: tiny payload, huge execution cost.
            val document = parse("{ a: ping b: ping c: ping d: ping e: ping }")

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(5, cost)
        }

        @Test
        fun `should count the same field once when it is not aliased`() {
            // Given
            val document = parse("{ ping }")

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(1, cost)
        }

        @Test
        fun `should grow linearly with the alias count`() {
            // Given
            val five = parse("{ a1: ping a2: ping a3: ping a4: ping a5: ping }")
            val ten = parse("{ a1: ping a2: ping a3: ping a4: ping a5: ping a6: ping a7: ping a8: ping a9: ping a10: ping }")

            // When
            val costFive = estimateCost(five)
            val costTen = estimateCost(ten)

            // Then
            assertEquals(5, costFive)
            assertEquals(10, costTen)
        }
    }

    @Nested
    @DisplayName("Given nested selections")
    inner class GivenNestedSelections {
        @Test
        fun `should add the children of each field`() {
            // Given — `episodes` plus its `title`, versus `episodes`, `anime` and `title`.
            val shallow = parse("{ episodes { title } }")
            val deeper = parse("{ episodes { anime { title } } }")

            // When
            val costShallow = estimateCost(shallow)
            val costDeeper = estimateCost(deeper)

            // Then
            assertEquals(2, costShallow)
            assertEquals(3, costDeeper)
        }

        @Test
        fun `should count two for two sibling fields`() {
            // Given
            val flat = parse("{ a: ping b: ping }")

            // When
            val cost = estimateCost(flat)

            // Then
            assertEquals(2, cost)
        }

        @Test
        fun `should ignore an inline fragment`() {
            // An inline fragment is a filter, not extra work: it selects the same fields.
            // Given
            val document = parse("{ episodes { ... on Episode { title } } }")

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(2, cost)
        }
    }

    @Nested
    @DisplayName("Given a fragment")
    inner class GivenFragment {
        @Test
        fun `should count the spread selections`() {
            // Given
            val document = parse(
                """
                query Q { episodes { ...F } }
                fragment F on Episode { title number }
                """.trimIndent()
            )

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(3, cost)
        }

        @Test
        fun `should not hang on a cyclic spread`() {
            // A self-referencing fragment is the recursion bomb; the walk must stop instead of
            // overflowing the stack.
            // Given
            val document = parse(
                """
                query Q { episodes { ...F } }
                fragment F on Episode { title ...F }
                """.trimIndent()
            )

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(2, cost)
        }
    }

    @Nested
    @DisplayName("Given a large but shallow document")
    inner class GivenLargeButShallowDocument {
        @Test
        fun `should still report a high cost`() {
            // Depth limits do not catch this shape: a thousand flat fields is one level deep but
            // a thousand times the work.
            // Given
            val document = parse("{ " + (1..1000).joinToString(" ") { "a$it: ping" } + " }")

            // When
            val cost = estimateCost(document)

            // Then
            assertEquals(1000, cost)
        }
    }

    @Nested
    @DisplayName("Given an introspection query")
    inner class GivenIntrospectionQuery {
        @ParameterizedTest
        @ValueSource(
            strings = [
                "{ __schema { types { name } } }",
                "{ __type(name: \"Episode\") { name fields { name } } }"
            ]
        )
        fun `should report a positive cost`(query: String) {
            // Given
            val document = parse(query)

            // When
            val cost = estimateCost(document)

            // Then
            assertTrue(cost > 0)
        }
    }

    @Nested
    @DisplayName("Given an empty document")
    inner class GivenEmptyDocument {
        @Test
        fun `should never report a zero cost`() {
            // A zero weight would make the request free in the rate limiter, which is worse than
            // having no rate limit at all.
            // Given
            val document = parse("query Empty { __typename }")

            // When
            val cost = estimateCost(document)

            // Then
            assertTrue(cost >= 1, "cost must be at least one, got $cost")
        }
    }
}
