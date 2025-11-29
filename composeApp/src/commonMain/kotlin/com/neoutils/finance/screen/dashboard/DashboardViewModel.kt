@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.usecase.AdjustBalanceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val repository: TransactionRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase
) : ViewModel() {

    private val currentMonth get() = Clock.System.now().toYearMonth()

    val uiState = repository
        .getAllTransactions()
        .map { transactions ->
            transactions.filter { transaction ->
                transaction.date.yearMonth <= currentMonth
            }
        }
        .map { transactions ->

            val currentTransactions = transactions.filter { transaction ->
                transaction.date.yearMonth == currentMonth
            }

            DashboardUiState(
                recents = currentTransactions.take(3),
                balance = DashboardUiState.BalanceStats(
                    income = currentTransactions.filter { it.type.isIncome }.sumOf { it.amount },
                    expense = currentTransactions.filter { it.type.isExpense }.sumOf { it.amount },
                    balance = transactions.sumOf { transaction ->
                        when (transaction.type) {
                            TransactionEntry.Type.INCOME -> transaction.amount
                            TransactionEntry.Type.EXPENSE -> -transaction.amount
                            TransactionEntry.Type.ADJUSTMENT -> transaction.amount
                        }
                    }
                ),
                yearMonth = currentMonth
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