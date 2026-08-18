package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination

/**
 * The catalog item whose section [destination] belongs to — the one the selector draws as active —
 * resolved in two tiers so it answers on any screen:
 *
 * 1. Route match over the `hierarchy`: section roots, and sibling destinations that share a graph
 *    while being distinct items of the selector (Credit Cards and Installments).
 * 2. Fallback for a sub-destination pushed inside a section (an invoice screen), whose route no item
 *    names: the item that owns the start destination of the graph it was pushed into.
 *
 * `null` when no item claims [destination], and then nothing is drawn as active: an item highlighted
 * for a screen that is not its own tells the user they are somewhere they are not.
 */
internal fun List<CatalogDestination>.sectionOf(destination: NavDestination?): CatalogDestination? =
    firstOrNull { item -> destination?.hierarchy?.any { node -> item.claims(node) } == true }
        ?: destination?.hierarchy
            ?.firstNotNullOfOrNull { it as? NavGraph }
            ?.findStartDestination()
            ?.let { sectionStart -> firstOrNull { item -> item.claims(sectionStart) } }

private fun CatalogDestination.claims(node: NavDestination) = node.hasRoute(route::class)
