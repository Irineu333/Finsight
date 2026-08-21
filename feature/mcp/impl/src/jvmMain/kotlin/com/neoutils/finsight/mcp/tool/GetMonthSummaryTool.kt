package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentChange
import com.neoutils.finsight.mcp.surface.AgentComparison
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentMonthSummaryAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **What came in and what went out in a month**, with the comparison to another month already taken.
 *
 * Three things this answers that a naive reading of a transaction list gets wrong, and each of them
 * is the ledger's doing rather than this tool's:
 *
 * - **A transfer between the user's own accounts is not spending**, and neither is paying an
 *   invoice. Both legs sit inside the perimeter, so the aggregate excludes them — nothing here
 *   filters anything, and a tool that had to would be reading from the wrong place.
 * - **Spending on a card and spending from an account are different money.** A card purchase has no
 *   account leg at all, which is why the two totals can be added without double counting, and why
 *   both are reported beside the total rather than hidden inside it.
 * - **A month in progress is not a smaller month.** The period says whether it has finished and
 *   through which day it is measured, and a comparison names which of the two sides is the
 *   unfinished one.
 */
internal class GetMonthSummaryTool(
    private val clock: Clock,
    private val entryRepository: IEntryRepository,
    private val categoryRepository: ICategoryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_MONTH_SUMMARY.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Income, spending, adjustments and the opening and closing position of one month. " +
            "PERIMETER: transfers between the user's own accounts and credit-card payments are " +
            "NOT spending and are not in the totals — the ledger already leaves them out. " +
            "`expense` is the whole month's spending; `expense_from_accounts` and " +
            "`expense_on_cards` are its two halves, which never overlap. " +
            "`opening_net` and `closing_net` are the NET position — the accounts less what is " +
            "owed on the cards, the same figure get_net_worth answers — and not the account " +
            "balance get_balance answers. " +
            "`compare_to` takes a second month and returns the differences already calculated, " +
            "marking which of the two months had not finished yet."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
        "compare_to" to text(
            "A second month to compare against, as `2026-02`. The answer then carries the " +
                "difference for each figure and says which side is still in progress.",
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val comparedMonth = arguments.monthOrNull("compare_to")
        val today = clock.today()
        val on = month.lastDay

        val figures = read(month)
        val period = AgentPeriod.of(month, today)

        answer(
            AgentMonthSummaryAnswer(
                period = period,
                openingNet = figure(figures.opening, on, DisplayAmount::natural),
                income = figure(figures.income, on, DisplayAmount::magnitude),
                yields = figure(figures.yields, on, DisplayAmount::magnitude),
                expense = figure(figures.expense, on, DisplayAmount::magnitude),
                expenseFromAccounts = figure(figures.expenseFromAccounts, on, DisplayAmount::magnitude),
                expenseOnCards = figure(figures.expenseOnCards, on, DisplayAmount::magnitude),
                invoicePayments = figure(figures.invoicePayments, on, DisplayAmount::magnitude),
                adjustment = figure(figures.adjustment, on, DisplayAmount::explicitSign),
                closingNet = figure(figures.closing, on, DisplayAmount::natural),
                perimeter = perimeter(month),
                comparedTo = comparedMonth?.let { compare(figures, period, it, today) },
            )
        )
    }

    // ------------------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------------------

    private suspend fun read(month: YearMonth): MonthMoney {
        // Resolving the yield category into a dimension is this surface's job, exactly as it is the
        // transactions screen's: the ledger takes an identity and never learns what it names.
        val yieldDimensionId = categoryRepository
            .getCategoryBySystemKey(SystemCategoryKey.YIELD)
            ?.dimensionId

        val asset = entryRepository.assetMonthFlowsByCurrency(month, yieldDimensionId)
        val liability = entryRepository.liabilityMonthFlowsByCurrency(month)
        val previous = month.minusMonth()

        return MonthMoney(
            opening = entryRepository.netBalanceUpTo(previous),
            income = asset.income,
            yields = asset.yield,
            // Disjoint sets — a card purchase has no account leg — so adding them cannot
            // double-count. `MoneyByCurrency.plus` is the one implementation of that addition.
            expense = asset.expense + liability.expense,
            expenseFromAccounts = asset.expense,
            expenseOnCards = liability.expense,
            invoicePayments = liability.payment,
            adjustment = asset.adjustment + liability.adjustment,
            closing = entryRepository.netBalanceUpTo(month),
        )
    }

    /** Accounts and cards together, which is a sum because liabilities are stored in credit. */
    private suspend fun IEntryRepository.netBalanceUpTo(month: YearMonth): MoneyByCurrency =
        naturalBalanceUpToByCurrency(month, AccountType.ASSET) +
            naturalBalanceUpToByCurrency(month, AccountType.LIABILITY)

    private suspend fun figure(
        money: MoneyByCurrency,
        on: LocalDate,
        policy: (Double, String, Boolean) -> DisplayAmount,
    ): AgentFigure = consolidateMoney.agentFigure(money = money, on = on, policy = policy)

    // ------------------------------------------------------------------------------
    // The comparison
    // ------------------------------------------------------------------------------

    private suspend fun compare(
        current: MonthMoney,
        currentPeriod: AgentPeriod,
        comparedMonth: YearMonth,
        today: LocalDate,
    ): AgentComparison {
        val compared = read(comparedMonth)
        val comparedPeriod = AgentPeriod.of(comparedMonth, today)
        // The differences are taken at the *current* month's rates, so that the two sides and the
        // difference between them are all expressed against one date. Two dates would let a figure
        // move because a rate did.
        val on = currentPeriod.to

        return AgentComparison(
            period = comparedPeriod,
            incompleteSide = when {
                currentPeriod.isInProgress && comparedPeriod.isInProgress -> "both"
                currentPeriod.isInProgress -> "this_period"
                comparedPeriod.isInProgress -> "compared_period"
                else -> null
            },
            changes = COMPARED_FIGURES.map { (label, of) ->
                change(label, of(current), of(compared), on)
            },
        )
    }

    private suspend fun change(
        label: String,
        current: MoneyByCurrency,
        compared: MoneyByCurrency,
        on: LocalDate,
    ): AgentChange {
        // One scale for both sides, from the single owner of "put these figures on one scale".
        // Taking the percentage off anything else would use a rate this app did not choose.
        val scale = consolidateMoney.comparativeMagnitudes(
            figures = mapOf(CURRENT to current, COMPARED to compared),
            on = on,
        )
        val before = scale.magnitudeOf(COMPARED)
        val after = scale.magnitudeOf(CURRENT)

        return AgentChange(
            figure = label,
            current = figure(current, on, DisplayAmount::magnitude),
            compared = figure(compared, on, DisplayAmount::magnitude),
            difference = figure(current - compared, on, DisplayAmount::explicitSign),
            // `null`, never `0`: a zero here claims nothing moved, and "there is no answer" is a
            // different statement. There is none when the earlier figure was zero, or when no rate
            // could place the two on one scale.
            percentChange = if (before == null || after == null || before == 0.0) {
                null
            } else {
                (after - before) / before * PERCENT
            },
        )
    }

    private fun perimeter(month: YearMonth) = AgentPerimeter(
        covers = "Every posting dated within $month, classified by what it moved: money into the " +
            "user's accounts, money out of them, and money spent on their cards. " +
            "`opening_net` and `closing_net` are the accounts less what is owed on the cards.",
        excludes = listOf(
            "transfers between the user's own accounts — money that moved sideways, not out",
            "credit-card payments, which settle a debt already counted when it was spent " +
                "(reported separately as `invoice_payments`, outside every total)",
            "recurring templates and instalment plans not yet posted",
        ),
        seeAlso = listOf(
            McpToolName.GET_SPENDING_BREAKDOWN.wireName,
            McpToolName.GET_NET_WORTH.wireName,
            McpToolName.GET_BALANCE.wireName,
        ),
    )

    /** One figure of the summary, and where to read it off a month. */
    private class MonthMoney(
        val opening: MoneyByCurrency,
        val income: MoneyByCurrency,
        val yields: MoneyByCurrency,
        val expense: MoneyByCurrency,
        val expenseFromAccounts: MoneyByCurrency,
        val expenseOnCards: MoneyByCurrency,
        val invoicePayments: MoneyByCurrency,
        val adjustment: MoneyByCurrency,
        val closing: MoneyByCurrency,
    )

    private companion object {

        const val CURRENT = "current"
        const val COMPARED = "compared"
        const val PERCENT = 100.0

        /** What a comparison reports on, named exactly as the answer names it. */
        val COMPARED_FIGURES: List<Pair<String, (MonthMoney) -> MoneyByCurrency>> = listOf(
            "income" to { it.income },
            "expense" to { it.expense },
            "expense_from_accounts" to { it.expenseFromAccounts },
            "expense_on_cards" to { it.expenseOnCards },
            "adjustment" to { it.adjustment },
        )
    }
}
