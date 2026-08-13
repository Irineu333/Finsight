package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.displaySign
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.YearMonth

/**
 * Per-subject totals from the ledger: the amount is `Σ entries` of the month's nominal
 * legs, grouped by the dimension they carry, with the group carrying none being the
 * unclassified total. One read answers the whole breakdown, and the unclassified total
 * is a group of that same aggregate rather than a read of its own.
 *
 * Translating the ledger's vocabulary into the facade's is this use case's job and
 * nobody else's: a dimension resolves to its category, and the absence of one is
 * [SpendingSubject.Uncategorized] — not a category, not an account, not a bucket.
 *
 * A dimension that resolves to no category at all is **dropped**, not folded into the
 * unclassified total: that is an integrity failure, and washing it into a legitimate
 * bucket would hide exactly what one would want to see.
 *
 * Sign, ordering, scale, the discarding of zeros and the share belong to
 * [spendingBreakdown], which owns them for every breakdown in the app.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    nominalType: AccountType,
    forYearMonth: YearMonth,
    entryRepository: IEntryRepository,
    consolidateMoney: ConsolidateMoneyUseCase,
): List<CategorySpending> {
    val categoriesByDimension = categories.associateBy { it.dimensionId }

    val totals = entryRepository
        .totalsByDimensionInMonthByCurrency(forYearMonth, nominalType)
        .mapNotNull { (dimensionId, natural) ->
            val subject = when (dimensionId) {
                null -> SpendingSubject.Uncategorized
                else -> categoriesByDimension[dimensionId]
                    ?.let(SpendingSubject::Categorized)
                    ?: return@mapNotNull null
            }
            subject to natural
        }
        .toMap()

    return consolidateMoney.spendingBreakdown(
        totals = totals,
        displaySign = nominalType.displaySign,
        // The month's rates are the ones that apply: a figure about March consolidates
        // at March's rates, or the past would move whenever a rate changed.
        on = forYearMonth.safeOnDay(forYearMonth.numberOfDays),
    )
}

class CalculateCategorySpendingUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : CalculateCategorySpendingUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            // Include closed: a category archived mid-month keeps the spending it made
            // that month; the ledger aggregate counts it, so the breakdown must too.
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isExpense },
            nominalType = AccountType.EXPENSE,
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
            consolidateMoney = consolidateMoney,
        )
}

class CalculateCategoryIncomeUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : CalculateCategoryIncomeUseCase {
    override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> =
        categoryTotals(
            categories = categoryRepository.getAllCategoriesIncludingClosed().filter { it.type.isIncome },
            nominalType = AccountType.INCOME,
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
            consolidateMoney = consolidateMoney,
        )
}
