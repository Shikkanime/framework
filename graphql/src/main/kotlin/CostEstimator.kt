package fr.shikkanime.graphql

import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet

/**
 * Estimates the static execution cost of a parsed GraphQL document.
 *
 * The estimate is a pure function of the document: no schema lookup, no database access. That is
 * deliberate, because the cost has to be known *before* execution to feed the rate limiter.
 *
 * Every selected field costs one unit and contributes the cost of its own selections, so nesting
 * and breadth both add up. Aliased fields are visited once per alias, which is what makes an
 * aliasing bomb expensive: a few hundred bytes of payload resolve hundreds of times over.
 *
 * A fragment spread costs its whole selection set **every time it appears**. Counting a repeated
 * spread once would be the opposite of what is needed: the fragment bomb is precisely a small
 * fragment spread dozens of times, so a shared "already seen" set would price the attack at the
 * cost of a single expansion.
 *
 * **Bounded on purpose.** Paying for every spread is what makes the price attack-shaped, but the
 * price itself must stay affordable to compute: a ladder of fragments where each level spreads the
 * next one twice costs 2^k, which reaches billions of nodes in under a kilobyte of payload. The
 * walk therefore stops after [SecurityLimits.costVisitBudget] nodes, and a truncated walk reports
 * [SecurityLimits.costCeiling] — one past the complexity bound, so the document is refused exactly
 * as it would have been by its true cost. A walk that ran to completion reports the real total
 * instead: the number is charged to the rate limiter, and clamping a legitimately expensive
 * document down to the ceiling would misprice it.
 *
 * The arithmetic is [Long], and the recursion is depth-bounded, both so the function is total. An
 * unbounded `Int` sum wraps, and a wrapped zero would pass the bound *and* weigh one unit in the
 * limiter; an unbounded recursion on a document nested thousands of levels deep raises a
 * [StackOverflowError], which is an `Error` and takes the thread with it.
 *
 * Known limitation: the estimate is static. It cannot tell that `episodes(limit: 1000)` costs more
 * than `episodes(limit: 10)`, because that depends on the arguments and on the data. Nothing bounds
 * that today: the engine carries no complexity instrumentation, so a data-dependent expansion is
 * bounded by the request timeout alone.
 *
 * @param document Parsed document to price.
 * @param limits Bounds the price saturates at.
 * @return The estimated cost, never below one so a request is never free.
 */
fun estimateCost(document: Document, limits: SecurityLimits = SecurityLimits.DEFAULT): Long {
    val fragments = document.definitions
        .filterIsInstance<FragmentDefinition>()
        .associateBy { fragment -> fragment.name }

    val ceiling = limits.costCeiling
    val budget = CostBudget(limits.costVisitBudget)

    val total = document.definitions
        .filterIsInstance<OperationDefinition>()
        .fold(0L) { carried, operation ->
            carried + selectionsCost(operation.selectionSet, fragments, HashSet(), budget, 0)
        }

    // Only a truncated walk reports the ceiling. A walk that finished priced the whole document, so
    // its total is exact and must not be clamped: the same number is charged to the rate limiter,
    // and clamping a legitimately expensive document down to the ceiling would misprice it.
    return when {
        budget.exhausted() || budget.truncated() -> ceiling
        else -> total.coerceAtLeast(1L)
    }
}

private fun selectionsCost(
    selectionSet: SelectionSet,
    fragments: Map<String, FragmentDefinition>,
    expandedFragments: Set<String>,
    budget: CostBudget,
    depth: Int
): Long =
    selectionSet.selections.sumOf { selection ->
        selectionCost(selection, fragments, expandedFragments, budget, depth)
    }

/**
 * Cost of one selection.
 *
 * Two stops, both needed. The node budget bounds a walk whose cost is exponential in the fragment
 * count while the document stays small. [MAX_PRICED_DEPTH] bounds the *call stack*: a document
 * nested thousands of levels deep costs only a few thousand visits, so the budget alone would let
 * it recurse until the JVM raised a [StackOverflowError] — which is an `Error`, not an exception,
 * and takes the thread down instead of being caught. The parser rejects such a document on the
 * request path, but this function is public and must hold on its own.
 */
private fun selectionCost(
    selection: Selection<*>,
    fragments: Map<String, FragmentDefinition>,
    expandedFragments: Set<String>,
    budget: CostBudget,
    depth: Int
): Long {
    // Both stops mean "the rest of this document was never priced", so both mark the walk
    // truncated. A depth cut that only returned zero would leave the budget untouched and the
    // caller would report the partial total as if it were the real one.
    if (depth >= MAX_PRICED_DEPTH) {
        budget.markTruncated()

        return 0L
    }

    if (budget.exhausted()) {
        return 0L
    }

    budget.spend()

    return when (selection) {
        is Field -> 1L + (selection.selectionSet
            ?.let { childSet -> selectionsCost(childSet, fragments, expandedFragments, budget, depth + 1) }
            .orZero())

        is InlineFragment ->
            selectionsCost(selection.selectionSet, fragments, expandedFragments, budget, depth + 1)

        is FragmentSpread -> fragmentCost(selection, fragments, expandedFragments, budget, depth + 1)

        else -> 0L
    }
}

/**
 * Cost of one spread: the selections it brings in, or one unit when the fragment is unknown.
 *
 * The expanded set is copied down the branch rather than mutated, so two sibling spreads of the
 * same fragment each pay full price while a fragment that spreads itself still terminates.
 */
private fun fragmentCost(
    spread: FragmentSpread,
    fragments: Map<String, FragmentDefinition>,
    expandedFragments: Set<String>,
    budget: CostBudget,
    depth: Int
): Long {
    if (spread.name in expandedFragments) {
        return 0L
    }

    val definition = fragments[spread.name] ?: return 1L

    return selectionsCost(definition.selectionSet, fragments, expandedFragments + spread.name, budget, depth)
}

/**
 * How many selections the pricing walk may visit before it reports the ceiling.
 *
 * Spending the budget stops the descent; the caller then reports
 * [SecurityLimits.costCeiling] rather than the partial total, so a truncated walk can never look
 * cheaper than the document really is.
 */
private class CostBudget(private var visitsLeft: Long) {
    private var wasTruncated = false

    fun exhausted(): Boolean =
        visitsLeft <= 0L

    /** Records that the walk stopped before reaching the whole document. */
    fun markTruncated() {
        wasTruncated = true
    }

    fun truncated(): Boolean =
        wasTruncated

    fun spend() {
        visitsLeft--
    }

    fun visitsLeft(): Long =
        visitsLeft
}

private fun Long?.orZero(): Long = this ?: 0L

/** Deepest nesting the pricing walk follows, whatever the node budget still allows. */
private const val MAX_PRICED_DEPTH = 64

/**
 * Number of nodes the pricing walk visits before its budget runs out.
 *
 * Exposed so the bound is testable: a wall-clock assertion cannot reliably tell a linear walk from
 * an exponential one, but a node count is a deterministic function of the document.
 */
internal fun countCostNodes(document: Document, limits: SecurityLimits): Long {
    val fragments = document.definitions
        .filterIsInstance<FragmentDefinition>()
        .associateBy { fragment -> fragment.name }

    val budget = CostBudget(limits.costVisitBudget)

    document.definitions
        .filterIsInstance<OperationDefinition>()
        .forEach { operation ->
            selectionsCost(operation.selectionSet, fragments, HashSet(), budget, 0)
        }

    return limits.costVisitBudget - budget.visitsLeft()
}

/**
 * Converts a cost into the weight charged to the rate limit bucket.
 *
 * Clamped on both ends for two reasons. The floor is the module's hard rule: a zero-weight request
 * is unlimited, which is worse than having no rate limit at all. The cap is what keeps one document
 * from draining the whole bucket, which would turn a single heavy query into an outage for every
 * other request arriving in the same window.
 *
 * @receiver Cost reported by [estimateCost].
 * @param minimum Smallest weight a request may be charged.
 * @param maximum Largest weight a request may be charged.
 * @return A weight within `[minimum, maximum]`.
 */
fun Long.toRateLimitWeight(minimum: Int, maximum: Int): Int =
    coerceIn(minimum.toLong(), maximum.toLong()).toInt()

/**
 * Documents this module is not expected to receive.
 *
 * Exposed so the cost model stays verifiable: a batch request is a JSON array of operations, and
 * graphql-java would otherwise process each element as a separate, unrated document.
 */
internal fun isBatchRequest(body: String): Boolean =
    body.trimStart().startsWith("[")