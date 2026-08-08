package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.MoneyByCurrency
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
 * figure**, and the reducer is the only thing that denominates one. The base currency
 * reaches the screen through the reducer's mouth and nowhere else (design D29).
 *
 * **Ordering and the percentage are settled on a common scale the reducer builds**, from
 * the same rates and the same date as the figures on screen — so the ranking and the
 * numbers beside it can never disagree. A figure of several terms has no magnitude of
 * its own to sort or divide by, and a family of figures in different currencies has no
 * shared one until something says what the shared scale is. With a single currency in
 * play — whichever it is — no rate is read and both come out exactly as before.
 *
 * A category nothing could be priced against sorts last and shows **no** percentage. Not
 * `0%`: zero is an assertion, and a missing rate is the absence of an answer.
 */
internal suspend fun categoryTotals(
    categories: List<Category>,
    forYearMonth: YearMonth,
    entryRepository: IEntryRepository,
    consolidateMoney: ConsolidateMoneyUseCase,
): List<CategorySpending> {
    val amounts = categories.mapNotNull { category ->
        // Σ entries carrying the dimension in the month, per currency. The display sign
        // is applied term by term: it turns both an expense and an income category into
        // a positive figure, and it is a property of the nature, not of the currency.
        val natural = entryRepository.dimensionBalanceInMonthByCurrency(forYearMonth, category.dimensionId)
        val sign = category.type.accountType.displaySign
        val amount = MoneyByCurrency.of(natural.toList().associate { it.currency to it.value * sign })
        if (amount.isEmpty || amount.toList().all { it.value == 0.0 }) null else category to amount
    }

    // The month's rates are the ones that apply: a figure about March consolidates at
    // March's rates, or the past would move whenever a rate changed.
    val on = forYearMonth.safeOnDay(forYearMonth.numberOfDays)

    val scale = consolidateMoney.comparativeMagnitudes(
        figures = amounts.associate { it },
        on = on,
    )

    return amounts
        // A category with no magnitude cannot be ranked against the others, so it goes
        // last rather than being dropped or ordered by accident.
        .sortedByDescending { (category, _) -> scale.magnitudeOf(category) ?: Double.NEGATIVE_INFINITY }
        .map { (category, amount) ->
            CategorySpending(
                category = category,
                // The line reads its direction off its own section's title.
                amount = consolidateMoney(
                    money = amount,
                    on = on,
                    policy = DisplayAmount::magnitude,
                ),
                percentage = scale.shareOf(category)?.let { it * 100 },
            )
        }
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
            forYearMonth = forYearMonth,
            entryRepository = entryRepository,
            consolidateMoney = consolidateMoney,
        )
}
