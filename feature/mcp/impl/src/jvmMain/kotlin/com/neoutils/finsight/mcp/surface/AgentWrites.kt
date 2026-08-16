package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The payloads of the **registration** family: what a write created or changed, and what it removed.
 *
 * **A write answers with identity, and that is not a nicety.** An agent that creates a category and
 * is told only "done" has to list the categories again to find out which one it made, and it will
 * guess before it lists. Worse, the activity log has to reference what the act produced so the user
 * can reach the posting from it — and it can only reference what the tool knew it wrote. So every
 * creation here comes back as the thing itself, identifier included, straight from the use case that
 * answers it rather than from the arguments the agent sent.
 *
 * A removal is the one exception, and for the same reason: what it produced no longer exists, so it
 * answers with the identity that stopped existing and the words for what went with it.
 */

/** What `create_transaction` and `update_transaction` answer: the posting as the ledger holds it. */
@Serializable
internal data class AgentTransactionWriteAnswer(
    val transaction: AgentTransaction,
    /**
     * Every posting the act wrote, when it wrote more than one — an installment plan is N of them,
     * decided by the use case that owns the dispatch and never by the tool.
     */
    val transactions: List<AgentTransaction> = emptyList(),
    /** The plan the postings belong to, when the form turned out to describe one. */
    val installment: AgentInstallment? = null,
    /** What the act did, said the way the user would read it. */
    val note: String,
)

/** What `create_account` and `update_account` answer. */
@Serializable
internal data class AgentAccountWriteAnswer(
    val account: AgentAccount,
    val note: String,
)

/** What `create_card` and `update_card` answer. */
@Serializable
internal data class AgentCardWriteAnswer(
    val card: AgentCard,
    val note: String,
)

/** What `create_category` and `update_category` answer. */
@Serializable
internal data class AgentCategoryWriteAnswer(
    val category: AgentCategory,
    val note: String,
)

/** What `create_budget` and `update_budget` answer. */
@Serializable
internal data class AgentBudgetWriteAnswer(
    val budget: AgentBudget,
    val note: String,
)

/** What `create_recurring` and `update_recurring` answer. */
@Serializable
internal data class AgentRecurringWriteAnswer(
    val recurring: AgentRecurring,
    val note: String,
)

/** What `update_installment` answers: the plan as it now describes itself. */
@Serializable
internal data class AgentInstallmentWriteAnswer(
    val installment: AgentInstallment,
    val note: String,
)

/** What `create_invoice` answers: the cycle that was brought into existence. */
@Serializable
internal data class AgentInvoiceWriteAnswer(
    val invoice: AgentInvoice,
    val note: String,
)

/**
 * What every `delete_*` answers.
 *
 * [alsoRemoved] is the part an agent must not have to infer: removing an installment takes its
 * postings with it and removing a future invoice takes whatever was booked into it, and a caller
 * told only "removed" would go on believing that money is still there.
 */
@Serializable
internal data class AgentRemovalAnswer(
    /** What kind of thing was removed: `transaction`, `account`, `category`, … */
    val removed: String,
    val id: Long,
    /** What it was called, as it was called at the moment it went. */
    val name: String? = null,
    @SerialName("also_removed")
    val alsoRemoved: List<String> = emptyList(),
    val note: String,
)
