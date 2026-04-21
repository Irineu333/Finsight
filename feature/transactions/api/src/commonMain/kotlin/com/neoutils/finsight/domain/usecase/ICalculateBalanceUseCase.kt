package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.YearMonth

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
