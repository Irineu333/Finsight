package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.datetime.LocalDate

class CalculateReportCategorySpendingUseCase(
    private val entryRepository: IEntryRepository,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) {
    suspend operator fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
        transactionType: Transaction.Type = Transaction.Type.EXPENSE,
    ): List<CategorySpending> {
        val categoryType = if (transactionType.isIncome) AccountType.INCOME else AccountType.EXPENSE
        // INCOME accounts are credit-natured (negative); flip to read positive.
        val displaySign = if (transactionType.isIncome) -1.0 else 1.0

        // The perspective is expressed as the sibling legs an operation must have:
        // its asset accounts (all, when none selected) or the card's ledger account.
        val siblingAccountIds = when (perspective) {
            is ReportPerspective.AccountPerspective ->
                perspective.accountIds.ifEmpty { accountRepository.getAllAccounts().map { it.id } }
            is ReportPerspective.CreditCardPerspective ->
                listOfNotNull(creditCardRepository.getCreditCardById(perspective.creditCardId)?.accountId)
        }
        if (siblingAccountIds.isEmpty()) return emptyList()

        val totals = entryRepository.categoryTotals(categoryType, startDate, endDate, siblingAccountIds)
        val categoriesByAccount = categoryRepository.getAllCategories()
            .mapNotNull { category -> category.accountId?.let { it to category } }
            .toMap()

        val amounts = totals.mapNotNull { (accountId, natural) ->
            val category = categoriesByAccount[accountId] ?: return@mapNotNull null
            val amount = natural * displaySign
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
}
