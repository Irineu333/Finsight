package com.neoutils.finance.usecase

import com.neoutils.finance.domain.model.Transaction
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

class CalculateBalanceUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        upToYearMonth: YearMonth
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= upToYearMonth }
            .sumOf { transaction ->
                when (transaction.type) {
                    Transaction.Type.INCOME -> transaction.amount
                    Transaction.Type.EXPENSE -> -transaction.amount
                    Transaction.Type.ADJUSTMENT -> transaction.amount
                }
            }
    }
}
