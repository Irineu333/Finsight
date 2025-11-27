package com.neoutils.finance.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BalanceStats(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val balance: Double = 0.0
)

data class DashboardUiState(
    val recents: List<TransactionEntry> = emptyList(),
    val balance: BalanceStats = BalanceStats()
)

class DashboardViewModel(
    repository: TransactionRepository
) : ViewModel() {

    val uiState = repository.getAllTransactions().map { transactions ->
        val income = transactions
            .filter { it.type == TransactionEntry.Type.INCOME }
            .sumOf { it.amount }

        val expense = transactions
            .filter { it.type == TransactionEntry.Type.EXPENSE }
            .sumOf { it.amount }

        DashboardUiState(
            recents = transactions.take(3),
            balance = BalanceStats(
                income = income,
                expense = expense,
                balance = income - expense
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}