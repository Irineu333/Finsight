package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

class CalculateReportCategorySpendingUseCase {
    operator fun invoke(
        operations: List<Operation>,
        perspective: ReportPerspective,
        startDate: LocalDate,
        endDate: LocalDate,
        transactionType: Transaction.Type = Transaction.Type.EXPENSE,
    ): List<CategorySpending> {
        val matchingTransactions = operations
            .filter { it.date in startDate..endDate }
            .flatMap { it.transactions }
            .filter { it.type == transactionType && it.category != null && it.matchesPerspective(perspective) }

        val totalAmount = matchingTransactions.sumOf { it.amount }

        return matchingTransactions
            .groupBy { it.category!! }
            .map { (category, transactions) ->
                val amount = transactions.sumOf { it.amount }
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
                (perspective.accountIds.isEmpty() || account?.id in perspective.accountIds)
        }
        is ReportPerspective.CreditCardPerspective -> {
            target == Transaction.Target.CREDIT_CARD &&
                creditCard?.id == perspective.creditCardId
        }
    }
}
