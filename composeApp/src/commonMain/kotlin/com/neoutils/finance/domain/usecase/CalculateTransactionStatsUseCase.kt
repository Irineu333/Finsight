package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateTransactionStatsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): TransactionStats {
        val monthTransactions = transactions.filter { it.date.yearMonth == forYearMonth }

        return TransactionStats(
            income = monthTransactions.filter { it.type.isIncome }.sumOf { it.amount },
            expense = monthTransactions.filter { it.type.isExpense }.sumOf { it.amount },
            adjustment = monthTransactions.filter { it.type.isAdjustment }.sumOf { it.amount },
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
