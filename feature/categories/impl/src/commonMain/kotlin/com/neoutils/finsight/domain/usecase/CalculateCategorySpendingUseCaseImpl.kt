package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.displaySign
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.YearMonth

/**
 * Per-category totals from the ledger: the amount is `Σ entries` carrying the
 * category's dimension in the month, converted to the ledger's display sign so that
 * both an expense and an income category read as a positive figure. A category with
 * no movement contributes nothing.
 *
 * A category is a dimension and not an account, so it has no currency of its own and
 * its entries may sit in several (design D13): the line shows a **consolidated
 * figure**, and the reducer is the only thing that denominates one. The ledger still
 * answers a single scalar here — swapping that source for a per-currency read is
 * group 10 — but the base currency reaches the screen through the reducer's mouth and
 * nowhere else (design D29).
 *
 * Ordering and the percentage are settled on the numbers, before consolidation: a
 * figure of several terms has no single magnitude to sort or divide by.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    forYearMonth: YearMonth,
    entryRepository: IEntryRepository,
    baseCurrencyRepository: IBaseCurrencyRepository,
    consolidateMoney: ConsolidateMoneyUseCase,
): List<CategorySpending> {
    val amounts = categories.mapNotNull { category ->
        val natural = entryRepository.dimensionBalanceInMonth(forYearMonth, category.dimensionId)
        val amount = natural * category.type.accountType.displaySign
        if (amount == 0.0) null else category to amount
    }
    val total = amounts.sumOf { it.second }
    val base = baseCurrencyRepository.observe().value
    // The month's rates are the ones that apply: a figure about March consolidates at
    // March's rates, or the past would move whenever a rate changed.
    val on = forYearMonth.safeOnDay(forYearMonth.numberOfDays)
    return amounts
        .sortedByDescending { it.second }
        .map { (category, amount) ->
            CategorySpending(
                category = category,
                // The display sign above already turns both an expense and an income
                // category into a positive figure; the line reads its direction off
                // its own section's title.
                amount = consolidateMoney(
                    money = MoneyByCurrency.of(base, amount),
                    on = on,
                    policy = DisplayAmount::magnitude,
                ),
                percentage = if (total > 0) (amount / total) * 100 else 0.0,
            )
        }
}

class CalculateCategorySpendingUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : CalculateCategorySpendingUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            // Include closed: a category archived mid-month keeps the spending it made
            // that month; the ledger aggregate counts it, so the breakdown must too.
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isExpense },
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
            baseCurrencyRepository = baseCurrencyRepository,
            consolidateMoney = consolidateMoney,
        )
}

class CalculateCategoryIncomeUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : CalculateCategoryIncomeUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isIncome },
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
            baseCurrencyRepository = baseCurrencyRepository,
            consolidateMoney = consolidateMoney,
        )
}
