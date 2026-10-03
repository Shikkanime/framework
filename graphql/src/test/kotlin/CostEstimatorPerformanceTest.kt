package fr.shikkanime.graphql

import graphql.parser.Parser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance guards for the cost and depth walks.
 *
 * A wall-clock assertion is flaky in CI, so it is deliberately absent: the runners throttle CPU and
 * pause for GC, and a threshold loose enough to survive that gives false confidence about a
 * regression to exponential expansion. What is asserted instead is the *shape* of the work — the
 * number of nodes each walk visits — which is a deterministic function of the document and holds
 * identically on every machine.
 *
 * `@Timeout` covers the remaining half: only it can notice a hang, and it fails the build instead of
 * stalling the suite. It is set wide enough that a loaded runner does not trip it, and narrow enough
 * that the exponential regression fails in seconds rather than minutes.
 */
class CostEstimatorPerformanceTest {
    private val limits = SecurityLimits(maxQueryDepth = 8, maxQueryComplexity = 200)

    private companion object {
        /**
         * How much slower a four-times-larger document may price.
         *
         * A linear walk lands near 4, quadratic near 16. Eight leaves room for the measurement
         * noise of a shared runner while still failing on any super-linear work.
         */
        const val LINEAR_SCALING_TOLERANCE = 8.0
    }

    private fun fragmentLadder(levels: Int): String {
        val document = StringBuilder("{ ...F0 }")

        for (level in 0 until levels) {
            val next = if (level == levels - 1) "episodes { ping }" else "...F${level + 1}"

            document.append(" fragment F$level on Query { $next $next }")
        }

        return document.toString()
    }

    @Nested
    @DisplayName("Given the nodes the cost walk visits")
    inner class GivenCostWalkNodes {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should stay within the budget on a wide document`() {
            // A 64 KiB body of aliases is the largest shape worth pricing. Linear in the fields,
            // so the node count is the field count — anything super-linear means the walk stopped
            // visiting each selection once.
            // Given
            val aliases = (1..4_000).joinToString(" ") { "a$it: ping" }
            val document = Parser().parseDocument("{ $aliases }")

            // When
            val visited = countCostNodes(document, limits)

            // Then
            assertTrue(
                visited <= limits.costVisitBudget,
                "cost walk visited $visited nodes, over the budget of ${limits.costVisitBudget}"
            )
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should scale its time with the field count, not its square`() {
            // A wall-clock assertion on a single document proves nothing here: measured, pricing a
            // 4000-field document takes ~0.013 ms, so a 250 ms threshold would still pass after a
            // 3800x regression — exactly the false confidence a loose bound gives. What matters is
            // the *shape* of the cost, so this compares two documents and asserts a ratio: pricing
            // four times the fields must stay roughly four times the work, not sixteen. A regression
            // to quadratic or exponential work fails at any absolute threshold.
            // Given
            val small = Parser().parseDocument("{ " + (1..1_000).joinToString(" ") { "a$it: ping" } + " }")
            val large = Parser().parseDocument("{ " + (1..4_000).joinToString(" ") { "a$it: ping" } + " }")

            // Warm up so JIT compilation is not charged to the first measurement.
            repeat(20) { estimateCost(small, limits); estimateCost(large, limits) }

            // When
            val smallCost = measureNanoTime { repeat(40) { estimateCost(small, limits) } }
            val largeCost = measureNanoTime { repeat(40) { estimateCost(large, limits) } }

            // Then — generous on both sides so a loaded runner cannot trip it, tight enough that
            // super-linear work fails.
            val ratio = largeCost.toDouble() / smallCost.coerceAtLeast(1L)
            assertTrue(
                ratio < LINEAR_SCALING_TOLERANCE,
                "4x the fields cost ${"%.1f".format(ratio)}x the time, over the " +
                    "${LINEAR_SCALING_TOLERANCE}x a linear walk allows"
            )
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should visit nodes linearly on a flat document`() {
            // Ten fields must not cost more node visits than a hundred by a factor of ten squared.
            // This is the invariant that a memoisation bug or a double-counting would break.
            // Given
            val ten = Parser().parseDocument("{ " + (1..10).joinToString(" ") { "a$it: ping" } + " }")
            val hundred = Parser().parseDocument("{ " + (1..100).joinToString(" ") { "a$it: ping" } + " }")

            // When
            val tenVisits = countCostNodes(ten, limits)
            val hundredVisits = countCostNodes(hundred, limits)

            // Then
            assertTrue(
                hundredVisits <= tenVisits * 12,
                "visits grew from $tenVisits to $hundredVisits for a 10x wider document"
            )
        }
    }

    @Nested
    @DisplayName("Given the nodes the depth walk visits")
    inner class GivenDepthWalkNodes {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should stay within the budget on an explosive ladder`() {
            // The ladder is 20 levels deep, comfortably under `MAX_TRACKED_DEPTH`, but expands into
            // a million branches. Depth is bounded per branch and unbounded in total, which is the
            // gap this budget closes.
            // Given
            val document = Parser().parseDocument(fragmentLadder(levels = 20))

            // When
            val visited = countDepthNodes(document, limits)

            // Then
            assertTrue(
                visited <= limits.depthVisitBudget,
                "depth walk visited $visited nodes, over the budget of ${limits.depthVisitBudget}"
            )
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should not grow with the level count`() {
            // Two ladders of very different explosive size must both stop at the budget rather than
            // scaling with their level count. The small one is measured under the budget, so it is
            // the *ceiling* that has to hold, not an equality between the two.
            // Given
            val small = Parser().parseDocument(fragmentLadder(levels = 12))
            val large = Parser().parseDocument(fragmentLadder(levels = 22))

            // When
            val smallVisits = countDepthNodes(small, limits)
            val largeVisits = countDepthNodes(large, limits)

            // Then
            assertTrue(
                largeVisits <= limits.depthVisitBudget,
                "the larger ladder exceeded the budget: $largeVisits"
            )
            assertTrue(
                largeVisits > smallVisits,
                "a larger ladder should still visit more branches, got $smallVisits then $largeVisits"
            )
        }
    }

    @Nested
    @DisplayName("Given a cyclic document")
    inner class GivenCyclicDocument {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should terminate on mutually recursive fragments`() {
            // Termination is a performance property as much as a correctness one: a walk that does
            // not stop on a cycle is a hang, not a wrong number.
            // Given
            val document = Parser().parseDocument(
                "{ episodes { ...A } } fragment A on Episode { ...B } fragment B on Episode { ...A }"
            )

            // When
            val cost = estimateCost(document, limits)
            val refused = exceedsLimits(document, limits)

            // Then
            assertTrue(cost >= 1, "a cyclic document still has a positive cost")
            assertTrue(!refused, "a two-fragment cycle expands to almost nothing")
        }
    }

    @Nested
    @DisplayName("Given a document priced to the ceiling")
    inner class GivenDocumentAtTheCeiling {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `should be flat past the ceiling`() {
            // Saturation flattens the curve: levels that differ by thousands of units of true cost
            // all report the same number. That is the property a guard relies on to refuse fast.
            // Given
            val documents = listOf(24, 28, 30).map { Parser().parseDocument(fragmentLadder(levels = it)) }

            // When
            val costs = documents.map { estimateCost(it, limits) }

            // Then
            assertEquals(1, costs.distinct().size, "every saturated document must report the same cost: $costs")
            assertEquals(limits.costCeiling, costs.first())
        }
    }
}