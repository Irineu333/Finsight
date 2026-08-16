package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.ui.screen.mcp.McpScreen
import kotlinx.serialization.Serializable

/**
 * Stays in the `impl`: whoever navigates to the MCP section aims at the screen
 * ([McpRoute]), never at the graph node.
 */
@Serializable
data object McpGraph : NavGraphRoute

fun NavGraphBuilder.mcpGraph() {
    navigation<McpGraph>(
        startDestination = McpRoute,
    ) {
        composable<McpRoute> {
            McpScreen()
        }
    }
}
