@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.transactions.usecase

import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import com.neoutils.finsight.feature.transactions.extension.signedImpact
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.feature.transactions.usecase.ICalculateBalanceUseCase

class CalculateBalanceUseCase(
    private val repository: ITransactionRepository
) : ICalculateBalanceUseCase {

    override operator fun invoke(
        target: YearMonth,
        transactions: List<Transaction>,
        accountId: Long?,
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= target }
            .filter { it.target.isAccount }
            .filter { accountId == null || it.accountId == accountId }
            .sumOf { it.signedImpact() }
    }

    override suspend operator fun invoke(
        target: YearMonth,
        accountId: Long?,
    ): Double {
        return invoke(
            transactions = repository.getAllTransactions(),
            target = target,
            accountId = accountId,
        )
    }
}
