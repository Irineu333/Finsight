package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpTool
import com.neoutils.finsight.mcp.McpToolEffect
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCategory
import com.neoutils.finsight.mcp.surface.AgentCategoryListAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * **Which categories exist, and what moved under each of them in a month.**
 *
 * Its recorte, against `get_category_spending` and `get_spending_breakdown`: this is the
 * **catalogue**. Every category is here, including the ones nothing moved under — which is exactly
 * what a breakdown drops, and exactly what an agent needs to resolve "groceries" into the identifier
 * a posting is classified with. Nothing here is ranked and nothing carries a share of the month: a
 * share is a fact about a breakdown, and this is not one.
 *
 * The two natures are read in one query each, whole, and each category's figure is taken from that
 * — so the number beside a name is the same number the breakdown shows it under.
 */
internal class ListCategoriesTool(
    private val clock: Clock,
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : McpTool {

    override val name: String = McpToolName.LIST_CATEGORIES.wireName

    override val effect = McpToolEffect.READS

    override val description: String =
        "Every category the user has, with its identifier, its name, whether it is an expense or " +
            "an income category, and what moved under it in a month. " +
            "PERIMETER: this is the catalogue — categories with nothing under them this month are " +
            "here too, with a total of zero. It ranks nothing and reports no share of the month: " +
            "for the ranked breakdown, with the money that carries no category as a line of its " +
            "own, use get_spending_breakdown or get_category_spending. " +
            "A category is an analytic axis and not an account: it has no currency of its own, so " +
            "its total comes back decomposed per currency whenever its postings sat in more " +
            "than one."

    override val inputSchema = schema(
        "month" to text("The month the totals cover, as `2026-03`. Defaults to the month the app is in."),
        "type" to choice("Only categories of this kind.", TYPES.keys.toList()),
        "include_archived" to yesOrNo("Include categories the user has archived. Defaults to false."),
    )

    override suspend fun call(arguments: JsonObject?) = reading {
        val month = arguments.month("month", clock)
        val includeArchived = arguments.flag("include_archived", default = false)
        val type = arguments.oneOf("type", TYPES.keys.toList())?.let { TYPES.getValue(it) }

        val categories = if (includeArchived) {
            categoryRepository.getAllCategoriesIncludingClosed()
        } else {
            categoryRepository.getAllCategories()
        }
            .filter { type == null || it.type == type }
            // A catalogue is read by a person's eye and paged by nobody, but the order still has to
            // be the same twice: name, then the identity, which no two categories share.
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

        // One read per nature for the whole month, not one per category.
        val totals = Nature.entries.associateWith {
            entryRepository.totalsByDimensionInMonthByCurrency(month, it.nominalType)
        }

        answer(
            AgentCategoryListAnswer(
                period = AgentPeriod.of(month, clock.today()),
                categories = categories.map { category ->
                    AgentCategory(
                        id = category.id,
                        name = category.name,
                        type = category.type.name.lowercase(),
                        isArchived = category.isArchived,
                        total = consolidateMoney.agentFigure(
                            money = totals.getValue(category.nature)[category.dimensionId]
                                ?: MoneyByCurrency.zero,
                            on = month.lastDay,
                            // A category's figure reads its direction off the kind of category it
                            // is, never off a sign — the same policy the app's own breakdown uses.
                            policy = DisplayAmount::magnitude,
                        ),
                        // Deliberately absent: a share is a fact about a breakdown — it needs the
                        // whole, and the whole includes the money that carries no category, which
                        // this list has no line for.
                        share = null,
                    )
                },
                perimeter = AgentPerimeter(
                    covers = "Every category listed, with what was classified under it in $month.",
                    excludes = listOfNotNull(
                        "money that carries no category at all, which is not a category and has " +
                            "no row here",
                        "transfers, card payments and adjustments, which have no category to be " +
                            "classified by",
                        "archived categories".takeUnless { includeArchived },
                    ),
                    seeAlso = listOf(
                        McpToolName.GET_SPENDING_BREAKDOWN.wireName,
                        McpToolName.GET_CATEGORY_SPENDING.wireName,
                        McpToolName.GET_BUDGET_PROGRESS.wireName,
                    ),
                ),
            ),
        )
    }

    /** Which side of the ledger a category's postings land on — its own declaration, not a derivation. */
    private val Category.nature: Nature
        get() = if (type.isExpense) Nature.EXPENSE else Nature.INCOME

    private companion object {

        /** The kinds a category can be, spelled as the agent spells them. */
        val TYPES: Map<String, Category.Type> =
            Category.Type.entries.associateBy { it.name.lowercase() }
    }
}
