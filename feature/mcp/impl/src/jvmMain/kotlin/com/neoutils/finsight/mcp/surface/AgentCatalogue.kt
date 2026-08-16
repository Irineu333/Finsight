package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The payloads of the **catalogue** family: what exists, what it is called, and the figure that
 * belongs beside it.
 *
 * Two things separate these from the questions family, and both are in every listing below.
 *
 * **A page is not the answer.** A listing carries [AgentTransactionListAnswer.matching] beside
 * [AgentTransactionListAnswer.returned] so that a consumer holding fifty of a hundred and
 * twenty-seven knows it. Without the pair, "the last fifty" and "all of them" are the same payload.
 *
 * **The total beside a page is not the page's.** It comes from a ledger read over the whole filter,
 * which is the only way it can be right — an agent does not scroll to check, so a total it can
 * disagree with the ledger about is a total it will report anyway.
 */

// ----------------------------------------------------------------------------------
// Transactions
// ----------------------------------------------------------------------------------

/** What `list_transactions` answers: a page of postings, and the ledger's totals for the filter. */
@Serializable
internal data class AgentTransactionListAnswer(
    val period: AgentPeriod,
    /** How many postings the filter matches in the ledger — **not** how many came back. */
    val matching: Int,
    /** How many are in this page. */
    val returned: Int,
    val offset: Int,
    /** Whether asking again with a larger `offset` would bring more. */
    @SerialName("has_more")
    val hasMore: Boolean,
    /** The order the page was cut in: `date` or `recorded`. Total, so paging loses nothing. */
    @SerialName("ordered_by")
    val orderedBy: String,
    /**
     * The account or card this list is read through, when the call named one — and `null` when it
     * did not, which is exactly when the items carry no `direction`.
     */
    @SerialName("read_from")
    val readFrom: String? = null,
    val totals: AgentListingTotals,
    val transactions: List<AgentTransaction>,
    val perimeter: AgentPerimeter,
)

/**
 * The money a listing's filter comes to, **read from the ledger and never summed from the page**.
 *
 * That is the whole reason this type exists rather than a number the caller could add up. A page
 * holds fifty of a hundred and twenty-seven postings, and a total taken from it is wrong by exactly
 * the seventy-seven nobody looked at — silently, and in the direction of "you spent less than you
 * did".
 *
 * [narrowedBy] names the arguments of the call these two figures reflect. It is not documentation:
 * an argument that cuts the list without moving the totals has to be visible as such, or the pair
 * reads as the total of what is beside it.
 */
@Serializable
internal data class AgentListingTotals(
    val income: AgentFigure,
    val expense: AgentFigure,
    /** The ledger read the two came from, and what it counts. */
    val basis: String,
    /** The arguments of the call these figures are narrowed by, named as the schema names them. */
    @SerialName("narrowed_by")
    val narrowedBy: List<String>,
)

/**
 * What `get_transaction` answers: one posting with **every** monetary leg, not the single line a
 * listing reads it through.
 *
 * A listing shows one figure because it is a list of lines; this is the operation itself, and a
 * transfer or a card payment has two ends. Both are the ledger's own amounts, and where they
 * disagree on currency [appliedRate] is the quotient between them — the rate the operation actually
 * got, never one from the archive.
 */
@Serializable
internal data class AgentTransactionDetailAnswer(
    val id: Long,
    /** What the ledger derives this to be: `expense`, `income`, `transfer`, `payment`, `adjustment`. */
    val nature: String,
    val title: String,
    val date: LocalDate,
    /** One entry per leg that holds money — an account or a card, never a category. */
    val legs: List<AgentLeg>,
    val category: String? = null,
    @SerialName("category_id")
    val categoryId: Long? = null,
    @SerialName("is_uncategorized")
    val isUncategorized: Boolean = false,
    /** Which instalment of a plan this is, as the user reads it — `"3/12"`. */
    val installment: String? = null,
    @SerialName("installment_id")
    val installmentId: Long? = null,
    @SerialName("recurring_id")
    val recurringId: Long? = null,
    @SerialName("recurring_cycle")
    val recurringCycle: Int? = null,
    @SerialName("applied_rate")
    val appliedRate: AgentAppliedRate? = null,
    val perimeter: AgentPerimeter,
)

/**
 * One leg of a posting that holds money: which account or card it landed on, and which way.
 *
 * [direction] is the leg's own — `expense`, `income` or `adjustment`, derived by the ledger from the
 * leg's sign — and it is safe here precisely because **every** leg is present: a transfer shows one
 * of each, so neither can be mistaken for a property of the transaction the way a single leg's
 * direction would be.
 */
@Serializable
internal data class AgentLeg(
    /** `account` or `card`. */
    val kind: String,
    val name: String,
    @SerialName("account_id")
    val accountId: Long,
    val direction: String,
    /**
     * The leg's own amount as the ledger recorded it, exact, in the currency its account declares:
     * **signed, debit-positive**. Negative is money that left an account or was charged to a card.
     * No display rule is applied — [direction] already says which way it went, and the legs of one
     * operation have to add up to nothing for anybody checking them.
     */
    val amount: AgentFigure,
    /** The invoice a card leg landed on, when the caller could resolve one. */
    @SerialName("invoice_id")
    val invoiceId: Long? = null,
)

/**
 * The rate an operation actually applied, read off its own two ends.
 *
 * It is a quotient and never a stored figure: nothing on the write path carries a rate, so the
 * detail derives it exactly as the form that wrote it did — units of [to] per one unit of [from].
 */
@Serializable
internal data class AgentAppliedRate(
    val from: String,
    val to: String,
    val rate: Double,
)

// ----------------------------------------------------------------------------------
// The facades
// ----------------------------------------------------------------------------------

/** What `list_accounts` answers: the accounts, each with its balance, and the ledger's total. */
@Serializable
internal data class AgentAccountListAnswer(
    @SerialName("as_of")
    val asOf: AgentPeriod,
    val accounts: List<AgentAccount>,
    /**
     * Every listed account together, from the ledger's own aggregate over the same set — not the
     * sum of the balances above it, which is the same number by a route that could stop being one.
     */
    val total: AgentFigure,
    val perimeter: AgentPerimeter,
)

/** What `list_cards` answers: the cards and what is left of each limit. */
@Serializable
internal data class AgentCardListAnswer(
    val cards: List<AgentCard>,
    val perimeter: AgentPerimeter,
)

/** What `list_categories` answers: every category, with what moved under it in the period. */
@Serializable
internal data class AgentCategoryListAnswer(
    val period: AgentPeriod,
    val categories: List<AgentCategory>,
    val perimeter: AgentPerimeter,
)

/** What `list_invoices` answers: a card's invoices over time, with what each still owes. */
@Serializable
internal data class AgentInvoiceListAnswer(
    val matching: Int,
    val returned: Int,
    val offset: Int,
    @SerialName("has_more")
    val hasMore: Boolean,
    val invoices: List<AgentInvoice>,
    /**
     * What **every matching** invoice owes together — the whole filter, not the page. Each term is
     * a ledger read of one invoice's dimension; adding them across currencies is the reducer's.
     */
    @SerialName("owed_total")
    val owedTotal: AgentFigure,
    val perimeter: AgentPerimeter,
)

/** What `get_invoice` answers: one invoice's window, its life, its figures and its statement. */
@Serializable
internal data class AgentInvoiceDetailAnswer(
    val invoice: AgentInvoice,
    /** The window the invoice covers: from the day it opened to the day it closes. */
    val period: AgentPeriod,
    /**
     * What was charged to it. `null` — never zero — when the invoice's money could not be stated
     * as one figure: it is denominated by the card it sits on, and a card that carries no currency
     * denominates nothing.
     */
    val spent: AgentFigure? = null,
    /** What was paid into it before it was settled. */
    @SerialName("advance_payments")
    val advancePayments: AgentFigure? = null,
    /** What was adjusted on it by hand, signed. */
    val adjustment: AgentFigure? = null,
    val matching: Int,
    val returned: Int,
    val offset: Int,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("ordered_by")
    val orderedBy: String,
    /** The postings that landed on this invoice, read from the card. */
    val statement: List<AgentTransaction>,
    val perimeter: AgentPerimeter,
)

/** What `list_installments` answers: the plans, how far each has got, and what one instalment costs. */
@Serializable
internal data class AgentInstallmentListAnswer(
    val installments: List<AgentInstallment>,
    val perimeter: AgentPerimeter,
)

/** What `list_budgets` answers: the budgets as they were set up. */
@Serializable
internal data class AgentBudgetListAnswer(
    val budgets: List<AgentBudget>,
    val perimeter: AgentPerimeter,
)

/** What `list_recurring` answers: every template, and whether this month's cycle is still waiting. */
@Serializable
internal data class AgentRecurringListAnswer(
    val period: AgentPeriod,
    val recurring: List<AgentRecurring>,
    val perimeter: AgentPerimeter,
)
