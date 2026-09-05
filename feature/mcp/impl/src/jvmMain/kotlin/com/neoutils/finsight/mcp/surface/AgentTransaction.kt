package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A transaction as an agent receives it: flat, already resolved, carrying identifiers and names and
 * no domain graph at all.
 *
 * The names are not decoration. An agent that receives `category_id: 7` has to spend a call to find
 * out it was groceries, and will more often guess — so whatever a person would expect to read is
 * here beside the identifier that acts on it.
 *
 * **[nature] and [direction] answer different questions, and only one of them is always available.**
 * The nature is what the ledger says this transaction *is*, derived from the account types of its
 * legs, and it is true from anywhere. The direction is which way the money went **as seen from one
 * account**, so it exists only when the caller named one: a transfer between the user's own accounts
 * is an outflow of one and an inflow of the other, and reporting either as a property of the
 * transaction is how transfers end up counted as spending.
 */
@Serializable
internal data class AgentTransaction(
    val id: Long,
    /** What the ledger derives this to be: `expense`, `income`, `transfer`, `payment`, `adjustment`. */
    val nature: String,
    /**
     * Which way the money moved in the account the caller asked about: `expense`, `income` or
     * `adjustment`. `null` when no account was named — there is then no point of view to answer
     * from, and the nature is the whole answer.
     */
    val direction: String? = null,
    val title: String,
    /** The figure this line reads as, with its currency and its sign already resolved. */
    val amount: AgentFigure,
    val date: LocalDate,
    /** The account whose leg this line is read through. */
    val account: String,
    @SerialName("account_id")
    val accountId: Long,
    val category: String? = null,
    @SerialName("category_id")
    val categoryId: Long? = null,
    /**
     * Whether this transaction is *unclassified* — which is not the same as having no
     * [categoryId]. A transfer, a card payment and an adjustment have no analytic axis at all, so
     * they are outside classification rather than missing it, and no unclassified total contains
     * them.
     */
    @SerialName("is_uncategorized")
    val isUncategorized: Boolean = false,
    /** Whether it posts to a card rather than moving money in an account. */
    @SerialName("is_on_card")
    val isOnCard: Boolean = false,
    @SerialName("is_recurring")
    val isRecurring: Boolean = false,
    /** Which instalment of a plan this is, as the user reads it — `"3/12"`. */
    val installment: String? = null,
)
