package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentBudget
import com.neoutils.finsight.mcp.surface.AgentBudgetListAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import kotlinx.serialization.json.JsonObject

/**
 * **The budgets as they were set up**: what each one allows, and which categories it watches.
 *
 * Its recorte, against `get_budget_progress`: this is the **catalogue** — the identities, the
 * limits and the categories, which is what an agent needs to name a budget or to know what one
 * covers. It says nothing about a month. What has been spent against a budget, what is left of it
 * and how far along it is are questions about a period, and that tool answers them.
 *
 * The limit carries no mark of approximation and never will: it is what the user typed, in the
 * currency they chose when they created the budget, and nothing re-denominates it — spending is
 * re-expressed against *it*, not the other way round.
 */
internal class ListBudgetsTool(
    private val budgetRepository: IBudgetRepository,
) : McpTool {

    override val name: String = McpToolName.LIST_BUDGETS.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Every budget the user set up, with its identifier, its title, the categories it " +
            "watches, and the limit it allows. " +
            "PERIMETER: this is the catalogue — how the budgets are configured, with no month in " +
            "it. For what has been spent against each one, what is left and how far along it is, " +
            "use get_budget_progress. " +
            "A limit is denominated in the currency the budget was created with and is never " +
            "re-expressed; a `percentage` limit is a share of the recurring income behind it, and " +
            "what it resolves to in a given month is get_budget_progress's answer, not this one's."

    override val inputSchema = schema()

    override suspend fun call(arguments: JsonObject?) = reading {
        val budgets = budgetRepository.getAllBudgets()
            // Title, then the identity no two budgets share: the same order in every call.
            .sortedWith(compareBy({ it.title.lowercase() }, { it.id }))

        answer(
            AgentBudgetListAnswer(
                budgets = budgets.map { budget ->
                    AgentBudget(
                        id = budget.id,
                        title = budget.title,
                        categories = budget.categories.map { it.name },
                        limit = AgentFigure.exact(budget.amount, budget.currency),
                        limitType = budget.limitType.name.lowercase(),
                        percentage = budget.percentage,
                    )
                },
                perimeter = AgentPerimeter(
                    covers = "Every budget the user set up, as it is configured.",
                    excludes = listOf(
                        "what has been spent against any of them — that is a question about a " +
                            "month, and get_budget_progress answers it",
                        "what a `percentage` limit comes to in a given month, which depends on " +
                            "what the recurring income behind it was confirmed at",
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_BUDGET_PROGRESS.wireName,
                        McpToolName.LIST_CATEGORIES.wireName,
                    ),
                ),
            ),
        )
    }
}
