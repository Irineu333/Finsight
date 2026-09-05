package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentBudget
import com.neoutils.finsight.mcp.surface.AgentBudgetProgressAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **How far into each budget the month has got.**
 *
 * The composing is this tool's work and the deciding is not. `CalculateBudgetProgressUseCase` wants
 * three lists it does not fetch — the budgets, the recurring templates and the transactions — because
 * a percentage limit is a share of whatever the recurring behind it was confirmed at *in that month*.
 * Gathering them is adaptation; what a limit resolves to, what counts as spent against it, and what
 * happens when part of that spending sits in a currency no rate reaches are all the use case's.
 *
 * The last of those is why `spent` can arrive without a single number: a budget is measured against a
 * currency chosen once, and spending no rate can express in it stays a term of its own. The progress
 * is then `null` rather than a bar at zero — a floor shown as a total reads "you have spent less than
 * you have", which is the one direction a budget must never err in.
 */
internal class GetBudgetProgressTool(
    private val clock: Clock,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateBudgetProgress: CalculateBudgetProgressUseCase,
) : McpTool {

    override val name: String = McpToolName.GET_BUDGET_PROGRESS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Each budget the user set, what it allows, what has been spent against it this month, " +
            "what is left and how far along it is. " +
            "PERIMETER: only the expense categories a budget watches count towards it, and only " +
            "postings dated inside the month. A budget's limit is denominated in the currency it " +
            "was created with and never re-expressed. " +
            "`remaining` and `progress` stop at the limit, so `is_exceeded` and `exceeded_by` are " +
            "what tell a budget past its limit from one exactly at it. " +
            "`progress` and `is_exceeded` are absent — never zero, never false — when part of the " +
            "spending sits in a currency no stored rate can express in the limit's currency; " +
            "`spent` then says which currencies those are."

    override val inputSchema = schema(
        "month" to text("The month, as `2026-03`. Defaults to the month the app is in."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)

        val progress = calculateBudgetProgress(
            budgets = budgetRepository.getAllBudgets(),
            recurringList = recurringRepository.observeAllRecurring().first(),
            transactions = transactionRepository.getAllTransactions(),
            month = month,
        )

        answer(
            AgentBudgetProgressAnswer(
                period = AgentPeriod.of(month, clock.today()),
                budgets = progress.map { it.toAgentBudget() },
                perimeter = AgentPerimeter(
                    covers = "Spending in $month under the expense categories each budget " +
                        "watches, measured against the budget's own limit.",
                    excludes = listOf(
                        "income categories a budget happens to list, which are not spending",
                        "spending outside every budgeted category — a budget is not the month",
                    ),
                    seeAlso = listOf(McpToolName.GET_CATEGORY_SPENDING.wireName),
                ),
            )
        )
    }

    private fun BudgetProgress.toAgentBudget() = AgentBudget(
        id = budget.id,
        title = budget.title,
        categories = budget.categories.map { it.name },
        // The limit is what the user typed, in the currency they chose. It never carries a mark:
        // nothing converted it, and nothing ever re-denominates it.
        limit = limitAmount.agentFigure(),
        spent = spentFigure?.agentFigure(),
        remaining = remainingAmount?.agentFigure(),
        // The domain answers a `Float`, and widening one to a `Double` publishes the binary noise
        // it never meant — `0.6` arriving as `0.6000000238418579`. Going through the shortest
        // decimal that round-trips to that float invents no precision and rounds nothing away; it
        // is the number the float stands for, written out.
        progress = progress?.toString()?.toDouble(),
        // The fact and the figure the screens choose "exceeded by" over "remaining" by, published
        // from the same two properties. Both stay unstated while the spending is a floor: the
        // domain answers `false` for "not known to be exceeded", and passing that on as a `false`
        // would turn a refusal to say into a denial.
        isExceeded = isExceeded.takeIf { isResolved },
        exceededBy = exceededAmount?.takeIf { isExceeded }?.agentFigure(),
        limitType = budget.limitType.name.lowercase(),
        percentage = budget.percentage,
    )
}
