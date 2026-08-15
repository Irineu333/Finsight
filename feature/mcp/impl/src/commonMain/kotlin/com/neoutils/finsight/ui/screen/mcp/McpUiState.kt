package com.neoutils.finsight.ui.screen.mcp

import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import com.neoutils.finsight.feature.mcp.api.McpPermission

/**
 * The MCP server screen, in the **three states the server can be in** — and they are three types
 * rather than three flags precisely because the screen must not present the same content in two
 * of them. What each state offers is decided here; the screen renders the state it is given and
 * chooses nothing.
 *
 * The states are the server's, not the configuration's: what the user needs to know is whether
 * something is listening, and *enabled* alone does not answer that. A port already taken is the
 * case that separates the two.
 */
sealed interface McpUiState {

    /**
     * Nothing is listening, and the screen says only what enabling would mean.
     *
     * **No connection snippet is offered here.** With nothing listening, a copyable snippet
     * produces a configured client that fails, and the user blames the client. Nothing else on
     * the screen has to be available in this state either: there is no address to show, no level
     * to matter yet, and no activity a server that never ran could have produced.
     */
    data class Off(
        /**
         * The switch's position. Normally `false`; it is `true` for the moment between the user
         * enabling the server and the socket existing, and saying "on" while nothing listens
         * would be the one thing this state must not do — so the state is still [Off], and only
         * the switch follows the user's gesture.
         */
        val isEnabled: Boolean = false,
    ) : McpUiState

    /**
     * Listening — and everything a user needs is offered **at once**: the address, the level with
     * both values and the one in force marked, the token hidden with its rotate action, the
     * connection instructions, and the recent activity.
     *
     * Ready at the **first** `on`, because a switch turned on with a token nobody pasted anywhere
     * is a "working" state that does not work.
     */
    data class Listening(
        /** The whole endpoint, as the server reports it. A client's configuration holds this. */
        val endpoint: String,
        /** The level in force. Both values are always offered; this is the one marked. */
        val permission: McpPermission,
        /** The bearer credential, in clear. Rendered only when [isTokenVisible]. */
        val token: String,
        /** Hidden by default; the user reveals it deliberately. */
        val isTokenVisible: Boolean,
        /**
         * What goes to the clipboard: a client configuration **ready to paste**, with the address
         * and the token already in place. An address and a token the user has to assemble into
         * some format themselves would not be this.
         */
        val clientConfig: String,
        /**
         * The revision of the protocol the listening server speaks. A client that speaks another
         * fails to connect, and nothing else on the screen would explain the failure.
         */
        val protocolRevision: String,
        /**
         * Whether the instructions must warn that **no write will be visible** to the agent.
         * Freshly enabled, a client connects, works, and is announced no write tool at all; left
         * unsaid, the natural reading is that the server is broken.
         */
        val isReadOnly: Boolean,
        /** The journal, newest first. Empty until an agent writes something. */
        val activity: List<AgentActivityUi>,
    ) : McpUiState

    /**
     * The persisted port is taken, so **the server did not start**.
     *
     * Presented as neither on nor off: in either reading the user would conclude the state is the
     * one they asked for. It declares that nothing is listening, names the conflict, and offers
     * choosing another port deliberately — no other port is ever assumed.
     */
    data class PortUnavailable(
        val port: Int,
        /** What the operating system said about the conflict, for the screen to name it. */
        val reason: String,
    ) : McpUiState
}

/**
 * One line of the journal, as the screen shows it.
 *
 * There is **no generic undo**: not every operation has an inverse, and some have one that does
 * not restore the previous state. A line leads to the entity it touched, where the inverse
 * operation already lives when the domain offers one — which is why [target] is a destination and
 * not a command.
 */
data class AgentActivityUi(
    val id: Long,
    /** Local civil date and time of the call, already formatted. */
    val timestamp: String,
    /**
     * What the client called itself, or `null` when it never introduced itself.
     *
     * **Self-declared and not authenticated**, and the screen says so: what authenticates is the
     * token, and the token is the same for every client.
     */
    val client: String?,
    val tool: String,
    val outcome: AgentActivityOutcome,
    /**
     * Where the line leads, or `null` when what it touched is not reachable in the interface.
     * Only what the app can actually open becomes a destination; a dead link would be worse than
     * none.
     */
    val target: AgentActivityTarget?,
)

/** The entity a journal line touched, resolved to something the interface can open. */
sealed interface AgentActivityTarget {

    /** A transaction, opened in the same modal the rest of the app opens it with. */
    data class Transaction(val id: Long) : AgentActivityTarget
}

/** What the user can do on this screen. */
sealed interface McpAction {

    /** The single switch of the capability: whether the MCP server exists. */
    data class SetEnabled(val isEnabled: Boolean) : McpAction

    /** The level, a decision of its own — never a side effect of the switch. */
    data class SetPermission(val permission: McpPermission) : McpAction

    /** The deliberate choice of another port, offered when the persisted one is taken. */
    data class SetPort(val port: Int) : McpAction

    /** Revocation: the previous token stops being accepted at once, without stopping the server. */
    data object RotateToken : McpAction

    /** Reveals or hides the token on screen. It changes nothing that is persisted. */
    data object ToggleTokenVisibility : McpAction
}
