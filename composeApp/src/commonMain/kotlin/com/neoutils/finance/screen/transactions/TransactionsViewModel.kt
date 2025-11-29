@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.usecase.AdjustBalanceUseCase
import com.neoutils.finance.usecase.CalculateBalanceUseCase
import com.neoutils.finance.usecase.CalculateTransactionStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val repository: TransactionRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.getAllTransactions(),
        selectedYearMonth
    ) { transactions, yearMonth ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = yearMonth
        )

        TransactionsUiState(
            transactions = stats.transactions
                .sortedByDescending { it.date }
                .groupBy { it.date },
            balanceOverview = TransactionsUiState.BalanceOverview(
                income = stats.income,
                expense = stats.expense,
                adjustment = stats.adjustment,
                initialBalance = calculateBalanceUseCase(
                    transactions = transactions,
                    upToYearMonth = yearMonth.minusMonth(),
                ),
                finalBalance = calculateBalanceUseCase(
                    transactions = transactions,
                    upToYearMonth = yearMonth
                )
            ),
            selectedYearMonth = yearMonth,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onAction(action: TransactionsAction) {
        when (action) {
            is TransactionsAction.AdjustBalance -> {
                adjustBalance(action.target)

            }

            is TransactionsAction.AdjustInitialBalance -> {
                adjustInitialBalance(action.target)
            }

            TransactionsAction.NextMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.plusMonth()
            }

            TransactionsAction.PreviousMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.minusMonth()
            }
        }
    }

    private fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        val currentMonth = Clock.System.now().toYearMonth()
        val selectedMonth = selectedYearMonth.value

        if (selectedMonth > currentMonth) return@launch

        if (selectedMonth == currentMonth) {
            adjustBalanceUseCase(
                currentBalance = uiState.value.balanceOverview.finalBalance,
                targetBalance = targetBalance,
                adjustmentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )
            return@launch
        }

        adjustBalanceUseCase(
            currentBalance = uiState.value.balanceOverview.finalBalance,
            targetBalance = targetBalance,
            adjustmentDate = selectedMonth.lastDay
        )
    }

    private fun adjustInitialBalance(targetInitialBalance: Double) = viewModelScope.launch {
        val currentMonth = Clock.System.now().toYearMonth()
        val selectedMonth = selectedYearMonth.value

        if (selectedMonth > currentMonth) return@launch

        adjustBalanceUseCase(
            currentBalance = uiState.value.balanceOverview.initialBalance,
            targetBalance = targetInitialBalance,
            adjustmentDate = selectedMonth.minus(1, DateTimeUnit.MONTH).lastDay
        )
    }
}
