package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A category as an agent receives it.
 *
 * [type] is `expense` or `income`, and it is the user's declaration rather than something derived:
 * nothing in the ledger produces it, which is why it is the one thing about a category that is
 * primary state.
 *
 * [share] is this category's part of the period's whole, between `0` and `1`. It is `null` — never
 * zero — when there is no answer: nothing about the figure could be placed on a common scale, or
 * some other category could not, and a share of an unknown whole is not a measurement.
 */
@Serializable
internal data class AgentCategory(
    val id: Long,
    val name: String,
    val type: String,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
    /** What was spent or earned under it in the period the answer is about. */
    val total: AgentFigure? = null,
    val share: Double? = null,
)
