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
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.settings.api.SettingsRoute
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Stands in for a section that is a feature module of its own, whose graph is top-level: the MCP one. */
@Serializable
internal data object OwnedGraph : NavGraphRoute

/** A screen pushed inside it, as the agent log is pushed inside the MCP section. */
@Serializable
internal data object OwnedSubRoute : NavRoute

/** A screen pushed inside settings, as the exchange-rate history is. */
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
            }
            navigation<OwnedGraph>(startDestination = McpRoute) {
                composable<McpRoute> {}
                composable<OwnedSubRoute> {}
            }
            navigation<StrangerGraph>(startDestination = StrangerRoute) {
                composable<StrangerRoute> {}
            }
        }.build()
    }

    private fun item(name: String) = destinations.first { it.name == name }

    private inline fun <reified G : Any, reified S : Any> screen(): NavDestination {
        val section = requireNotNull(graph.findNode<G>() as? NavGraph) { "no such section in the graph" }
        return requireNotNull(section.findNode<S>()) { "no such screen in the section" }
    }

    @Test
    fun `the MCP server keeps settings active, the section the user opened it from`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(screen<OwnedGraph, McpRoute>()),
            "no graph in the hierarchy ties the MCP section to settings, so only the route settings " +
                "owns answers for it — without that the selector highlighted its first item, the " +
                "dashboard, on a screen that has nothing to do with it",
        )
    }

    @Test
    fun `a screen pushed inside the MCP server keeps settings active too`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(screen<OwnedGraph, OwnedSubRoute>()),
            "the fallback tier resolves the section by its start destination, which is the owned route",
        )
    }

    @Test
    fun `a screen pushed inside a section keeps that section active`() {
        assertEquals(
            item("settings"),
            destinations.sectionOf(screen<SettingsGraph, SettingsSubRoute>()),
        )
    }

    @Test
    fun `a section root is its own item`() {
        assertEquals(
            item("dashboard"),
            destinations.sectionOf(screen<DashboardGraph, DashboardRoute>()),
        )
    }

    @Test
    fun `a destination the catalog does not claim leaves every item unselected`() {
        assertNull(
            destinations.sectionOf(screen<StrangerGraph, StrangerRoute>()),
            "an unclaimed screen has to select none: falling back to the first item of the bar is " +
                "how a section with no owner came to be drawn as the dashboard",
        )
        assertNull(destinations.sectionOf(null), "no destination at all selects nothing")
    }
}
