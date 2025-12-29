@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import kotlinx.coroutines.flow.first
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CalculateBalanceUseCase(
    private val repository: ITransactionRepository
) {
    operator fun invoke(
        target: YearMonth,
        transactions: List<Transaction>
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= target }
            .filter { it.target.isAccount }
            .sumOf { it.accountAmount }
    }

    suspend operator fun invoke(
        target: YearMonth,
    ): Double {
        return invoke(
            transactions = repository.getAllTransactions(),
            target = target,
        )
    }
}
