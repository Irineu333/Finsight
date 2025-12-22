@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.extension.toYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdjustInitialBalanceUseCase(
    private val adjustBalanceUseCase: AdjustBalanceUseCase
) {

    private val currentYearMonth get() = Clock.System.now().toYearMonth()

    suspend operator fun invoke(
        targetBalance: Double,
        targetMonth: YearMonth
    ) {

        if (targetMonth > currentYearMonth) return

        adjustBalanceUseCase(
            targetBalance = targetBalance,
            adjustmentDate = targetMonth.minusMonth().lastDay
        )
    }
}
