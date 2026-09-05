package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.neoutils.finsight.feature.dashboard.api.DashboardGraph
import com.neoutils.finsight.feature.dashboard.api.DashboardRoute
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.settings.api.SettingsRoute
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Stands in for a section that is a feature module of its own, hosted inside another's graph: the MCP one. */
@Serializable
internal data object HostedGraph : NavGraphRoute

/** Its root screen, as the MCP section's is. */
@Serializable
internal data object HostedRoute : NavRoute

/** A screen pushed inside it, as the agent log is pushed inside the MCP section. */
@Serializable
internal data object HostedSubRoute : NavRoute

/** A screen pushed inside settings itself, as the exchange-rate history is. */
@Serializable
internal data object SettingsSubRoute : NavRoute

/** A section no catalog item names, in any way. */
@Serializable
internal data object StrangerGraph : NavGraphRoute

@Serializable
internal data object StrangerRoute : NavRoute

/**
 * What the selector highlights, asked of a real graph rather than of a description of one: the
 * destinations are built by the navigation library from serializable routes, so `hasRoute` and
 * `hierarchy` answer here exactly as they do in the running app.
 */
class ActiveSectionTest {

    private val destinations = AppNavCatalog.destinations

    private val graph: NavGraph = NavigatorProvider().let { provider ->
        provider.addNavigator(NavGraphNavigator(provider))
        provider.addNavigator(ComposeNavigator())

        NavGraphBuilder(
            provider = provider,
            startDestination = DashboardGraph,
            route = null,
            typeMap = emptyMap(),
        ).apply {
            navigation<DashboardGraph>(startDestination = DashboardRoute) {
                composable<DashboardRoute> {}
            }
            navigation<SettingsGraph>(startDestination = SettingsRoute) {
                composable<SettingsRoute> {}
                composable<SettingsSubRoute> {}
                navigation<HostedGraph>(startDestination = HostedRoute) {
                    composable<HostedRoute> {}
                    composable<HostedSubRoute> {}
                }
            }
            navigation<StrangerGraph>(startDestination = StrangerRoute) {
                composable<StrangerRoute> {}
            }
        }.build()
    }

    private fun item(name: String) = destinations.first { it.name == name }

    private inline fun <reified G : Any> NavGraph.section(): NavGraph =
        requireNotNull(findNode<G>() as? NavGraph) { "no such section in this graph" }

    private inline fun <reified S : Any> NavGraph.screen(): NavDestination =
        requireNotNull(findNode<S>()) { "no such screen in this graph" }

    @Test
    fun `a hosted section's root keeps its host active`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(graph.section<SettingsGraph>().section<HostedGraph>().screen<HostedRoute>()),
            "the host's graph is in the hierarchy of every screen it hosts, which is the whole point " +
                "of hosting it: a section reached from settings is a section of settings",
        )
    }

    @Test
    fun `a screen pushed inside a hosted section keeps the host active too`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(graph.section<SettingsGraph>().section<HostedGraph>().screen<HostedSubRoute>()),
        )
    }

    @Test
    fun `a screen pushed inside a section keeps that section active`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(graph.section<SettingsGraph>().screen<SettingsSubRoute>()),
        )
    }

    @Test
    fun `a section root is its own item`() {
        assertEquals(
            item("dashboard"),
            destinations.sectionOf(graph.section<DashboardGraph>().screen<DashboardRoute>()),
        )
    }

    @Test
    fun `a destination the catalog does not claim leaves every item unselected`() {
        assertNull(
            destinations.sectionOf(graph.section<StrangerGraph>().screen<StrangerRoute>()),
            "an unclaimed screen has to select none: falling back to the first item of the bar is " +
                "how a section with no owner came to be drawn as the dashboard",
        )
        assertNull(destinations.sectionOf(null), "no destination at all selects nothing")
    }
}
