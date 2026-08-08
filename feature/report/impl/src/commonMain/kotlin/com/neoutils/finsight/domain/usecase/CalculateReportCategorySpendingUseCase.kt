package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.displaySign
import kotlinx.datetime.LocalDate

class CalculateReportCategorySpendingUseCase(
    private val entryRepository: IEntryRepository,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) {
    /** Account-perspective report: category totals in a date range, scoped by the perspective's legs. */
    suspend operator fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
        transactionType: TransactionType = TransactionType.EXPENSE,
    ): List<CategorySpending> {
        val nominalType = accountType(transactionType)
        // The perspective is expressed as the sibling legs a transaction must have:
        // its asset accounts (all, when none selected) or the card's ledger account.
        val siblingAccountIds = when (perspective) {
            is ReportPerspective.AccountPerspective ->
                // Include closed, mirroring the stats use case: the "all accounts"
                // fallback must not silently drop an archived account's spending.
                perspective.accountIds.ifEmpty { accountRepository.getAllAccountsIncludingClosed().map { it.id } }
            is ReportPerspective.CreditCardPerspective ->
                listOfNotNull(creditCardRepository.getCreditCardById(perspective.creditCardId)?.accountId)
        }
        if (siblingAccountIds.isEmpty()) return emptyList()

        return build(
            totals = entryRepository.totalsByDimensionByCurrency(nominalType, startDate, endDate, siblingAccountIds),
            transactionType = transactionType,
            on = endDate,
        )
    }

    /**
     * Sub-ledger-scoped report: category totals across a set of dimensions.
     *
     * [on] is the date whose rates consolidate the figures — the period the scope
     * covers ends there, and a report about March must not move when a rate changes in
     * April.
     */
    suspend fun forDimensions(
        dimensionIds: List<Long>,
        on: LocalDate,
        transactionType: TransactionType = TransactionType.EXPENSE,
    ): List<CategorySpending> {
        if (dimensionIds.isEmpty()) return emptyList()
        return build(
            totals = entryRepository.totalsByDimensionInScopeByCurrency(accountType(transactionType), dimensionIds),
            transactionType = transactionType,
            on = on,
        )
    }

    private fun accountType(transactionType: TransactionType) =
        if (transactionType.isIncome) AccountType.INCOME else AccountType.EXPENSE

    private suspend fun build(
        totals: Map<Long?, MoneyByCurrency>,
        transactionType: TransactionType,
        on: LocalDate,
    ): List<CategorySpending> {
        val displaySign = accountType(transactionType).displaySign
        // Include closed: the ledger totals above count an archived category's
        // spending, so resolving with the open-only list would drop it from the
        // breakdown AND inflate the survivors' percentages (the total below is a sum
        // over what resolves). Mirrors the sibling-set a few lines up.
        //
        // The unclassified group (`null` key) resolves to no category and drops out
        // here, exactly as the uncategorized bucket account used to.
        val categoriesByDimension: Map<Long, Category> = categoryRepository.getAllCategoriesIncludingClosed()
            .associateBy { it.dimensionId }

        val amounts = totals.mapNotNull { (dimensionId, natural) ->
            val category = categoriesByDimension[dimensionId] ?: return@mapNotNull null
            // The display sign is a property of the nature, not of the currency, so it
            // applies term by term.
            val amount = MoneyByCurrency.of(natural.toList().associate { it.currency to it.value * displaySign })
            if (amount.isEmpty || amount.toList().all { it.value == 0.0 }) null else category to amount
        }

        // A category is a dimension and not an account, so it has no currency of its
        // own and its entries may sit in several: what a breakdown line shows is a
        // consolidated figure, and the reducer is the only thing that denominates one
        // (design D9, D13). The base currency reaches the screen through the reducer's
        // mouth and nowhere else (D29).
        //
        // Ordering and the share come from the same reducer, on one common scale built
        // from the same rates and the same date as the figures — the report's ranking
        // and its numbers cannot disagree. With a single currency in play no rate is
        // read at all, and both are what they always were.
        val scale = consolidateMoney.comparativeMagnitudes(figures = amounts.associate { it }, on = on)

        return amounts
            .sortedByDescending { (category, _) -> scale.magnitudeOf(category) ?: Double.NEGATIVE_INFINITY }
            .map { (category, amount) ->
                CategorySpending(
                    category = category,
                    // The display sign above already turns both an expense and an
                    // income category into a positive figure; the line reads its
                    // direction off its own section's title.
                    amount = consolidateMoney(
                        money = amount,
                        on = on,
                        policy = DisplayAmount::magnitude,
                    ),
                    percentage = scale.shareOf(category)?.let { it * 100 },
                )
            }
    }
}
