@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.exception.FutureMonthAdjustmentException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdjustInitialBalanceUseCase(
    private val adjustBalanceUseCase: AdjustBalanceUseCase
) {

    private val currentYearMonth get() = Clock.System.now().toYearMonth()

    suspend operator fun invoke(
        targetBalance: Double,
        targetMonth: YearMonth,
        account: Account
    ): Either<Throwable, Unit> {
        if (targetMonth > currentYearMonth) return FutureMonthAdjustmentException().left()

        return adjustBalanceUseCase(
            targetBalance = targetBalance,
            adjustmentDate = targetMonth.minusMonth().lastDay,
            account = account,
        )
    }
}
