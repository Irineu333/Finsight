package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth

interface ICalculateTransactionStatsUseCase {
    operator fun invoke(
        transactions: List<Transaction>,
        forYearMonth: YearMonth,
    ): TransactionStats

    data class TransactionStats(
        val income: Double,
        val expense: Double,
        val adjustment: Double,
        val transactions: List<Transaction>,
    )
}
