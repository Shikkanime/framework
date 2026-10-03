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
 * Bounds applied to a GraphQL document before it is executed.
 *
 * @property maxQueryDepth Maximum selection nesting accepted.
 * @property maxQueryComplexity Maximum estimated cost accepted.
 * @property introspectionEnabled Whether introspection is served. When false, an introspection
 * query is refused even if it stays within the other bounds, because the schema is the thing
 * being protected.
 */
data class SecurityLimits(
    val maxQueryDepth: Int,
    val maxQueryComplexity: Int,
    val introspectionEnabled: Boolean = true
) {
    /**
     * Cost reported by a document expensive enough that pricing it precisely is pointless.
     *
     * Derived from [maxQueryComplexity] rather than fixed: a hard-coded ceiling would saturate
     * *below* the bound of a consumer configured with a larger one, and the hostile document would
     * then pass `cost > maxQueryComplexity`. One past the bound is the smallest value that keeps
     * every saturated document refused, whatever the configuration.
     */
    val costCeiling: Long =
        maxQueryComplexity.toLong() + 1L

    /**
     * Largest number of selections the cost walk may visit before it gives up.
     *
     * A document cannot ask for more selections than it has bytes, so the budget scales with the
     * body limit the caller configured. It exists to stop an expansion that is exponential in the
     * number of fragments rather than linear in the number of selections.
     */
    val costVisitBudget: Long =
        COST_VISITS_PER_BYTE * (maxQueryComplexity.toLong() + FRAGMENT_VISIT_FACTOR)

    /**
     * Largest number of branches the depth walk may visit before it gives up.
     *
     * Derived from [maxQueryComplexity], not [maxQueryDepth]: an explosive ladder is shallow, so a
     * budget tied to the depth bound would be tiny and would truncate documents that have nothing
     * to do with depth. Complexity is the bound that actually prices the expansion.
     */
    val depthVisitBudget: Long =
        DEPTH_VISITS_PER_BYTE * (maxQueryComplexity.toLong() + FRAGMENT_VISIT_FACTOR)

    companion object {
        /**
         * Bounds used when a caller prices a document outside a configured endpoint.
         *
         * Exposed so [estimateCost] keeps a total function for its own tests and for any consumer
         * pricing a document outside a request. The endpoint always passes the limits it was
         * configured with, so the ceiling follows the deployment rather than these defaults.
         */
        val DEFAULT: SecurityLimits =
            SecurityLimits(maxQueryDepth = 8, maxQueryComplexity = 200)
    }
}

/**
 * Whether the document only asks for schema metadata.
 *
 * Introspection is deep and expensive by nature, so it is exempt from the depth and complexity
 * bounds when public. Refusing it would break GraphiQL and every codegen client, which is why the
 * exemption has to be explicit rather than a lowered threshold.
 *
 * A document that mixes introspection with business fields is **not** exempt: it would otherwise
 * be a way to smuggle a heavy query past the limits.
 */
fun isIntrospectionOnly(document: Document): Boolean {
    val operations = document.definitions.filterIsInstance<OperationDefinition>()

    if (operations.isEmpty()) {
        return false
    }

    return operations.all { operation ->
        operation.selectionSet.selections.all { isIntrospectionField(it) }
    }
}

private fun isIntrospectionField(selection: Selection<*>): Boolean =
    when (selection) {
        // Only a meta field *is* introspection. A business field merely containing a meta field is
        // a smuggling attempt: exempting it would let a heavy query ride under the exemption.
        is Field -> selection.name in META_FIELDS

        // An inline fragment wrapping a meta field is still introspection, since it selects
        // nothing else.
        is InlineFragment -> selection.selectionSet.selections.all(::isIntrospectionField)

        is FragmentSpread -> false
        else -> false
    }

private fun isIntrospectionField(selectionSet: SelectionSet): Boolean =
    selectionSet.selections.all { isIntrospectionField(it) }

/**
 * Whether the document breaks one of the [limits].
 *
 * @param document Parsed document to check.
 * @param limits Bounds to enforce.
 * @return `true` when the document must not be executed.
 */
fun exceedsLimits(document: Document, limits: SecurityLimits): Boolean {
    if (isIntrospectionOnly(document)) {
        return !limits.introspectionEnabled
    }

    // Cost is checked first, and each walk stops at its own ceiling. The order matters: pricing is
    // the cheaper of the two analyses, so a document refused on cost never pays for the depth walk,
    // which resolves spreads and is the more explosive of the pair.
    val cost = estimateCost(document, limits)

    if (cost > limits.maxQueryComplexity) {
        return true
    }

    return operationsMaxDepth(document, limits) > limits.maxQueryDepth
}

/**
 * Deepest selection nesting of the document, counting from 1 for a root field.
 *
 * The walk stops at [MAX_TRACKED_DEPTH] rather than recursing further: a document deep enough to
 * hit the bound is already refused, so counting the exact depth past that point would cost time on
 * exactly the inputs an attacker sends.
 */
private fun operationsMaxDepth(document: Document, limits: SecurityLimits): Int =
    document.definitions
        .filterIsInstance<OperationDefinition>()
        .maxOfOrNull { operation ->
            depthOf(
                selectionSet = operation.selectionSet,
                current = 0,
                fragments = documentFragments(document),
                limits = limits,
                budget = Budget(limits.depthVisitBudget)
            )
        }
        ?: 0

/**
 * Deepest nesting, counting fragment spreads as the depth of the fragment they expand to.
 *
 * Resolving spreads matters for the limits to mean anything: the fragment bomb nests through
 * fragments, so a document whose visible fields look three deep can expand to fifty. Cycle
 * tracking is per branch, so a fragment that spreads itself stops instead of looping.
 */
private fun depthOf(
    selectionSet: SelectionSet,
    current: Int,
    fragments: Map<String, FragmentDefinition> = emptyMap(),
    expanding: Set<String> = emptySet(),
    limits: SecurityLimits,
    budget: Budget
): Int {
    if (current >= MAX_TRACKED_DEPTH || budget.exhausted()) {
        return current
    }

    budget.spend()

    return selectionSet.selections.maxOfOrNull { selection ->
        val childSelections = when (selection) {
            is Field -> selection.selectionSet
            is InlineFragment -> selection.selectionSet
            is FragmentSpread -> {
                val definition = fragments[selection.name]

                if (definition == null || selection.name in expanding) {
                    null
                } else {
                    return@maxOfOrNull depthOf(
                        selectionSet = definition.selectionSet,
                        current = current + 1,
                        fragments = fragments,
                        expanding = expanding + selection.name,
                        limits = limits,
                        budget = budget
                    )
                }
            }

            else -> null
        }

        if (childSelections == null) {
            current + 1
        } else {
            depthOf(
                selectionSet = childSelections,
                current = current + 1,
                fragments = fragments,
                expanding = expanding,
                limits = limits,
                budget = budget
            )
        }
    } ?: current + 1
}

/**
 * Shared allowance for one analysis walk.
 *
 * The walks resolve fragment spreads, so their cost is exponential in the number of fragments while
 * the document stays comfortably inside the body limit. The budget is what stops that: once spent,
 * the walk stops descending and reports what it has. It is mutable and passed by reference so the
 * whole walk shares one allowance rather than restarting it per branch.
 */
private class Budget(private var remaining: Long) {
    fun exhausted(): Boolean =
        remaining <= 0L

    fun spend() {
        remaining--
    }

    fun remainingVisits(): Long =
        remaining
}

/**
 * Number of nodes the depth walk visits before its budget runs out.
 *
 * Exposed so the bound is testable: a wall-clock assertion cannot see the difference between a
 * linear walk and an exponential one reliably, but a node count can.
 */
internal fun countDepthNodes(document: Document, limits: SecurityLimits): Long {
    val fragments = documentFragments(document)
    val budget = Budget(limits.depthVisitBudget)

    document.definitions
        .filterIsInstance<OperationDefinition>()
        .forEach { operation ->
            depthOf(operation.selectionSet, 0, fragments, emptySet(), limits, budget)
        }

    return limits.depthVisitBudget - budget.remainingVisits()
}

private fun documentFragments(document: Document): Map<String, FragmentDefinition> =
    document.definitions
        .filterIsInstance<FragmentDefinition>()
        .associateBy { fragment -> fragment.name }

private const val MAX_TRACKED_DEPTH = 64

/** Visit budget multiplier applied to the cost bound. */
private const val COST_VISITS_PER_BYTE = 64L

/** Visit budget multiplier applied to the depth bound. */
private const val DEPTH_VISITS_PER_BYTE = 64L

/** Head-room the visit budgets keep for fragment expansion beyond the bound itself. */
private const val FRAGMENT_VISIT_FACTOR = 256L

/** The root fields that make a document an introspection request. */
private val META_FIELDS = setOf("__schema", "__type")
