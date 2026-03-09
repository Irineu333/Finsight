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
    ): List<CategorySpending> {
        val expenseTransactions = operations
            .filter { it.date in startDate..endDate }
            .flatMap { it.transactions }
            .filter { it.type.isExpense && it.category != null && it.matchesPerspective(perspective) }

        val totalExpense = expenseTransactions.sumOf { it.amount }

        return expenseTransactions
            .groupBy { it.category!! }
            .map { (category, transactions) ->
                val amount = transactions.sumOf { it.amount }
                CategorySpending(
                    category = category,
                    amount = amount,
                    percentage = when {
                        totalExpense > 0 -> (amount / totalExpense) * 100
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
