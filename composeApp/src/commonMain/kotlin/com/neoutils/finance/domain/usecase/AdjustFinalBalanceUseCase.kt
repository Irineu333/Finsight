@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.extension.toYearMonth
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
    ) {
        if (targetMonth > currentYearMonth) return

        if (targetMonth == currentYearMonth) {
            adjustBalanceUseCase(
                targetBalance = targetBalance,
                adjustmentDate = currentDateTime.date,
                account = account,
            )
            return
        }

        adjustBalanceUseCase(
            targetBalance = targetBalance,
            adjustmentDate = targetMonth.lastDay,
            account = account,
        )
    }
}
