@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.editBalance.EditBalanceModal
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class EditBalanceViewModel(
    private val type: EditBalanceModal.Type,
    private val targetMonth: YearMonth?,
    private val currentBalance: Double,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val dateTime get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val currentMonth get() = Clock.System.now().toYearMonth()

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        when (type) {
            EditBalanceModal.Type.CURRENT -> adjustCurrentBalance(targetBalance)
            EditBalanceModal.Type.FINAL -> adjustFinalBalance(targetBalance)
            EditBalanceModal.Type.INITIAL -> adjustInitialBalance(targetBalance)
        }
        modalManager.dismiss()
    }

    private suspend fun adjustCurrentBalance(targetBalance: Double) {
        adjustBalanceUseCase(
            currentBalance = currentBalance,
            targetBalance = targetBalance,
            adjustmentDate = dateTime.date
        )
    }

    private suspend fun adjustFinalBalance(targetBalance: Double) {
        val selectedMonth = targetMonth ?: currentMonth

        if (selectedMonth > currentMonth) return

        val adjustmentDate = if (selectedMonth == currentMonth) {
            dateTime.date
        } else {
            selectedMonth.lastDay
        }

        adjustBalanceUseCase(
            currentBalance = currentBalance,
            targetBalance = targetBalance,
            adjustmentDate = adjustmentDate
        )
    }

    private suspend fun adjustInitialBalance(targetBalance: Double) {
        val selectedMonth = targetMonth ?: currentMonth

        if (selectedMonth > currentMonth) return

        adjustBalanceUseCase(
            currentBalance = currentBalance,
            targetBalance = targetBalance,
            adjustmentDate = selectedMonth.minus(1, DateTimeUnit.MONTH).lastDay
        )
    }
}
