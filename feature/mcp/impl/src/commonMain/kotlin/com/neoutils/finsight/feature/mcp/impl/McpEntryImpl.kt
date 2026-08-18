package com.neoutils.finsight.feature.mcp.impl

import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.feature.mcp.api.McpEntry
import com.neoutils.finsight.ui.navigation.mcpGraph

internal class McpEntryImpl : McpEntry {

    context(builder: NavGraphBuilder)
    override fun register() = builder.mcpGraph()
}
