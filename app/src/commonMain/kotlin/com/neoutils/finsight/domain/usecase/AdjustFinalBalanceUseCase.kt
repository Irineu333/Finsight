@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.exception.FutureMonthAdjustmentException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdjustFinalBalanceUseCase(
    private val adjustBalanceUseCase: AdjustBalanceUseCase
) {
    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDateTime get() = Clock.System.now().toLocalDateTime(timeZone)
    private val currentYearMonth get() = Clock.System.now().toYearMonth()

    suspend operator fun invoke(
        targetBalance: Double,
        targetMonth: YearMonth,
        account: Account
    ): Either<Throwable, Unit> {
        if (targetMonth > currentYearMonth) return FutureMonthAdjustmentException().left()

        if (targetMonth == currentYearMonth) {
            return adjustBalanceUseCase(
                targetBalance = targetBalance,
                adjustmentDate = currentDateTime.date,
                account = account,
            )
        }

        return adjustBalanceUseCase(
            targetBalance = targetBalance,
            adjustmentDate = targetMonth.lastDay,
            account = account,
        )
    }
}
