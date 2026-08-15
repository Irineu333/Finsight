package com.neoutils.finsight.feature.mcp.impl

import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.ui.navigation.mcpGraph

/**
 * Registers the MCP server's destination wherever the caller is building a graph — which is
 * inside `SettingsGraph`, the single path to the switch.
 *
 * The graph builder arrives as the context parameter declared by [McpEntry], so this adds nothing
 * of its own: the whole point of the entry point is that the extension it calls stays `internal`
 * to this module.
 */
internal class McpEntryImpl : McpEntry {

    context(builder: NavGraphBuilder)
    override fun register() {
        builder.mcpGraph()
    }
}
