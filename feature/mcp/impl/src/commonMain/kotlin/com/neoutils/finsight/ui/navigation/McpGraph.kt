package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.mcp.api.McpRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.mcp.McpScreen

/**
 * The MCP server's destination.
 *
 * **`internal`, and invoked only by this module's own `McpEntry`**: the feature is *hosted* — it
 * hangs inside the settings graph rather than beside it in the shell — so its registration is
 * offered through the entry point, and `settings:impl` never names this function. That is the
 * rule for a hosted feature, and it is what keeps `impl ⊄ impl` true.
 *
 * There is no `navigation<>` subgraph here for the same reason: this destination belongs to the
 * settings graph, and wrapping it in one of its own would put a graph inside a graph to hold a
 * single screen.
 */
internal fun NavGraphBuilder.mcpGraph() {
    composable<McpRoute> {
        val navController = LocalNavController.current

        McpScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
