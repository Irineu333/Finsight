package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An invoice as an agent receives it: the window it covers, where it is in its life, and what is
 * owed on it.
 *
 * [status] is the invoice's own lifecycle — `future`, `open`, `closed`, `paid`, `retroactive` —
 * and it is read, never written: marking an invoice paid without posting the payment leaves a
 * balance that lies, so paying is an operation and not a field.
 */
@Serializable
internal data class AgentInvoice(
    val id: Long,
    val card: String,
    @SerialName("card_id")
    val cardId: Long,
    val status: String,
    @SerialName("opening_date")
    val openingDate: LocalDate,
    @SerialName("closing_date")
    val closingDate: LocalDate,
    @SerialName("due_date")
    val dueDate: LocalDate,
    /** What the ledger says is still owed on this invoice. */
    val owed: AgentFigure? = null,
    @SerialName("paid_at")
    val paidAt: LocalDate? = null,
)
