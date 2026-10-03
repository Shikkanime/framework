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

    // A companion meta field may ride along but never carries the exemption by itself: the
    // document has to read the schema somewhere. Without this, `{ __typename }` — and a document
    // aliasing it thousands of times — would qualify while doing no introspection work at all.
    if (!documentReadsSchema(document)) {
        return false
    }

    return operations.all { operation ->
        operation.selectionSet.selections.all {
            isIntrospectionField(it, documentFragments(document), emptySet())
        }
    }
}

/**
 * Whether the document selects a field that actually reads the schema.
 *
 * `__schema` and `__type` do; `__typename` does not — it resolves against the query type and
 * reveals one name. It is the only meta field a document can flood, so it cannot be the thing
 * that grants the exemption.
 */
private fun documentReadsSchema(document: Document): Boolean =
    document.definitions
        .filterIsInstance<OperationDefinition>()
        .any { operation ->
            operation.selectionSet.selections.any {
                it.startsSchemaRead(
                    fragments = documentFragments(document),
                    depth = 0,
                    visited = emptySet(),
                    budget = SchemaReadBudget(SCHEMA_READ_VISIT_CAP)
                )
            }
        }

/**
 * Node allowance for the schema-read probe.
 *
 * The probe answers a yes/no question and runs *before* the cost estimator, which is the walk that
 * actually carries a budget. Without an allowance of its own it inherits the hazard it exists to
 * classify: a ladder of spreads expanded until an answer came back, measured at 329 s on a
 * 24-level document. A safety net, not the primary defence — the exponential shape is cut by
 * [MAX_TRACKED_DEPTH] and the per-branch visited set.
 */
private const val SCHEMA_READ_VISIT_CAP = 256

/** Remaining visits for one schema-read probe. */
private class SchemaReadBudget(private var left: Int) {
    fun exhausted(): Boolean =
        left <= 0

    fun spend() {
        left--
    }
}

/**
 * Whether this selection reaches a field that reads the schema.
 *
 * @param fragments Fragment definitions of the document.
 * @param depth Nesting level, which bounds a legitimately deep document.
 * @param visited Fragment names already walked on this branch, so a cycle terminates.
 * @param budget Node allowance, since this runs ahead of the budgeted cost walk.
 */
private fun Selection<*>.startsSchemaRead(
    fragments: Map<String, FragmentDefinition>,
    depth: Int,
    visited: Set<String>,
    budget: SchemaReadBudget
): Boolean =
    when {
        depth > MAX_TRACKED_DEPTH || budget.exhausted() -> false

        this is Field -> name in META_FIELDS

        this is FragmentSpread -> {
            val name = this.name

            when {
                name in visited -> false

                else -> {
                    budget.spend()

                    fragments[name]?.selectionSet?.selections
                        ?.any { it.startsSchemaRead(fragments, depth + 1, visited + name, budget) }
                        ?: false
                }
            }
        }

        this is InlineFragment -> selectionSet.selections.any {
            it.startsSchemaRead(fragments, depth + 1, visited, budget)
        }

        else -> false
    }

private fun isIntrospectionField(
    selection: Selection<*>,
    fragments: Map<String, FragmentDefinition>,
    expanding: Set<String>
): Boolean =
    when (selection) {
        // Only a meta field *starts* an introspection request. Everything below it is schema
        // metadata — `types`, `fields`, `name`, `ofType` qualify by being selected on `__schema`,
        // not by name — so sub-fields are not re-checked against META_FIELDS.
        //
        // What is checked is whether the subtree can multiply its work through spreads. Fields
        // under `__schema` are bounded by the schema's own shape, so a fixed selection count costs
        // a fixed amount; a spread that expands other spreads does not, and
        // `{ __schema { types { ...F0 } } }` with a ladder of them qualified as introspection on
        // name alone and skipped both bounds entirely.
        is Field -> when {
            selection.name in META_FIELDS ->
                !carriesExpandingSpread(selection.selectionSet, fragments, expanding)

            // A companion may ride along with a schema read the document already performs.
            // `isIntrospectionOnly` requires that read, so a document of nothing but
            // `__typename` is priced and bounded like any other.
            selection.name in COMPANION_META_FIELDS -> true

            else -> false
        }

        // An inline fragment wrapping meta fields is still introspection, since it selects
        // nothing else.
        is InlineFragment -> selection.selectionSet.selections.all {
            isIntrospectionField(it, fragments, expanding)
        }

        // A spread at the root of an operation is introspection only if the fragment it points at
        // is, which keeps a codegen client's `...SchemaFragment` working without letting an
        // arbitrary fragment claim the exemption.
        // A spread is introspection only if the fragment it names is, *and* does not multiply its
        // own work. Without the second half the ladder hid behind a named fragment: checking only
        // that its body selects meta fields let `{ __schema { ...F0 } }` through while `F0`'s body
        // spread another fragment twice per level.
        is FragmentSpread -> {
            val name = selection.name

            when {
                name in expanding -> true

                else -> {
                    val body = fragments[name]?.selectionSet

                    body != null &&
                        !spreadMultiplies(name, fragments, expanding) &&
                        body.selections.all { isIntrospectionField(it, fragments, expanding + name) }
                }
            }
        }

        else -> false
    }

/**
 * Whether a selection set's fragment spreads can compound into an unbounded amount of work.
 *
 * A codegen client's introspection query spreads a handful of named fragments, each *reached once*
 * and each selecting fixed fields — `...FullType` reaches `...InputValue` through
 * `fields { args { ... } }`, exactly once. That is a fixed amount of work.
 *
 * The bomb reaches the same fragment **more than once**, so each occurrence adds its work again and
 * the total grows multiplicatively: measured, a ladder of spreads doubling per level reached a cost
 * of 201 in 422 bytes and skipped the bounds entirely.
 *
 * Repetition, not mere presence, is the whole distinction. Asking only "is there a spread" refuses
 * GraphiQL; following every spread descends a `TypeRef` chain forever, since those nest
 * `ofType { ofType { ... } }` by design.
 *
 * @param selectionSet Selections to inspect, `null` for a leaf field such as `__typename`.
 * @param fragments Fragment definitions of the document.
 * @param visited Fragment names already walked on this branch, so a cycle terminates.
 */
private fun carriesExpandingSpread(
    selectionSet: SelectionSet?,
    fragments: Map<String, FragmentDefinition>,
    visited: Set<String>
): Boolean {
    val selections = selectionSet?.selections ?: return false

    return selections.any { selection ->
        when (selection) {
            is FragmentSpread -> spreadMultiplies(selection.name, fragments, visited)
            is Field -> carriesExpandingSpread(selection.selectionSet, fragments, visited)
            is InlineFragment -> carriesExpandingSpread(selection.selectionSet, fragments, visited)
            else -> false
        }
    }
}

/**
 * Whether expanding [name] repeats work, i.e. its body reaches the same spread more than once.
 *
 * @param name Fragment being expanded.
 * @param fragments Fragment definitions of the document.
 * @param visited Names already walked on this branch, so a cycle terminates.
 */
private fun spreadMultiplies(
    name: String,
    fragments: Map<String, FragmentDefinition>,
    visited: Set<String>
): Boolean {
    if (name in visited) {
        return false
    }

    val body = fragments[name]?.selectionSet ?: return false

    return body.spreadNamesBelow(visited + name)
        .groupingBy { it }
        .eachCount()
        .values
        .any { occurrences -> occurrences > 1 }
}

/**
 * Every fragment spread reachable below this selection set, fields included, counted with
 * multiplicity.
 *
 * Depth matters: `fragment F0 { a: types { ...F1 } b: types { ...F1 } }` repeats `...F1` twice, but
 * not twice at its *root* — each sits under its own field, so counting only the root sees none.
 * Fragments already on this branch are skipped, so a cycle contributes nothing.
 *
 * @param visited Names already on this branch, so a cycle terminates.
 * @param depth Nesting level, bounding a legitimately deep schema walk.
 */
private fun SelectionSet.spreadNamesBelow(
    visited: Set<String>,
    depth: Int = 0
): List<String> {
    if (depth > MAX_TRACKED_DEPTH) {
        return emptyList()
    }

    return selections.flatMap { selection ->
        when (selection) {
            is FragmentSpread -> if (selection.name in visited) emptyList() else listOf(selection.name)

            is Field -> selection.selectionSet?.spreadNamesBelow(visited, depth + 1) ?: emptyList()
            is InlineFragment -> selection.selectionSet.spreadNamesBelow(visited, depth + 1)
            else -> emptyList()
        }
    }
}



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
    // An exhausted budget means the walk gave up before finishing, not that the document is
    // shallow. Reporting `current` there accepted a document whose expansion was never walked, so
    // a truncated walk reports past every possible bound instead.
    if (budget.exhausted()) {
        return Int.MAX_VALUE
    }

    // Stopping at the configured bound, capped, keeps the walk bounded by the same number the
    // caller will compare it against.
    if (current >= minOf(MAX_TRACKED_DEPTH, limits.maxQueryDepth + 1)) {
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
/**
 * The root fields that read the schema, and so can *start* an introspection request.
 *
 * `__typename` is deliberately absent: it resolves against the query type rather than the schema,
 * so it carries no introspection work. Listing it here would exempt any document whose selections
 * are all `__typename` — including one that aliases it thousands of times — which hands the
 * exemption to the cheapest possible bomb. It is accepted as an addition instead, in
 * [COMPANION_META_FIELDS], because GraphiQL, Apollo and Relay all send it.
 */
private val META_FIELDS = setOf("__schema", "__type")

/**
 * Meta fields that may accompany an introspection request without granting the exemption.
 *
 * `__typename` costs one unit and reveals the query type's name. Refusing a client that sends it
 * alongside `__schema` would break the codegen tools the exemption exists for.
 */
private val COMPANION_META_FIELDS = setOf("__typename")
