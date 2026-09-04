package com.neoutils.finsight.mcp

import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.tool.agentJson

/**
 * What the app says while the user has its MCP server switched off.
 *
 * **It speaks, and that is the point.** The window simply binds no socket when the switch is off,
 * so the question never arises there; a process a client launched has already been launched, and
 * exiting would show the agent a program that died — which it would report as the app being broken,
 * or missing, and never as a switch of the user's own. So the handshake completes, `tools/list`
 * answers with nothing, and every call is refused in words that name the switch and where it lives
 * (design D7).
 *
 * **These are two sentences and not a server.** Whether the switch is off is a fact about the
 * instant a request arrives, not about the instant a process started: the user can switch the
 * server off while their agent is connected, and on again a minute later, and a session assembled
 * around either answer would be wrong for the rest of its life. So the question is asked where the
 * ownership of the database is already asked — per request, in `McpStdioCallSite` — and this is
 * what that site answers with.
 *
 * **Nothing of the app runs while it is off.** No tool is reached, no row is written and the
 * database is never opened: the refusal is decided before any of it, and the session holds the
 * surface without ever asking anything of it.
 */
internal object McpServerOff {

    /**
     * What the agent is told before its first question, so it never asks one.
     *
     * Read at the handshake, which is the one thing about a session that is settled once — see
     * `McpCallSite.instructions` for why that is right rather than a leftover.
     */
    val INSTRUCTIONS: String = buildString {
        append(McpPermissionNotice.WHAT_THIS_IS)
        append(" Its MCP server is switched off right now: nothing is offered here and no call ")
        append("will run.\n\n")
        append("Only this user can switch it on, in ")
        append(McpPermissionNotice.THE_SECTION)
        append(". When they ask for something the app does, tell them the app does it and is ")
        append("waiting on them to switch the server on, and where to do it. Never tell them the ")
        append("app cannot do it.")
    }

    /**
     * What every call is answered with, in the same words and for the same reason.
     *
     * Every call, and not only the ones on a name the app has: a switched-off app is not offering a
     * surface at all, so what it can say about a name is nothing — and *"tool not found"* on a
     * genuine operation is the exact false statement about the app that `McpPermissionNotice`
     * exists to prevent.
     */
    val REFUSAL: String = agentJson.encodeToString(
        AgentRefusal(
            reason = "The Finsight MCP server is switched off. The operation exists and nothing " +
                "here will run until this user switches the server on, in " +
                "${McpPermissionNotice.THE_SECTION}. Say the app does this and is waiting on " +
                "their permission — never that it cannot.",
        ),
    )
}
