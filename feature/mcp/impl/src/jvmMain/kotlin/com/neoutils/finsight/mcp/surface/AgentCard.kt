package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A credit card as an agent receives it.
 *
 * [limit], [used] and [available] are three figures and not one with arithmetic left over: the
 * available limit is a domain reading, not `limit - used`, and an agent handed two of the three
 * would compute the third with a subtraction the app does not endorse.
 */
@Serializable
internal data class AgentCard(
    val id: Long,
    val name: String,
    /** `null` for a card that never declared one, which then follows the account it settles in. */
    val currency: String? = null,
    @SerialName("closing_day")
    val closingDay: Int,
    @SerialName("due_day")
    val dueDay: Int,
    val limit: AgentFigure? = null,
    /**
     * Everything holding the limit, cycles that have not opened yet included — an instalment
     * holds limit from the moment it is bought. It is therefore **not** what the user owes
     * today; `get_card_overview` splits it into the cycle that is due and the cycles that are
     * merely committed.
     */
    val used: AgentFigure? = null,
    val available: AgentFigure? = null,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
)
