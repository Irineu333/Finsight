package com.neoutils.finsight.mcp.surface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The payloads of the **questions** family: what the app calculated, in the shape an agent can
 * repeat to a person without doing arithmetic of its own.
 *
 * Every one of them carries three things beyond the number — the currency it is expressed in
 * ([AgentFigure]), the stretch of time it covers and whether that has finished ([AgentPeriod]), and
 * what it is a total *of* ([AgentPerimeter]). None of the three is decoration: a figure missing any
 * one of them is a number that reads as an answer and is not one.
 */

/** What `get_balance` answers: money sitting in accounts, with card debt untouched. */
@Serializable
internal data class AgentBalanceAnswer(
    val balance: AgentFigure,
    /** The account the question was scoped to, when it named one. */
    val account: AgentAccount? = null,
    @SerialName("as_of")
    val asOf: AgentPeriod,
    val perimeter: AgentPerimeter,
)

/** What `get_net_worth` answers: what is owned less what is owed, in one figure. */
@Serializable
internal data class AgentNetWorthAnswer(
    @SerialName("net_worth")
    val netWorth: AgentFigure,
    @SerialName("as_of")
    val asOf: AgentPeriod,
    val perimeter: AgentPerimeter,
)

/**
 * What `get_month_summary` answers.
 *
 * [expense] is the month's whole spending; [expenseFromAccounts] and [expenseOnCards] are its two
 * disjoint halves — a card purchase has no account leg, so aggregating them cannot double-count.
 * They are all three present because "how much did I spend" and "how much left my accounts" are
 * different questions, and an agent handed only one of them answers the other one wrongly.
 *
 * [invoicePayments] sits outside every total on purpose: both of its legs are inside this perimeter,
 * so it moves nothing — it is reported because a person asks about it, not because it counts.
 *
 * [openingNet] and [closingNet] are the **net** position — what is in the accounts less what is owed
 * on the cards — and are named for it rather than "balance", because a balance in this surface means
 * the accounts alone. The two are different numbers and neither is recoverable from the other.
 */
@Serializable
internal data class AgentMonthSummaryAnswer(
    val period: AgentPeriod,
    @SerialName("opening_net")
    val openingNet: AgentFigure,
    val income: AgentFigure,
    /** The slice of [income] posted to a yielding account, already taken out of it. */
    @SerialName("yield")
    val yields: AgentFigure,
    val expense: AgentFigure,
    @SerialName("expense_from_accounts")
    val expenseFromAccounts: AgentFigure,
    @SerialName("expense_on_cards")
    val expenseOnCards: AgentFigure,
    @SerialName("invoice_payments")
    val invoicePayments: AgentFigure,
    val adjustment: AgentFigure,
    @SerialName("closing_net")
    val closingNet: AgentFigure,
    val perimeter: AgentPerimeter,
    @SerialName("compared_to")
    val comparedTo: AgentComparison? = null,
)

/**
 * One nature's breakdown of a period: every category that moved, ranked, plus the money that
 * carried no category at all.
 *
 * [uncategorized] is a line of the same total and not a leftover — its [AgentCategory.id] is `0`
 * and its share is taken off the same scale as everyone else's, which is what makes the shares add
 * up to the period rather than to the classified part of it.
 */
@Serializable
internal data class AgentCategoryBreakdownAnswer(
    val period: AgentPeriod,
    /** `expense` or `income` — which side of the ledger this breakdown is about. */
    val nature: String,
    val total: AgentFigure,
    val categories: List<AgentCategory>,
    /** The line for money with no category, present only when there was any. */
    val uncategorized: AgentCategory? = null,
    val perimeter: AgentPerimeter,
)

/** What `get_spending_breakdown` answers: both sides of a month at once, and the net between them. */
@Serializable
internal data class AgentSpendingBreakdownAnswer(
    val period: AgentPeriod,
    val breakdowns: List<AgentCategoryBreakdownAnswer>,
    /** Income less expense over the same period, when both sides were asked for. */
    val net: AgentFigure? = null,
    val perimeter: AgentPerimeter,
)

/** What `get_budget_progress` answers. */
@Serializable
internal data class AgentBudgetProgressAnswer(
    val period: AgentPeriod,
    val budgets: List<AgentBudget>,
    val perimeter: AgentPerimeter,
)

/** What `get_pending_recurring` answers: the cycles still waiting, and what confirming them costs. */
@Serializable
internal data class AgentPendingRecurringAnswer(
    val period: AgentPeriod,
    val pending: List<AgentRecurring>,
    /**
     * What the pending cycles would post if every one of them were confirmed. It is money that has
     * **not moved**: nothing is in the ledger until a cycle is confirmed, and reporting this as
     * spending is the one mistake a template invites.
     */
    @SerialName("expected_total")
    val expectedTotal: AgentFigure,
    val perimeter: AgentPerimeter,
)

/** What `get_card_overview` answers: each card's limit, and the invoice standing open on it. */
@Serializable
internal data class AgentCardOverviewAnswer(
    val cards: List<AgentCardOverview>,
    val perimeter: AgentPerimeter,
)

/**
 * One card, its limit and the invoice currently open on it.
 *
 * What holds the limit arrives **split by the cycle holding it**, because a single total cannot
 * say whether money is due now or merely committed: [closedTotal] is what is waiting to be paid,
 * [futureTotal] is what an instalment already committed to cycles that have not opened, and
 * [openTotal] is what the current cycle has accrued. [committedTotal] is exactly their sum, and is
 * the same figure as `card.used`.
 */
@Serializable
internal data class AgentCardOverview(
    val card: AgentCard,
    /** The invoice open right now, or `null` when none is. */
    @SerialName("open_invoice")
    val openInvoice: AgentInvoice? = null,
    /**
     * What the open cycle holds of the limit. It is [openInvoice]`.owed` floored at zero: an
     * over-paid cycle is owed *to* the user and holds no limit, so it reads `0` here while the
     * invoice keeps showing the credit.
     */
    @SerialName("open_total")
    val openTotal: AgentFigure? = null,
    /** What the cycles already closed and not yet paid owe — the part that is actually due. */
    @SerialName("closed_total")
    val closedTotal: AgentFigure? = null,
    /** What is committed to cycles that have not opened yet — what an instalment plan holds. */
    @SerialName("future_total")
    val futureTotal: AgentFigure? = null,
    /** The three above added up: everything holding this card's limit. */
    @SerialName("committed_total")
    val committedTotal: AgentFigure? = null,
    /** The fraction of the limit in use, between `0` and `1`, or `null` when the card has no limit. */
    @SerialName("limit_usage")
    val limitUsage: Double? = null,
)

/** What `get_report_stats` answers over an arbitrary range and a chosen perimeter. */
@Serializable
internal data class AgentReportStatsAnswer(
    val period: AgentPeriod,
    /** What the figures are seen from: `accounts` or `card`. */
    val scope: String,
    /** The names of the accounts, or of the card, the scope resolved to. */
    @SerialName("scope_names")
    val scopeNames: List<String>,
    @SerialName("opening_balance")
    val openingBalance: AgentFigure,
    val income: AgentFigure,
    val expense: AgentFigure,
    val balance: AgentFigure,
    val perimeter: AgentPerimeter,
)
