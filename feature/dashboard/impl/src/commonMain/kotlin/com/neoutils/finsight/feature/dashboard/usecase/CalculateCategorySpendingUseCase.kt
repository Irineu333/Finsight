package com.neoutils.finsight.feature.dashboard.usecase

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.categories.model.CategorySpending
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateCategorySpendingUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>,
        forYearMonth: YearMonth,
    ): List<CategorySpending> {
        val expenseTransactions = transactions.filter {
            it.type.isExpense && it.date.yearMonth == forYearMonth && it.categoryId != null
        }

        val totalExpense = expenseTransactions.sumOf { it.amount }

        return expenseTransactions
            .groupBy { it.categoryId!! }
            .mapNotNull { (categoryId, txs) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                val amount = txs.sumOf { it.amount }
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
