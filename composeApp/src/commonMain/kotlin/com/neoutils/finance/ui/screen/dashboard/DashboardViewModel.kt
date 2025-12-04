@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val repository: ITransactionRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val instant get() = Clock.System.now()
    private val currentMonth get() = instant.toYearMonth()
    private val dateTime get() = instant.toLocalDateTime(timeZone)

    val uiState = repository.getAllTransactions().map { transactions ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        DashboardUiState(
            recents = stats.transactions.take(3),
            balance = DashboardUiState.BalanceStats(
                income = stats.income,
                expense = stats.expense,
                balance = calculateBalanceUseCase(
                    transactions = transactions,
                    upToYearMonth = currentMonth,
                )
            ),
            yearMonth = currentMonth,
            categorySpending = categorySpending.take(3),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.AdjustBalance -> {
                adjustBalance(action.target)
            }
        }
    }

    private fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        adjustBalanceUseCase(
            currentBalance = uiState.value.balance.balance,
            targetBalance = targetBalance,
            adjustmentDate = dateTime.date
        )
    }
}