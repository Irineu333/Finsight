package com.neoutils.finsight.feature.transactions.usecase

import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.feature.transactions.usecase.ICalculateBalanceUseCase

interface ICalculateBalanceUseCase {
    operator fun invoke(
        target: YearMonth,
        transactions: List<Transaction>,
        accountId: Long? = null,
    ): Double

    suspend operator fun invoke(
        target: YearMonth,
        accountId: Long? = null,
    ): Double
}
