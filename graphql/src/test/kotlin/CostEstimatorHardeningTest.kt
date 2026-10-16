package fr.shikkanime.graphql

import graphql.language.Document
import graphql.parser.Parser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cost and depth analysis under adversarial input.
 *
 * Both walks run on untrusted documents, so their own worst case matters as much as their verdict: a
 * guard that refuses the right document after exponential work is still a denial of service. The
 * ceiling is therefore part of the contract, and these tests pin it.
 */
class CostEstimatorHardeningTest {
    private fun parse(query: String): Document =
        Parser().parseDocument(query)

    /**
     * A ladder of fragments where each level spreads the next one twice, so the cost doubles at
     * every level. Under 1.3 KB it reaches 2^31, and both walks are exponential unless bounded.
     */
    private fun fragmentLadder(levels: Int): String {
        val document = StringBuilder("{ ...F0 }")

        for (level in 0 until levels) {
            val next = if (level == levels - 1) "episodes { ping }" else "...F${level + 1}"

            document.append(" fragment F$level on Query { $next $next }")
        }

        return document.toString()
    }

    private val limits = SecurityLimits(maxQueryDepth = 8, maxQueryComplexity = 200)

    @Nested
    @DisplayName("Given an exponential fragment ladder")
    inner class GivenExponentialFragmentLadder {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should saturate at the ceiling of the configured bound`() {
            // The ceiling is derived from the bound rather than hard-coded: a fixed ceiling lower
            // than a consumer's `maxQueryComplexity` would saturate *below* the bound, and the
            // hostile document would then pass `cost > maxQueryComplexity`.
            // Given
            val document = parse(fragmentLadder(levels = 28))

            // When
            val cost = estimateCost(document, limits)

            // Then
            assertEquals(limits.costCeiling, cost)
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should follow the ceiling of a raised bound`() {
            // The regression this pins: with a hard-coded ceiling of 200 and a consumer bound of
            // 20 000, a saturated document reported 200 and passed the check.
            // Given
            val generous = SecurityLimits(maxQueryDepth = 8, maxQueryComplexity = 20_000)
            val document = parse(fragmentLadder(levels = 28))

            // When
            val cost = estimateCost(document, generous)

            // Then
            assertEquals(generous.costCeiling, cost)
            assertTrue(cost > generous.maxQueryComplexity, "saturation must break the bound it belongs to")
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should saturate rather than wrap around to a small cost`() {
            // The overflow is what turns a slow guard into an open one: 2^32 as an Int is 0, and a
            // zero cost both passes the bound and weighs 1 in the rate limiter — the one outcome
            // the module forbids outright.
            // Given
            val document = parse(fragmentLadder(levels = 26))

            // When
            val cost = estimateCost(document, limits)

            // Then
            assertTrue(cost >= limits.costCeiling, "cost must never wrap below the ceiling, got $cost")
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should keep pricing ordinary documents exactly`() {
            // The ceiling must not leak into legitimate pricing: the same number is charged to the
            // rate limit bucket, so it has to stay exact below the ceiling.
            // Given
            val document = parse("{ episodes { anime { title } } }")

            // When
            val cost = estimateCost(document, limits)

            // Then
            assertEquals(3, cost)
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should be refused on cost once it saturates`() {
            // Given
            val document = parse(fragmentLadder(levels = 28))

            // When & Then
            assertTrue(exceedsLimits(document, limits), "an explosive document must break the complexity bound")
        }
    }

    @Nested
    @DisplayName("Given an explosive document priced for the depth walk")
    inner class GivenExplosiveDocumentDepthWalk {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should refuse it without walking every branch`() {
            // Bounding `estimateCost` alone is not enough: `exceedsLimits` also runs the depth walk,
            // which expands the same ladder. Capping the cost first means the depth walk is never
            // reached for a document that is already refused on cost.
            // Given
            val document = parse(fragmentLadder(levels = 24))

            // When & Then — the timeout is the assertion; the walk would otherwise take minutes.
            assertTrue(exceedsLimits(document, limits))
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should measure depth within the node budget`() {
            // Depth is bounded per branch, but the total number of branches is not: a ladder of
            // 20 fragments is 20 levels deep, far under `MAX_TRACKED_DEPTH`, yet expands into a
            // million branches. The counter is what makes that observable without a clock.
            // Given
            val document = parse(fragmentLadder(levels = 20))

            // When
            val visited = countDepthNodes(document, limits)

            // Then
            assertTrue(
                visited <= limits.depthVisitBudget,
                "depth walk visited $visited nodes, over the budget of ${limits.depthVisitBudget}"
            )
        }
    }

    @Nested
    @DisplayName("Given a document nested deeper than the parser allows")
    inner class GivenUnboundedSelectionNesting {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should price it instead of overflowing the stack`() {
            // The parser refuses such a document on the request path, but `estimateCost` is public
            // and has to hold on its own. An unbounded recursion raises a `StackOverflowError`, and
            // that is an `Error` rather than an exception: it cannot be caught, so it takes the
            // thread down instead of failing the request.
            // Given — built through the API, because the parser caps nesting at 500 levels.
            var selection: graphql.language.Selection<*> = graphql.language.Field.newField("ping").build()

            repeat(3_000) {
                selection = graphql.language.Field.newField("nested")
                    .selectionSet(
                        graphql.language.SelectionSet.newSelectionSet()
                            .selections(listOf(selection))
                            .build()
                    )
                    .build()
            }

            val document = graphql.language.Document.newDocument()
                .definition(
                    graphql.language.OperationDefinition.newOperationDefinition()
                        .name("Deep")
                        .selectionSet(
                            graphql.language.SelectionSet.newSelectionSet()
                                .selections(listOf(selection))
                                .build()
                        )
                        .build()
                )
                .build()

            // When
            val cost = estimateCost(document, limits)

            // Then
            assertTrue(cost >= 1L, "a deeply nested document still has a positive cost, got $cost")
        }
    }

    @Nested
    @DisplayName("Given the weight charged for a saturated document")
    inner class GivenWeightFromSaturatedDocument {
        @Test
        fun `should never charge a free request`() {
            // The module rules state it outright: a zero-weight request is unlimited, which is
            // worse than no rate limit at all. Saturation must land on the ceiling and the
            // conversion must keep the floor.
            // Given
            val saturated = estimateCost(parse(fragmentLadder(levels = 26)), limits)

            // When
            val weight = saturated.toRateLimitWeight(minimum = 1, maximum = 100)

            // Then
            assertTrue(weight >= 1, "weight must be at least one, got $weight")
            assertTrue(weight > 1, "a saturated document must cost more than a trivial one, got $weight")
        }

        @Test
        fun `should never charge more than the bucket it drains`() {
            // A weight above the bucket capacity makes the bucket unreachable for the whole refill
            // period, which turns one heavy document into a self-inflicted outage.
            // Given
            val saturated = estimateCost(parse(fragmentLadder(levels = 26)), limits)

            // When
            val weight = saturated.toRateLimitWeight(minimum = 1, maximum = 100)

            // Then
            assertTrue(weight in 1..100, "weight must fit the bucket, got $weight")
        }

        @Test
        fun `should charge a trivial document exactly one`() {
            // Given
            val trivial = estimateCost(parse("{ ping }"), limits)

            // When
            val weight = trivial.toRateLimitWeight(minimum = 1, maximum = 100)

            // Then
            assertEquals(1, weight)
        }

        @Test
        fun `should charge an affordable document its real cost`() {
            // Between the floor and the cap the cost is exact: that is the whole point of charging
            // by cost rather than by request.
            // Given
            val affordable = estimateCost(parse("{ " + (1..40).joinToString(" ") { "a$it: ping" } + " }"), limits)

            // When
            val weight = affordable.toRateLimitWeight(minimum = 1, maximum = 100)

            // Then
            assertEquals(40, weight)
        }
    }
}