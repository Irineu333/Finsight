package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An account as an agent receives it.
 *
 * [balance] is `null` when the answer did not include one — a tool that just created the account,
 * or one whose question was about the account and not about its money. It is never a zero standing
 * in for an unread figure: a zero is an assertion, and the agent would report it as one.
 */
@Serializable
internal data class AgentAccount(
    val id: Long,
    val name: String,
    /** The one currency this account is denominated in. It has no default and never changes. */
    val currency: String,
    val balance: AgentFigure? = null,
    @SerialName("is_default")
    val isDefault: Boolean = false,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
    /** Whether the account declares that it yields, which is what makes a yield posting legal on it. */
    @SerialName("yields_interest")
    val yieldsInterest: Boolean = false,
)
