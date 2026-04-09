package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateCategoryIncomeUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): List<CategorySpending> {
        val incomeTransactions = transactions.filter {
            it.type.isIncome && it.date.yearMonth == forYearMonth && it.category != null
        }

        val totalIncome = incomeTransactions.sumOf { it.amount }

        return incomeTransactions
            .groupBy { it.category!! }
            .map { (category, transactions) ->
                val amount = transactions.sumOf { it.amount }
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