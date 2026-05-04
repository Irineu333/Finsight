package com.neoutils.finsight.feature.dashboard.usecase

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.categories.model.CategorySpending
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateCategoryIncomeUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>,
        forYearMonth: YearMonth,
    ): List<CategorySpending> {
        val incomeTransactions = transactions.filter {
            it.type.isIncome && it.date.yearMonth == forYearMonth && it.categoryId != null
        }

        val totalIncome = incomeTransactions.sumOf { it.amount }

        return incomeTransactions
            .groupBy { it.categoryId!! }
            .mapNotNull { (categoryId, txs) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                val amount = txs.sumOf { it.amount }
                CategorySpending(
                    category = category,
                    amount = amount,
                    percentage = when {
                        totalIncome > 0 -> (amount / totalIncome) * 100
                        else -> 0.0
                    },
                )
            }
            .sortedByDescending { it.amount }
    }
}
