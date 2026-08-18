package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.screen.mcp.McpScreen
import com.neoutils.finsight.ui.screen.mcpActivity.McpActivityScreen
import kotlinx.serialization.Serializable

/**
 * Stays in the `impl`: whoever navigates to the MCP section aims at the screen
 * ([McpRoute]), never at the graph node.
 */
@Serializable
data object McpGraph : NavGraphRoute

/**
 * The full agent log.
 *
 * Internal to this graph: it is reached from the section's recent activity and from nowhere else,
 * so no other feature has any reason to name it.
 */
@Serializable
internal data object McpActivityRoute : NavRoute

/**
 * Internal to this module: the section is registered inside its host's graph, and the only caller
 * is this feature's own entry point.
 */
internal fun NavGraphBuilder.mcpGraph() {
    navigation<McpGraph>(
        startDestination = McpRoute,
    ) {
        composable<McpRoute> {
            val navController = LocalNavController.current

            McpScreen(
                onNavigateBack = { navController.navigateUp() },
                onOpenActivity = { navController.navigate(McpActivityRoute) },
            )
        }

        composable<McpActivityRoute> {
            val navController = LocalNavController.current

            McpActivityScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
