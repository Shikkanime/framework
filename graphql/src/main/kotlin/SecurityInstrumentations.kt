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
)

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

    val cost = estimateCost(document)
    val depth = operationsMaxDepth(document)

    return depth > limits.maxQueryDepth || cost > limits.maxQueryComplexity
}

/**
 * Deepest selection nesting of the document, counting from 1 for a root field.
 *
 * The walk stops at [MAX_TRACKED_DEPTH] rather than recursing further: a document deep enough to
 * hit the bound is already refused, so counting the exact depth past that point would cost time on
 * exactly the inputs an attacker sends.
 */
private fun operationsMaxDepth(document: Document): Int =
    document.definitions
        .filterIsInstance<OperationDefinition>()
        .maxOfOrNull { depthOf(it.selectionSet, 0, documentFragments(document)) }
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
    expanding: Set<String> = emptySet()
): Int {
    if (current >= MAX_TRACKED_DEPTH) {
        return current
    }

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
                        definition.selectionSet,
                        current + 1,
                        fragments,
                        expanding + selection.name
                    )
                }
            }

            else -> null
        }

        if (childSelections == null) {
            current + 1
        } else {
            depthOf(childSelections, current + 1, fragments, expanding)
        }
    } ?: current + 1
}

private fun documentFragments(document: Document): Map<String, FragmentDefinition> =
    document.definitions
        .filterIsInstance<FragmentDefinition>()
        .associateBy { fragment -> fragment.name }

private const val MAX_TRACKED_DEPTH = 64

/** The root fields that make a document an introspection request. */
private val META_FIELDS = setOf("__schema", "__type")
