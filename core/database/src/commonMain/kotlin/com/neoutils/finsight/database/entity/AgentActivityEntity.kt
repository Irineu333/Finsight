@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One act an agent performed through the local MCP server: **when** it happened, **which**
 * operation it was, **what** it was about in words the user recognises, and **how it ended**
 * — plus the reference that reaches whatever it created or changed.
 *
 * This is the only place in the app where the **authorship** of a write appears. Every other
 * surface shows the result — the transaction is simply there — and a posting made from
 * outside is indistinguishable, in a list, from one the user forgot making.
 *
 * **A row is a trace, never accounting truth.** What was posted lives in the ledger, which is
 * where every figure is derived from; this table only says who did it and when. Deleting a row
 * therefore undoes nothing, and no read of the ledger consults this table.
 *
 * **Reads leave no row.** There is no column marking a query, because a query never becomes a
 * row: an agent asks dozens of questions to answer one, and listing them would drown exactly
 * what the log exists to show. A read alters nothing and has nothing to audit.
 *
 * **[summary] is frozen and [referenceKind]/[referenceId] are live, and the split is the
 * point.** The summary is the sentence as it was true at the instant of the act — the account
 * and the category under the names they had then. It is not refreshed when something is
 * renamed later, because it is testimony about a past act and rewriting it would falsify the
 * record; and it is not a second source of truth precisely because nothing reads it back as
 * data. The reference is the opposite half: an identity, which the section resolves against
 * the ledger to reach the posting as it is *now*.
 *
 * **The reference is deliberately not a foreign key.** The log must never keep a posting from
 * being deleted, and a row whose target is gone still carries a fact worth having — the act
 * happened, and what it touched no longer exists. Room's facade tables take the same shape for
 * the same reason with their installment and recurring links.
 */
@Entity(
    tableName = "agent_activity",
    indices = [
        // Both reads and both halves of the retention policy order by this column, and
        // it is the only column any of them looks at.
        Index(value = ["at"]),
    ],
)
data class AgentActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The instant of the act, which is what every read and the retention policy order by. */
    val at: Instant,
    /**
     * The stable identity of the operation — the tool's own name, as the agent called it.
     *
     * An identity and not a label: it survives a rewording, and the section is what turns it
     * into something to read. Labelling it here would freeze the app's language into stored
     * data, the same defect [summary] accepts deliberately and this column has no reason to.
     */
    val operation: String,
    /** What the act was about, in the user's words, as they were true when it happened. */
    val summary: String,
    val outcome: Outcome,
    /** Why an act was refused. `null` when it was applied — there is nothing to explain. */
    val detail: String? = null,
    /** What kind of thing the act reached, or `null` when it created and changed nothing. */
    val referenceKind: ReferenceKind? = null,
    /** The identity of that thing, unenforced: the row it names may since have been deleted. */
    val referenceId: Long? = null,
) {

    /**
     * How the act ended.
     *
     * Two values, because those are the two things that can happen to a write: it was applied,
     * or something said no. A refusal is recorded for the user's sake — it is what explains why
     * the agent reported that it could not do something.
     */
    enum class Outcome {
        /** The operation went through, and the ledger holds its result. */
        APPLIED,

        /** Permission or the domain refused it; nothing was written, and `detail` says why. */
        REFUSED,
    }

    /** The kind of thing a reference names, which is what tells the section where to resolve it. */
    enum class ReferenceKind {
        TRANSACTION,
        ACCOUNT,
        CATEGORY,
        CREDIT_CARD,
        INVOICE,
        INSTALLMENT,
        RECURRING,
        BUDGET,
    }
}
