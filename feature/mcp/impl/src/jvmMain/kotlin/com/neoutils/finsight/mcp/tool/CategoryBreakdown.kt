package com.neoutils.finsight.mcp.tool

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.mcp.McpToolName
import com.neoutils.finsight.mcp.surface.AgentCategory
import com.neoutils.finsight.mcp.surface.AgentCategoryBreakdownAnswer
import com.neoutils.finsight.mcp.surface.AgentPeriod
import com.neoutils.finsight.mcp.surface.AgentPerimeter
import com.neoutils.finsight.mcp.surface.agentFigure
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * One nature's breakdown of a month, translated for an agent.
 *
 * **Which categories are in it, in what order, and what share each holds is not decided here.** That
 * is `CalculateCategorySpendingUseCase` and, under it, the single breakdown builder in the
 * consolidation layer: the display sign, dropping what nets to zero, the one comparative scale, the
 * ranking and the share. Three tools consume the same answer, so none of them can rank differently
 * from the app's own screens.
 *
 * **What is added is the decomposition per currency**, which the domain's figure no longer carries:
 * once reduced, a figure's base term holds several currencies added together, and publishing that as
 * a decomposition would be publishing the very sum a decomposition exists to avoid. So the same
 * ledger aggregate is read once more, exact, and each line's figure is built from it — the same
 * number as the screen's, with its parts still visible.
 *
 * The **money with no category at all** is a line of this total, not a remainder: its share comes off
 * the same scale as everyone else's, which is what makes the shares add up to the month rather than
 * to the classified part of it.
 */
internal suspend fun categoryBreakdown(
    month: YearMonth,
    today: LocalDate,
    nature: Nature,
    lines: List<CategorySpending>,
    entryRepository: IEntryRepository,
    consolidateMoney: ConsolidateMoneyUseCase,
): AgentBreakdown {
    val exact = entryRepository.totalsByDimensionInMonthByCurrency(month, nature.nominalType)
    val on = month.lastDay

    suspend fun figureOf(subject: SpendingSubject) = consolidateMoney.agentFigure(
        money = exact.moneyOf(subject),
        on = on,
        // A breakdown line reads its direction off the section it is in, never off a sign — which
        // is the same policy the app's own breakdown uses.
        policy = DisplayAmount::magnitude,
    )

    val categories = lines.mapNotNull { line ->
        val category = (line.subject as? SpendingSubject.Categorized)?.category ?: return@mapNotNull null
        AgentCategory(
            id = category.id,
            name = category.name,
            type = category.type.name.lowercase(),
            isArchived = category.isArchived,
            total = figureOf(line.subject),
            share = line.percentage?.div(PERCENT),
        )
    }

    val uncategorized = lines
        .firstOrNull { it.subject is SpendingSubject.Uncategorized }
        ?.let { line ->
            AgentCategory(
                // Not a category: it has no row, nothing to rename, archive or delete. The zero is
                // the absence of an identity, and the name says so in words.
                id = 0,
                name = UNCATEGORIZED,
                type = nature.wireName,
                total = figureOf(line.subject),
                share = line.percentage?.div(PERCENT),
            )
        }

    // The total of exactly the lines the breakdown shows, in the ledger's own sign. Kept beside the
    // answer rather than recomputed by whoever combines two breakdowns: a second read would include
    // what these lines legitimately drop — what nets to zero, and a dimension resolving to no
    // category — and the net printed beside two totals would not be their difference.
    val total = lines.fold(MoneyByCurrency.zero) { running, line -> running + exact.moneyOf(line.subject) }

    return AgentBreakdown(
        total = total,
        answer = AgentCategoryBreakdownAnswer(
            period = AgentPeriod.of(month, today),
            nature = nature.wireName,
            total = consolidateMoney.agentFigure(
                money = total,
                on = on,
                policy = DisplayAmount::magnitude,
            ),
            categories = categories,
            uncategorized = uncategorized,
            perimeter = nature.perimeter(month),
        ),
    )
}

/**
 * One nature's breakdown, with the per-currency total it was built from kept beside it.
 *
 * The raw total is not redundant with [AgentCategoryBreakdownAnswer.total]: that one is reduced and
 * signed for reading, and two of them cannot be subtracted from each other. This is what the ledger
 * answered, which is what combining two breakdowns needs.
 */
internal class AgentBreakdown(
    val answer: AgentCategoryBreakdownAnswer,
    val total: MoneyByCurrency,
)

/** The exact per-currency total behind one line, as the ledger answered it. */
private fun Map<Long?, MoneyByCurrency>.moneyOf(subject: SpendingSubject): MoneyByCurrency = when (subject) {
    is SpendingSubject.Categorized -> this[subject.category.dimensionId]
    SpendingSubject.Uncategorized -> this[null]
} ?: MoneyByCurrency.zero

/** Which side of the ledger a breakdown is about, and everything that follows from it. */
internal enum class Nature(val wireName: String, val nominalType: AccountType) {

    EXPENSE("expense", AccountType.EXPENSE),
    INCOME("income", AccountType.INCOME);

    fun perimeter(month: YearMonth) = when (this) {
        EXPENSE -> AgentPerimeter(
            covers = "Everything spent in $month, whichever way it was paid — from an account or " +
                "on a card — grouped by the category it was classified under.",
            excludes = listOf(
                "transfers between the user's own accounts, which are not spending",
                "credit-card payments, which settle spending already counted when it happened",
                "balance adjustments, which have no category",
            ),
            seeAlso = listOf(
                McpToolName.GET_MONTH_SUMMARY.wireName,
                McpToolName.GET_BUDGET_PROGRESS.wireName,
            ),
        )

        INCOME -> AgentPerimeter(
            covers = "Everything received in $month, grouped by the category it was classified " +
                "under.",
            excludes = listOf(
                "transfers between the user's own accounts, which bring in nothing new",
                "balance adjustments, which have no category",
            ),
            seeAlso = listOf(McpToolName.GET_MONTH_SUMMARY.wireName),
        )
    }

    companion object {
        val wireNames: List<String> = entries.map { it.wireName }

        fun of(wireName: String): Nature = entries.first { it.wireName == wireName }
    }
}

private const val PERCENT = 100.0

private const val UNCATEGORIZED = "Uncategorized"
