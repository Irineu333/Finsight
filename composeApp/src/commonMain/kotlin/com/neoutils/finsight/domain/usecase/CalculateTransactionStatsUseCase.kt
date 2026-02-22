package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateTransactionStatsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): TransactionStats {
        val monthTransactions = transactions.filter { it.date.yearMonth == forYearMonth }

        val accountTransactions = monthTransactions.filter { it.target.isAccount }

        val expense = accountTransactions.filter { it.type.isExpense }
        val adjustment = accountTransactions.filter { it.type.isAdjustment }
        val income = accountTransactions.filter { it.type.isIncome }

        return TransactionStats(
            income = income.sumOf { it.amount },
            expense = expense.sumOf { it.amount },
            adjustment = adjustment.sumOf { it.amount },
            transactions = monthTransactions,
        )
    }

    data class TransactionStats(
        val income: Double,
        val expense: Double,
        val adjustment: Double,
        val transactions: List<Transaction>
    )
}
