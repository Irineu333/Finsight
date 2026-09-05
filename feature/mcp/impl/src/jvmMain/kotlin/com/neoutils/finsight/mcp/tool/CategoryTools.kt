package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.AgentSpendingBreakdownAnswer
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **Where a month's spending went**, category by category, ranked, each with its share of the month.
 *
 * The unclassified money is a line of the same total rather than a remainder — see
 * [categoryBreakdown], which the three category tools share so that none of them can rank or
 * apportion differently from the app's own screens.
 */
internal class GetCategorySpendingTool(
    private val clock: Clock,
    private val calculateCategorySpending: CalculateCategorySpendingUseCase,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_CATEGORY_SPENDING.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "What the user spent in a month, broken down by category, ranked, each with its share " +
            "of the month's spending. " +
            "PERIMETER: all spending, whether it was paid from an account or put on a card. " +
            "Transfers between the user's own accounts and credit-card payments are not " +
            "spending and are not here. " +
            "Money that carries no category comes back as a separate `uncategorized` line, and " +
            "its share is counted in the same whole — the shares add up to the month, not to the " +
            "classified part of it. " +
            "For the income side call get_category_income; for both sides at once, " +
            "get_spending_breakdown."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        answer(
            categoryBreakdown(
                month = month,
                today = clock.today(),
                nature = Nature.EXPENSE,
                lines = calculateCategorySpending(month),
                entryRepository = entryRepository,
                consolidateMoney = consolidateMoney,
            ).answer
        )
    }
}

/** The same question asked of the other side of the ledger. */
internal class GetCategoryIncomeTool(
    private val clock: Clock,
    private val calculateCategoryIncome: CalculateCategoryIncomeUseCase,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_CATEGORY_INCOME.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "What the user received in a month, broken down by category, ranked, each with its " +
            "share of the month's income. " +
            "PERIMETER: money coming in from outside. Transfers between the user's own accounts " +
            "bring in nothing new and are not here. " +
            "Income that carries no category comes back as a separate `uncategorized` line, " +
            "counted in the same whole. " +
            "For the spending side call get_category_spending; for both at once, " +
            "get_spending_breakdown."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        answer(
            categoryBreakdown(
                month = month,
                today = clock.today(),
                nature = Nature.INCOME,
                lines = calculateCategoryIncome(month),
                entryRepository = entryRepository,
                consolidateMoney = consolidateMoney,
            ).answer
        )
    }
}

/**
 * **Both sides of a month in one call**, and the net between them.
 *
 * Its recorte, against the two tools above: they each answer one nature, this one answers the pair
 * — and only this one can state the **net**, because a net is a fact about the two together and
 * cannot be taken from either answer alone. Give it `nature` and it narrows to exactly what the
 * matching tool returns; leave it out and the round trip that would otherwise be two calls, plus a
 * subtraction across currencies that nothing would have checked, is one.
 */
internal class GetSpendingBreakdownTool(
    private val clock: Clock,
    private val calculateCategorySpending: CalculateCategorySpendingUseCase,
    private val calculateCategoryIncome: CalculateCategoryIncomeUseCase,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_SPENDING_BREAKDOWN.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "A month broken down by category on BOTH sides at once — what came in and what went " +
            "out — plus the net between them, already calculated. " +
            "This is the one tool that can state the net: it is a fact about the two sides " +
            "together and cannot be taken from either breakdown alone. " +
            "Give `nature` to narrow it to one side, which then returns exactly what " +
            "get_category_spending or get_category_income returns. " +
            "PERIMETER: all spending, from accounts and on cards alike; transfers between the " +
            "user's own accounts and credit-card payments are in neither side. Money with no " +
            "category is a line of its own on each side."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
        "nature" to choice(
            "Narrows the answer to one side of the ledger. Omit for both.",
            Nature.wireNames,
        ),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val today = clock.today()
        val asked = arguments.oneOf("nature", Nature.wireNames)?.let { listOf(Nature.of(it)) }
            ?: Nature.entries

        val breakdowns = asked.map { nature ->
            categoryBreakdown(
                month = month,
                today = today,
                nature = nature,
                lines = when (nature) {
                    Nature.EXPENSE -> calculateCategorySpending(month)
                    Nature.INCOME -> calculateCategoryIncome(month)
                },
                entryRepository = entryRepository,
                consolidateMoney = consolidateMoney,
            )
        }

        answer(
            AgentSpendingBreakdownAnswer(
                period = AgentPeriod.of(month, today),
                breakdowns = breakdowns.map { it.answer },
                net = if (asked.size < Nature.entries.size) {
                    // One side alone has no net, and taking one out of it would be inventing a
                    // number: `null` says there is none rather than claiming a zero.
                    null
                } else {
                    consolidateMoney.agentFigure(
                        money = netOf(breakdowns),
                        on = month.lastDay,
                        policy = DisplayAmount::explicitSign,
                    )
                },
                perimeter = AgentPerimeter(
                    covers = "Every posting of $month that carries a nature — money in and money " +
                        "out — grouped by category, with the two sides side by side.",
                    excludes = listOf(
                        "transfers between the user's own accounts",
                        "credit-card payments, which settle spending already counted",
                        "balance adjustments, which have no category",
                    ),
                    seeAlso = listOf(McpToolName.GET_MONTH_SUMMARY.wireName),
                ),
            )
        )
    }

    /**
     * What was left over: income less spending, per currency, over exactly the lines the two
     * breakdowns show.
     *
     * The totals arrive in the **ledger's own sign** — income is credit and sits negative, spending
     * is debit and sits positive — so what a person calls "income minus expense" is the negation of
     * their sum, and no sign rule of this surface's own takes part. Adding and subtracting per
     * currency has one owner, `MoneyByCurrency`, and this is it being used.
     */
    private fun netOf(breakdowns: List<AgentBreakdown>): MoneyByCurrency =
        MoneyByCurrency.zero - breakdowns.fold(MoneyByCurrency.zero) { sum, it -> sum + it.total }
}
