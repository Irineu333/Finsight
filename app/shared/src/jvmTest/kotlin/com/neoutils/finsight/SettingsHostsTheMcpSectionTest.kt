package com.neoutils.finsight

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.ui.navigation.McpGraph
import com.neoutils.finsight.ui.navigation.settingsGraph
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The MCP section is a feature module of its own, and settings hosts its graph inside its own — the
 * only reason every MCP screen carries `SettingsGraph` in its `hierarchy`, which is what the shell's
 * selector reads to keep settings active there.
 *
 * The wiring that holds it is invisible to the compiler on both ends: the host resolves `McpEntry`
 * from Koin, and the registration happens while the graph is being built, outside any composition.
 * So the graph is built here for real, from the modules the app ships.
 */
class SettingsHostsTheMcpSectionTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun `the MCP section is built inside the settings graph`() {
        startKoin { modules(appModules) }

        val settings = requireNotNull(graph().findNode<SettingsGraph>() as? NavGraph) {
            "the settings graph is not in the graph it built"
        }
        val mcp = requireNotNull(settings.findNode<McpGraph>() as? NavGraph) {
            "settings does not host the MCP section: registering its graph as a top-level one " +
                "navigates just as well and leaves the section with no owner in the hierarchy"
        }
        val screen = requireNotNull(mcp.findNode<McpRoute>()) { "the section has no root screen" }

        assertTrue(
            screen.hierarchy.any { it.hasRoute(SettingsGraph::class) },
            "the section's screens answer to settings, which is the whole point of hosting it",
        )
    }

    private fun graph(): NavGraph = NavigatorProvider().let { provider ->
        provider.addNavigator(NavGraphNavigator(provider))
        provider.addNavigator(ComposeNavigator())

        NavGraphBuilder(
            provider = provider,
            startDestination = SettingsGraph,
            route = null,
            typeMap = emptyMap(),
        ).apply { settingsGraph() }.build()
    }
}
