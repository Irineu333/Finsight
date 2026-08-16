package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An instalment plan as an agent receives it.
 *
 * [paid] and [remaining] are counts of instalments, not money, and they are `null` when the answer
 * did not resolve the plan's transactions — an agent must not read an absent count as zero
 * instalments paid.
 */
@Serializable
internal data class AgentInstallment(
    val id: Long,
    val title: String? = null,
    val card: String? = null,
    @SerialName("card_id")
    val cardId: Long? = null,
    /** How many instalments the plan has in total. */
    val count: Int,
    val paid: Int? = null,
    val remaining: Int? = null,
    /** What the whole plan costs. */
    val total: AgentFigure? = null,
    /** What one instalment of it costs. */
    @SerialName("installment_amount")
    val installmentAmount: AgentFigure? = null,
)
