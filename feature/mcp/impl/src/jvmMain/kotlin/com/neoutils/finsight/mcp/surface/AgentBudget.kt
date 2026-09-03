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
 *
 * [remaining] and [progress] describe that bar, so both stop at the ceiling — what went past it is
 * a fact of its own, and [isExceeded] with [exceededBy] is where it is stated. Without them a
 * budget that stopped exactly at its limit and one that ran well past it arrive identical.
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
    /**
     * Whether the spending is known to have passed the limit — `null`, never `false`, when no rate
     * reaches part of it. A floor settles nothing against a ceiling, and a `false` there would deny
     * an overrun nothing ruled out, which is the direction a budget must never err in.
     */
    @SerialName("is_exceeded")
    val isExceeded: Boolean? = null,
    /** By how much, and only when [isExceeded] is true — there is no overrun to state otherwise. */
    @SerialName("exceeded_by")
    val exceededBy: AgentFigure? = null,
    /** `fixed`, or `percentage` of a recurring income. */
    @SerialName("limit_type")
    val limitType: String,
    /** The percentage a `percentage` limit is of its base income. */
    val percentage: Double? = null,
)
