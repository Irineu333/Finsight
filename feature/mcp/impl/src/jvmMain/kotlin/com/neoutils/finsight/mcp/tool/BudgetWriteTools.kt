package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CreateBudgetUseCase
import com.neoutils.finsight.domain.usecase.DeleteBudgetUseCase
import com.neoutils.finsight.domain.usecase.UpdateBudgetUseCase
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentBudget
import com.neoutils.finsight.mcp.surface.AgentBudgetWriteAnswer
import com.neoutils.finsight.mcp.surface.AgentFigure
import com.neoutils.finsight.mcp.surface.AgentRefusal
import com.neoutils.finsight.mcp.surface.AgentRemovalAnswer
import com.neoutils.finsight.util.AppIcon
import kotlinx.serialization.json.JsonObject

/** The two kinds of limit, spelled as the agent spells them. */
private val LIMIT_TYPES: Map<String, LimitType> = LimitType.entries.associateBy { it.name.lowercase() }

/** A budget as an agent receives it back from a write — no spending, because none was read. */
private fun Budget.asAgentBudget() = AgentBudget(
    id = id,
    title = title,
    categories = categories.map { it.name },
    limit = AgentFigure.exact(amount, currency),
    limitType = limitType.name.lowercase(),
    percentage = percentage,
)

/** The categories an argument names, each refused by name when it resolves to nothing. */
private suspend fun ICategoryRepository.require(categoryIds: List<Long>): List<Category> =
    categoryIds.map { require(it) }

// ----------------------------------------------------------------------------------
// create_budget
// ----------------------------------------------------------------------------------

/** **Creates a budget: a lens over the spending of some categories, with a limit to read it against.** */
internal class CreateBudgetTool(
    private val categoryRepository: ICategoryRepository,
    private val recurringRepository: IRecurringRepository,
    private val createBudget: CreateBudgetUseCase,
) : McpTool {

    override val name: String = McpToolName.CREATE_BUDGET.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Create a budget over one or more categories, with the limit to measure their spending " +
            "against. " +
            "A `fixed` limit is an amount; a `percentage` limit is a share of a recurring " +
            "income, which base_recurring_id names and which is required for that kind. " +
            "The currency is required and never changes: a category is a dimension with no " +
            "currency of its own, so the limit's denomination cannot be derived from what it " +
            "measures — spending is re-expressed against the limit, not the other way round. " +
            "PERIMETER: it configures the budget. What has been spent against it in a month is " +
            "get_budget_progress's answer, and this writes nothing about any month."

    override val inputSchema = schema(
        "title" to text("What the user calls it. Must not clash with a budget that already exists."),
        "category_ids" to numbers("The categories it watches, from list_categories."),
        "currency" to text("The ISO code the limit is denominated in, as `BRL`. Fixed from now on."),
        "limit_type" to choice("How the limit is stated.", LIMIT_TYPES.keys.toList()),
        "amount" to amount("The limit itself, for a `fixed` limit — 800.00, not 80000."),
        "percentage" to amount("The share of the base income, for a `percentage` limit — 30 for 30%."),
        "base_recurring_id" to number("The recurring income a `percentage` limit is a share of, from list_recurring."),
        required = listOf("title", "category_ids", "currency"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val title = arguments.requiredString("title")
        val currency = arguments.requiredString("currency").uppercase()
        val categories = categoryRepository.require(arguments.longs("category_ids").orEmpty())
        val limitType = arguments.oneOf("limit_type", LIMIT_TYPES.keys.toList())
            ?.let { LIMIT_TYPES.getValue(it) }
            ?: LimitType.FIXED
        val baseIncome = arguments.long("base_recurring_id")?.let {
            recurringRepository.getRecurringById(it)
                ?: return@writing refusedWith(
                    AgentRefusal.notFound("recurring", it),
                    summary = "budget $title",
                )
        }

        createBudget(
            title = title,
            categories = categories,
            iconKey = AppIcon.BUDGET.key,
            currency = currency,
            limitType = limitType,
            amount = arguments.money("amount") ?: 0.0,
            percentage = arguments.money("percentage"),
            baseIncome = baseIncome,
        ).reported(
            summary = "budget $title over ${categories.joinToString { it.name }}",
            payload = {
                AgentBudgetWriteAnswer(
                    budget = it.asAgentBudget(),
                    note = "Created. Ask get_budget_progress for what has been spent against it.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.BUDGET, it.id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// update_budget
// ----------------------------------------------------------------------------------

/** **Edits a budget — what it watches, what it is called and what it is measured against.** */
internal class UpdateBudgetTool(
    private val budgetRepository: IBudgetRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRepository: IRecurringRepository,
    private val updateBudget: UpdateBudgetUseCase,
) : McpTool {

    override val name: String = McpToolName.UPDATE_BUDGET.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Change a budget's title, the categories it watches, or the limit it measures them " +
            "against. What is not given keeps the value it already has. " +
            "PERIMETER: the currency of a limit is chosen once and cannot be changed — " +
            "re-denominating it would rewrite the meaning of a number the user typed, so " +
            "another currency means another budget."

    override val inputSchema = schema(
        "id" to number("The budget to edit, from list_budgets."),
        "title" to text("The new title."),
        "category_ids" to numbers("The categories it watches, replacing the current set."),
        "limit_type" to choice("How the limit is stated.", LIMIT_TYPES.keys.toList()),
        "amount" to amount("The limit itself, for a `fixed` limit — 800.00, not 80000."),
        "percentage" to amount("The share of the base income, for a `percentage` limit — 30 for 30%."),
        "base_recurring_id" to number("The recurring income a `percentage` limit is a share of."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = budgetRepository.getBudgetById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("budget", id),
                summary = "edit budget $id",
            )

        val categories = arguments.longs("category_ids")
            ?.let { categoryRepository.require(it) }
            ?: stored.categories
        val limitType = arguments.oneOf("limit_type", LIMIT_TYPES.keys.toList())
            ?.let { LIMIT_TYPES.getValue(it) }
            ?: stored.limitType
        val baseIncomeId = arguments.long("base_recurring_id") ?: stored.recurringId
        val baseIncome = baseIncomeId?.let {
            recurringRepository.getRecurringById(it)
                ?: return@writing refusedWith(
                    AgentRefusal.notFound("recurring", it),
                    summary = "budget ${stored.title}",
                )
        }
        val title = arguments.string("title") ?: stored.title
        val amount = arguments.money("amount") ?: stored.amount
        val percentage = arguments.money("percentage") ?: stored.percentage

        updateBudget(
            budgetId = id,
            title = title,
            categories = categories,
            // Kept as it is: the surface does not carry icons, and a default here would reset
            // one the user chose on the screen.
            iconKey = stored.iconKey,
            limitType = limitType,
            amount = amount,
            percentage = percentage,
            baseIncome = baseIncome,
        ).reported(
            summary = "budget ${stored.title}",
            payload = {
                AgentBudgetWriteAnswer(
                    budget = stored.copy(
                        title = title.trim(),
                        categories = categories,
                        limitType = limitType,
                        amount = amount,
                        percentage = percentage,
                    ).asAgentBudget(),
                    note = "Edited. Everything the call did not name kept the value it had.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.BUDGET, id) },
        )
    }
}

// ----------------------------------------------------------------------------------
// delete_budget
// ----------------------------------------------------------------------------------

/** **Removes a budget.** */
internal class DeleteBudgetTool(
    private val budgetRepository: IBudgetRepository,
    private val deleteBudget: DeleteBudgetUseCase,
) : McpTool {

    override val name: String = McpToolName.DELETE_BUDGET.wireName

    override val effect = McpToolEffect.CHANGES

    override val description: String =
        "Remove a budget for good. " +
            "PERIMETER: a budget owns no money and no posting — it is a lens over spending the " +
            "categories already classify — so removing it takes nothing with it and no spending " +
            "is affected. The categories it watched are untouched."

    override val inputSchema = schema(
        "id" to number("The budget to remove, from list_budgets."),
        required = listOf("id"),
    )

    override suspend fun call(arguments: JsonObject?) = writing {
        val id = arguments.requiredLong("id")

        val stored = budgetRepository.getBudgetById(id)
            ?: return@writing refusedWith(
                AgentRefusal.notFound("budget", id),
                summary = "delete budget $id",
            )

        deleteBudget(id).reported(
            summary = "budget ${stored.title}",
            payload = {
                AgentRemovalAnswer(
                    removed = "budget",
                    id = id,
                    name = stored.title,
                    note = "Removed. No posting and no category was touched: a budget is a lens " +
                        "over what the categories already classify.",
                )
            },
            reference = { reference(AgentActivity.Reference.Kind.BUDGET, id) },
        )
    }
}
