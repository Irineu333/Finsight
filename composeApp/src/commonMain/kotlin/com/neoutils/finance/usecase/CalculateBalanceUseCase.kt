package com.neoutils.finance.usecase

import com.neoutils.finance.data.TransactionEntry
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateBalanceUseCase {
    operator fun invoke(
        transactions: List<TransactionEntry>,
        upToYearMonth: YearMonth
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= upToYearMonth }
            .sumOf { transaction ->
                when (transaction.type) {
                    TransactionEntry.Type.INCOME -> transaction.amount
                    TransactionEntry.Type.EXPENSE -> -transaction.amount
                    TransactionEntry.Type.ADJUSTMENT -> transaction.amount
                }
            }
    }
}
