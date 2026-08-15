package com.neoutils.finsight.feature.mcp.api

import com.neoutils.finsight.navigation.NavRoute
import kotlinx.serialization.Serializable

/**
 * The MCP server screen: the switch, the address it listens on, the permission level, the
 * token with its rotate action, the connection instructions, and the way to recent activity.
 *
 * It lives in the `api` because **`settings:impl` names it** — settings holds the single
 * entry to this capability, and `impl ⊄ impl`. That is the whole criterion for being here:
 * only what another module consumes is promoted.
 *
 * There is exactly one path to the switch — Settings → this screen — because two doors to
 * the same switch make it impossible to answer where the capability was turned on.
 */
@Serializable
data object McpRoute : NavRoute
