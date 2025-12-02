@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.usecase.AdjustBalanceUseCase
import com.neoutils.finance.usecase.CalculateBalanceUseCase
import com.neoutils.finance.usecase.CalculateTransactionStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val repository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase
) : ViewModel() {

    private val currentMonth get() = Clock.System.now().toYearMonth()

    val uiState = combine(
        repository.getAllTransactions(),
        categoryRepository.getAllCategories()
    ) { transactions, categories ->

        val stats = calculateTransactionStatsUseCase(
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
            categories = categories.associateBy { it.id }
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
            adjustmentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        )
    }
}