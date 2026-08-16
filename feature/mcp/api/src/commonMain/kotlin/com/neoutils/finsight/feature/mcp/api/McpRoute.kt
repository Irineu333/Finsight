package com.neoutils.finsight.feature.mcp.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The MCP server section: whether the server is up, and how a client reaches it.
 *
 * Externally navigable because the user goes looking for it in the app's settings, among the
 * other integrations. Settings names this destination and nothing else of the feature — the
 * section itself, with its switch, its address and its token, belongs here.
 */
@Serializable
data object McpRoute : NavRoute
