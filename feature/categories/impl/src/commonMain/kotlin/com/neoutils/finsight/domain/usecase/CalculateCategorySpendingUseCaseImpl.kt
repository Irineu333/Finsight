package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.accountType
import com.neoutils.finsight.extension.displaySign
import kotlinx.datetime.YearMonth

/**
 * Per-category totals from the ledger: the amount is `Σ entries` of the category's
 * chart-of-accounts row in the month, converted to the ledger's display sign so that
 * both an expense and an income category read as a positive figure. Categories with no
 * ledger account yet (never posted to) contribute nothing.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    forYearMonth: YearMonth,
    entryRepository: IEntryRepository,
): List<CategorySpending> {
    val amounts = categories.mapNotNull { category ->
        val accountId = category.accountId
        val natural = entryRepository.balanceInMonth(forYearMonth, accountId)
        val amount = natural * category.type.accountType.displaySign
        if (amount == 0.0) null else category to amount
    }
    val total = amounts.sumOf { it.second }
    return amounts
        .map { (category, amount) ->
            CategorySpending(
                category = category,
                amount = amount,
                percentage = if (total > 0) (amount / total) * 100 else 0.0,
            )
        }
        .sortedByDescending { it.amount }
}

class CalculateCategorySpendingUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
) : CalculateCategorySpendingUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            categories = categoryRepository.getAllCategories().filter { it.type.isExpense },
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
        )
}

class CalculateCategoryIncomeUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
) : CalculateCategoryIncomeUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            categories = categoryRepository.getAllCategories().filter { it.type.isIncome },
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
        )
}
