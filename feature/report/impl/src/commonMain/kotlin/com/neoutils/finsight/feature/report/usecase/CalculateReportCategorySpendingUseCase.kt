package com.neoutils.finsight.feature.report.usecase

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.categories.model.CategorySpending
import com.neoutils.finsight.feature.report.model.ReportPerspective
import kotlinx.datetime.LocalDate

class CalculateReportCategorySpendingUseCase {
    operator fun invoke(
        operations: List<Operation>,
        categoriesById: Map<Long, Category>,
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
        transactionType: Transaction.Type = Transaction.Type.EXPENSE,
    ): List<CategorySpending> {
        val matchingTransactions = operations
            .filter { it.date in startDate..endDate }
            .flatMap { it.transactions }
            .filter { it.type == transactionType && it.categoryId != null && it.matchesPerspective(perspective) }

        val totalAmount = matchingTransactions.sumOf { it.amount }

        return matchingTransactions
            .groupBy { it.categoryId!! }
            .mapNotNull { (categoryId, txs) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                val amount = txs.sumOf { it.amount }
                CategorySpending(
                    category = category,
                    amount = amount,
                    percentage = when {
                        totalAmount > 0 -> (amount / totalAmount) * 100
                        else -> 0.0
                    },
                )
            }
            .sortedByDescending { it.amount }
    }
}

private fun Transaction.matchesPerspective(perspective: ReportPerspective): Boolean {
    return when (perspective) {
        is ReportPerspective.AccountPerspective -> {
            target == Transaction.Target.ACCOUNT &&
                (perspective.accountIds.isEmpty() || accountId in perspective.accountIds)
        }
        is ReportPerspective.CreditCardPerspective -> {
            target == Transaction.Target.CREDIT_CARD &&
                creditCardId == perspective.creditCardId
        }
    }
}
