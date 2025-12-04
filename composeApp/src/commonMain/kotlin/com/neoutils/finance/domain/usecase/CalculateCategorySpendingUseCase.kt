package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CategorySpending
import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateCategorySpendingUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): List<CategorySpending> {
        val expenseTransactions = transactions.filter {
            it.type.isExpense && it.date.yearMonth == forYearMonth && it.category != null
        }

        if (expenseTransactions.isEmpty()) return listOf()

        val totalExpense = expenseTransactions.sumOf { it.amount }

        return expenseTransactions
            .groupBy { it.category!! }
            .map { (category, transactions) ->
                val amount = transactions.sumOf { it.amount }
                CategorySpending(
                    category = category,
                    amount = amount,
                    percentage = if (totalExpense > 0) (amount / totalExpense) * 100 else 0.0
                )
            }
            .sortedByDescending { it.amount }
    }
}
