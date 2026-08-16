package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.YearMonth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The payloads of the **operations** family: what moved, and what it left the world looking like.
 *
 * They differ from the registration family's in one way, and it is the way that matters here. A
 * registration answers with the thing it wrote, because that thing *is* the act. An operation's act
 * is a change of state — an invoice settled, a balance corrected, a cycle passed over — and the
 * thing named in the call is only where it landed. So each of these answers with the subject **as it
 * stands afterwards**, read back rather than echoed: an agent that is told "paid" and then reports
 * the amount it sent is reporting the argument it typed, not what the app now holds.
 */

/**
 * What every invoice operation answers: the cycle as it stands afterwards, and the posting the
 * operation wrote when it wrote one.
 *
 * The two are separate fields because only some of these write money. Closing, opening and
 * reopening move a cycle through its life and post nothing; paying and adjusting post. A single
 * field would make the difference invisible, and "did the money leave?" is the question the agent
 * is about to answer for the user.
 */
@Serializable
internal data class AgentInvoiceOperationAnswer(
    val invoice: AgentInvoice,
    /** The posting the operation wrote, or `null` when it wrote none. */
    val transaction: AgentTransaction? = null,
    /** What the act did, said the way the user would read it. */
    val note: String,
)

/**
 * What `skip_recurring` answers: the template, and the month that will stop being offered.
 *
 * The month is stated because it is the whole content of the decision — a skip writes no posting
 * and produces no entry, so without it the answer would describe nothing that happened.
 */
@Serializable
internal data class AgentCycleAnswer(
    val recurring: AgentRecurring,
    /** The month the cycle was filed under, as `2026-03`. */
    val month: String,
    val note: String,
) {
    constructor(recurring: AgentRecurring, month: YearMonth, note: String) :
        this(recurring, month.toString(), note)
}

/**
 * What `archive_entity` and `unarchive_entity` answer.
 *
 * [entity] repeats back the kind that was operated on, because the tool is discriminated and an
 * agent holding four identifiers has to be able to tell which one the answer is about. [isArchived]
 * is read back from the store rather than assumed from the tool that was called: archiving is the
 * one operation whose whole effect is a flag, and echoing the flag the caller asked for would make
 * the answer true by construction.
 */
@Serializable
internal data class AgentArchiveAnswer(
    /** Which kind this was: `account`, `card`, `category` or `recurring`. */
    val entity: String,
    val id: Long,
    val name: String,
    @SerialName("is_archived")
    val isArchived: Boolean,
    val note: String,
)
