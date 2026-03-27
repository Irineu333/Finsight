@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.signedImpact
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.toYearMonth
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
        transactions: List<Transaction>,
        accountId: Long? = null
    ): Double {
        return transactions
            .filter { it.date.yearMonth <= target }
            .filter { it.target.isAccount }
            .filter { accountId == null || it.account?.id == accountId }
            .sumOf { it.signedImpact() }
    }

    suspend operator fun invoke(
        target: YearMonth,
        accountId: Long? = null,
    ): Double {
        return invoke(
            transactions = repository.getAllTransactions(),
            target = target,
            accountId = accountId,
        )
    }
}
