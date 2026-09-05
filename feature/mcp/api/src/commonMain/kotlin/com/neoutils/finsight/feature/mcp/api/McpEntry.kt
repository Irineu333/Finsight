package com.neoutils.finsight.feature.mcp.api

import androidx.navigation.NavGraphBuilder

/**
 * How the section that hosts the MCP server registers its destinations.
 *
 * The section belongs to settings — it is reached from there and from nowhere else — and being a
 * feature module of its own does not change that: settings builds its graph inside its own, so the
 * `hierarchy` of every MCP screen says which section the user is in, and the shell's selector reads
 * it like any other. Since `impl ⊄ impl`, the host cannot name the extension that builds the graph,
 * and asks for the registration here.
 */
interface McpEntry {

    /**
     * Builds the MCP section's graph as a child of the graph under construction.
     *
     * The builder is a context parameter, so the compiler refuses the call anywhere but inside a
     * graph — the only place the registration means anything.
     */
    context(builder: NavGraphBuilder)
    fun register()
}
