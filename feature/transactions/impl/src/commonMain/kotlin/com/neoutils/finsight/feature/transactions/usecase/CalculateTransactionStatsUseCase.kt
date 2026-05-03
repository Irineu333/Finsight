package com.neoutils.finsight.feature.transactions.usecase

import com.neoutils.finsight.core.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateTransactionStatsUseCase : ICalculateTransactionStatsUseCase {
    override operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): ICalculateTransactionStatsUseCase.TransactionStats {
        val monthTransactions = transactions.filter { it.date.yearMonth == forYearMonth }

        val accountTransactions = monthTransactions.filter { it.target.isAccount }

        val expense = accountTransactions.filter { it.type.isExpense }
        val adjustment = accountTransactions.filter { it.type.isAdjustment }
        val income = accountTransactions.filter { it.type.isIncome }

        return ICalculateTransactionStatsUseCase.TransactionStats(
            income = income.sumOf { it.amount },
            expense = expense.sumOf { it.amount },
            adjustment = adjustment.sumOf { it.amount },
            transactions = monthTransactions,
        )
    }
}
