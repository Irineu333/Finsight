package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.displaySign
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Per-category totals from the ledger: the amount is `Σ entries` carrying the
 * category's dimension in the month, converted to the ledger's display sign so that
 * both an expense and an income category read as a positive figure. A category with
 * no movement contributes nothing.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    forYearMonth: YearMonth,
    base: String,
    today: LocalDate,
    entryRepository: IEntryRepository,
    consolidateFigure: ConsolidateFigureUseCase,
): List<CategorySpending> {
    // A category is a dimension, not an account: its entries may be denominated in several
    // currencies, so the ledger answers per currency and the consolidation layer is what
    // turns each answer into the one figure the breakdown ranks and renders.
    val date = consolidationDateOf(forYearMonth, today)
    val displaySignOf = { category: Category -> category.type.accountType.displaySign }
    val amounts = categories.mapNotNull { category ->
        val natural = entryRepository.dimensionBalanceInMonth(forYearMonth, category.dimensionId)
        if (natural.isEmpty) return@mapNotNull null
        // The ledger's natural balance is debit-positive; the display sign is what turns both
        // an expense and an income category into a figure that reads positive. Applying it
        // per currency keeps it presentation of each number rather than arithmetic over them.
        val signed = CurrencyBalance.of(
            natural.entries.mapValues { (_, amount) -> amount * displaySignOf(category) }
        )
        val figure = consolidateFigure(
            balance = signed,
            base = base,
            date = date,
            policy = DisplayAmount.SignPolicy.NATURAL,
        )
        if (figure.comparable == 0.0 && !figure.isPartial) null else category to figure
    }
    val total = amounts.sumOf { it.second.comparable }
    return amounts
        .map { (category, figure) ->
            CategorySpending(
                category = category,
                amount = figure,
                percentage = if (total > 0) (figure.comparable / total) * 100 else 0.0,
            )
        }
        .sortedByDescending { it.amount.comparable }
}

class CalculateCategorySpendingUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateFigure: ConsolidateFigureUseCase,
) : CalculateCategorySpendingUseCase {
    override suspend fun invoke(
        forYearMonth: YearMonth,
        base: String,
        today: LocalDate,
    ): List<CategorySpending> =
        categoryTotals(
            // Include closed: a category archived mid-month keeps the spending it made
            // that month; the ledger aggregate counts it, so the breakdown must too.
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isExpense },
            forYearMonth = forYearMonth,
            base = base,
            today = today,
            entryRepository = entryRepository,
            consolidateFigure = consolidateFigure,
        )
}

class CalculateCategoryIncomeUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateFigure: ConsolidateFigureUseCase,
) : CalculateCategoryIncomeUseCase {
    override suspend fun invoke(
        forYearMonth: YearMonth,
        base: String,
        today: LocalDate,
    ): List<CategorySpending> =
        categoryTotals(
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isIncome },
            forYearMonth = forYearMonth,
            base = base,
            today = today,
            entryRepository = entryRepository,
            consolidateFigure = consolidateFigure,
        )
}
