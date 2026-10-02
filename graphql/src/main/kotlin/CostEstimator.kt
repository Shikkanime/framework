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
 * Known limitation: the estimate is static. It cannot tell that `episodes(limit: 1000)` costs more
 * than `episodes(limit: 10)`, because that depends on the arguments and on the data. Anything
 * data-dependent is bounded by the complexity instrumentation and the request timeout instead.
 *
 * @param document Parsed document to price.
 * @return The estimated cost, never below one so a request is never free.
 */
fun estimateCost(document: Document): Int {
    val fragments = document.definitions
        .filterIsInstance<FragmentDefinition>()
        .associateBy { fragment -> fragment.name }

    return document.definitions
        .filterIsInstance<OperationDefinition>()
        .sumOf { operation -> selectionsCost(operation.selectionSet, fragments, HashSet()) }
        .coerceAtLeast(1)
}

private fun selectionsCost(
    selectionSet: SelectionSet,
    fragments: Map<String, FragmentDefinition>,
    expandedFragments: Set<String>
): Int =
    selectionSet.selections.sumOf { selection -> selectionCost(selection, fragments, expandedFragments) }

private fun selectionCost(
    selection: Selection<*>,
    fragments: Map<String, FragmentDefinition>,
    expandedFragments: Set<String>
): Int =
    when (selection) {
        is Field -> 1 + selection.selectionSet
            ?.let { childSet -> selectionsCost(childSet, fragments, expandedFragments) }
            .orZero()

        is InlineFragment -> selectionsCost(selection.selectionSet, fragments, expandedFragments)

        is FragmentSpread -> fragmentCost(selection, fragments, expandedFragments)

        else -> 0
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
    expandedFragments: Set<String>
): Int {
    if (spread.name in expandedFragments) {
        return 0
    }

    val definition = fragments[spread.name] ?: return 1

    return selectionsCost(definition.selectionSet, fragments, expandedFragments + spread.name)
}

private fun Int?.orZero(): Int = this ?: 0

/**
 * Documents this module is not expected to receive.
 *
 * Exposed so the cost model stays verifiable: a batch request is a JSON array of operations, and
 * graphql-java would otherwise process each element as a separate, unrated document.
 */
internal fun isBatchRequest(body: String): Boolean =
    body.trimStart().startsWith("[")
