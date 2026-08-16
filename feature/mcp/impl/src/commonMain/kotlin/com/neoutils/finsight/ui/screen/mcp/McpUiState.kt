package com.neoutils.finsight.ui.screen.mcp

import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.feature.categories.api.CategoriesRoute
import com.neoutils.finsight.feature.creditcards.api.CreditCardsRoute
import com.neoutils.finsight.feature.creditcards.api.InstallmentsRoute
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.neoutils.finsight.feature.mcp.api.McpServerState
import com.neoutils.finsight.feature.mcp.api.toPortFieldUiText
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.mcp_port_error_invalid
import com.neoutils.finsight.util.UiText
import kotlin.time.Instant

/**
 * The MCP server section.
 *
 * **The order is deliberate and it is a requirement, not a taste.** With the server off there is no
 * address to point a client at, no token to present, no permission that governs anything and no
 * instructions that would work — so [showsDetails] is false and none of it is drawn. Switching the
 * server on must not be preceded by a single other decision.
 *
 * **What is shown about the socket is a fact about the socket.** [server] is collected from the
 * controller for as long as the screen is open, so a bind that fails after the app started reads as
 * failed here too; nothing on this screen is derived from the switch being on.
 */
data class McpUiState(
    /**
     * Whether this platform has a server at all. False everywhere but the desktop, where the entry
     * point into this section is not offered — but the route exists on every target, and a screen
     * reached anyway must say what is true rather than offer a switch that turns nothing on.
     */
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val server: McpServerState = McpServerState.Stopped,
    val port: Int = McpServerController.DEFAULT_PORT,
    val token: String? = null,
    val isTokenRevealed: Boolean = false,
    val permissions: List<McpPermissionUi> = emptyList(),
    val recentActivity: List<McpActivityUi> = emptyList(),
) {

    /** Address, token, permissions and instructions: only once there is a server to connect to. */
    val showsDetails: Boolean get() = isSupported && isEnabled

    val isRunning: Boolean get() = server is McpServerState.Running

    /**
     * How many clients hold a session **right now** — a different fact from being enabled, and the
     * only one that means something may be reading the finances at this moment.
     */
    val sessions: Int get() = (server as? McpServerState.Running)?.sessions ?: 0

    val hasConnectedClient: Boolean get() = sessions > 0

    /** The address a client is configured with, which outlives a bind that failed. */
    val address: String get() = "http://$LOOPBACK_HOST:$port$MCP_PATH"

    /** The token as the screen shows it: masked until the user asks for it. */
    val displayedToken: String? get() = token?.let { if (isTokenRevealed) it else MASK }

    /**
     * What is wrong with the address, which in practice is always its port.
     *
     * It is said **on the address row** because that is where the port is visible and where the
     * affordance to move it sits: a failure reported anywhere else would leave the user reading
     * about a port with nothing to act on nearby. The address a client was configured with survives
     * a bind that failed, so the row keeps showing it — what changes is that it now says it is not
     * answering, and why.
     */
    val addressError: UiText?
        get() = (server as? McpServerState.Failed)?.toPortFieldUiText()

    private companion object {

        const val LOOPBACK_HOST = "127.0.0.1"

        const val MCP_PATH = "/mcp"

        /** Not text to translate: it stands for characters, and every language hides them alike. */
        const val MASK = "••••••••••••••••"
    }
}

/**
 * One permission axis as the section states it: the switch, and **what flipping it does**.
 *
 * [toolCount] is the whole point of the row beyond the switch. A capability granted without knowing
 * how much it hands over is granted blind, so a granted axis says how many tools it gives and a
 * withheld one says how many it is holding back. The number comes from the controller, which reads
 * the one place the surface is counted — the same count the socket announces.
 */
data class McpPermissionUi(
    val axis: McpPermissionAxis,
    val isGranted: Boolean,
    val toolCount: Int,
)

/**
 * One act of an agent, as the section shows it.
 *
 * [target] is what makes the entry more than a sentence: it is where the user goes to see what the
 * act produced. It is deliberately not a promise that the thing is still there — the log is
 * testimony about the past, and an entry stays valid, listed and readable after whatever it created
 * has been removed. [isTargetGone] says so where the app can know it.
 */
data class McpActivityUi(
    val id: Long,
    val at: Instant,
    /** The tool's own name, as the agent called it — an identity that survives a rewording. */
    val operation: String,
    /** What the act was about, in the words that were true when it happened. */
    val summary: String,
    val isRefused: Boolean,
    /** Why it was refused, or `null` when it went through. */
    val detail: String?,
    val target: McpActivityTarget?,
    val isTargetGone: Boolean = false,
)

/**
 * Where an entry of the log leads.
 *
 * A posting is reached exactly — it is the thing the record exists to let the user check — and
 * everything else is reached at the section that holds it, which is as precise as the reference
 * allows: an invoice's own screen, for one, is addressed by the card that holds it, and the
 * reference names the invoice alone.
 */
sealed interface McpActivityTarget {

    /** The posting itself, opened as the same detail every list in the app opens. */
    data class Posting(val transactionId: Long) : McpActivityTarget

    /** The section the referenced thing lives in. */
    data class Section(val route: NavRoute) : McpActivityTarget
}

/**
 * Where a reference leads, decided once.
 *
 * `null` is impossible by construction: every kind the log can record has somewhere to go, and a
 * kind added later without a destination fails to compile here rather than becoming a row that
 * silently does nothing.
 */
fun AgentActivity.Reference.toTarget(): McpActivityTarget = when (kind) {
    AgentActivity.Reference.Kind.TRANSACTION -> McpActivityTarget.Posting(id)
    AgentActivity.Reference.Kind.ACCOUNT -> McpActivityTarget.Section(AccountsRoute(accountId = id))
    AgentActivity.Reference.Kind.CREDIT_CARD -> McpActivityTarget.Section(CreditCardsRoute(creditCardId = id))
    AgentActivity.Reference.Kind.INVOICE -> McpActivityTarget.Section(CreditCardsRoute())
    AgentActivity.Reference.Kind.CATEGORY -> McpActivityTarget.Section(CategoriesRoute)
    AgentActivity.Reference.Kind.INSTALLMENT -> McpActivityTarget.Section(InstallmentsRoute)
    AgentActivity.Reference.Kind.RECURRING -> McpActivityTarget.Section(RecurringRoute)
    AgentActivity.Reference.Kind.BUDGET -> McpActivityTarget.Section(BudgetsRoute)
}
