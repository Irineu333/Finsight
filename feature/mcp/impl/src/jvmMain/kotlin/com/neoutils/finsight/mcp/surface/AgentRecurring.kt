package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A recurring template as an agent receives it.
 *
 * It is a template and not a posting: nothing is in the ledger until a cycle is confirmed, which is
 * why [isPending] exists at all. An agent that reads the amount of a pending recurring is reading
 * what *would* be posted, and must not report it as money already moved.
 */
@Serializable
internal data class AgentRecurring(
    val id: Long,
    /** What the user chose when creating it: `expense`, `income` or `adjustment`. */
    val type: String,
    val title: String,
    val amount: AgentFigure,
    /** The day of the month the cycle falls on, clamped to the month's length where it overruns. */
    @SerialName("day_of_month")
    val dayOfMonth: Int,
    val category: String? = null,
    @SerialName("category_id")
    val categoryId: Long? = null,
    val account: String? = null,
    @SerialName("account_id")
    val accountId: Long? = null,
    val card: String? = null,
    @SerialName("card_id")
    val cardId: Long? = null,
    @SerialName("next_occurrence")
    val nextOccurrence: LocalDate? = null,
    /** Whether the cycle the answer is about is still waiting to be confirmed or skipped. */
    @SerialName("is_pending")
    val isPending: Boolean? = null,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
)
