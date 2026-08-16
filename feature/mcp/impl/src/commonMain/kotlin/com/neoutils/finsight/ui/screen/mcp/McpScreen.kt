package com.neoutils.finsight.ui.screen.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The MCP server section.
 *
 * Its content — the switch, the state, the permission axes, the address, the token and the
 * connection instructions — is task group 13. The destination exists so that the graph and
 * the route it starts from are in place; it deliberately shows nothing rather than an
 * affordance that would not work.
 */
@Composable
internal fun McpScreen(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize())
}
