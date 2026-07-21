package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.extension.displaySign
import kotlinx.datetime.LocalDate

class CalculateReportCategorySpendingUseCase(
    private val entryRepository: IEntryRepository,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) {
    /** Account-perspective report: category totals in a date range, scoped by the perspective's legs. */
    suspend operator fun invoke(
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
        transactionType: TransactionType = TransactionType.EXPENSE,
    ): List<CategorySpending> {
        val categoryType = accountType(transactionType)
        // The perspective is expressed as the sibling legs a transaction must have:
        // its asset accounts (all, when none selected) or the card's ledger account.
        val siblingAccountIds = when (perspective) {
            is ReportPerspective.AccountPerspective ->
                perspective.accountIds.ifEmpty { accountRepository.getAllAccounts().map { it.id } }
            is ReportPerspective.CreditCardPerspective ->
                listOfNotNull(creditCardRepository.getCreditCardById(perspective.creditCardId)?.accountId)
        }
        if (siblingAccountIds.isEmpty()) return emptyList()

        return build(
            totals = entryRepository.categoryTotals(categoryType, startDate, endDate, siblingAccountIds),
            transactionType = transactionType,
        )
    }

    /** Invoice-scoped report: category totals across a set of card invoices. */
    suspend fun forInvoices(
        invoiceIds: List<Long>,
        transactionType: TransactionType = TransactionType.EXPENSE,
    ): List<CategorySpending> {
        if (invoiceIds.isEmpty()) return emptyList()
        return build(
            totals = entryRepository.categoryTotalsForInvoices(accountType(transactionType), invoiceIds),
            transactionType = transactionType,
        )
    }

    private fun accountType(transactionType: TransactionType) =
        if (transactionType.isIncome) AccountType.INCOME else AccountType.EXPENSE

    private suspend fun build(
        totals: Map<Long, Double>,
        transactionType: TransactionType,
    ): List<CategorySpending> {
        val displaySign = accountType(transactionType).displaySign
        val categoriesByAccount: Map<Long, Category> = categoryRepository.getAllCategories()
            .associateBy { it.accountId }

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
