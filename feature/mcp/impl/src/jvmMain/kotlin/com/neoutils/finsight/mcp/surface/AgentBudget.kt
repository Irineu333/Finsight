package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A budget as an agent receives it.
 *
 * [limit] is denominated in the currency the budget was created in, which is chosen once and never
 * moves — everything spent under it is re-expressed against that, rather than the budget following
 * whatever currency the spending happened in.
 *
 * [progress] is the fraction of the limit already spent, and it is `null` rather than zero when no
 * rate could bring the spending onto the limit's currency: a bar that cannot be drawn is not a bar
 * at zero.
 */
@Serializable
internal data class AgentBudget(
    val id: Long,
    val title: String,
    /** The categories the budget watches, by name — the whole point of it. */
    val categories: List<String> = emptyList(),
    val limit: AgentFigure,
    val spent: AgentFigure? = null,
    val remaining: AgentFigure? = null,
    val progress: Double? = null,
    /** `fixed`, or `percentage` of a recurring income. */
    @SerialName("limit_type")
    val limitType: String,
    /** The percentage a `percentage` limit is of its base income. */
    val percentage: Double? = null,
)
