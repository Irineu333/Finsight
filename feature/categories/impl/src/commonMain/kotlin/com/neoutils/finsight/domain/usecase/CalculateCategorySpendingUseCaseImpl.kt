package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.YearMonth

/**
 * Per-category totals from the ledger: the amount is `Σ entries` of the category's
 * chart-of-accounts row in the month. [displaySign] converts the natural balance to
 * a positive figure (EXPENSE accounts are debit-natured → +spent; INCOME accounts
 * are credit-natured → negate to read +received). Categories with no ledger account
 * yet (never posted to) contribute nothing.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    forYearMonth: YearMonth,
    entryRepository: IEntryRepository,
    displaySign: Double,
): List<CategorySpending> {
    val amounts = categories.mapNotNull { category ->
        val accountId = category.accountId ?: return@mapNotNull null
        val amount = entryRepository.balanceInMonth(forYearMonth, accountId) * displaySign
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
            displaySign = 1.0,
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
            displaySign = -1.0,
        )
}
