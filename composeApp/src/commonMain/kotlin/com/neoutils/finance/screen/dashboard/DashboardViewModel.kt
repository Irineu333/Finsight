@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class BalanceStats(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val balance: Double = 0.0
)

data class DashboardUiState(
    val recents: List<TransactionEntry> = emptyList(),
    val balance: BalanceStats = BalanceStats(),
    val currentMonth: YearMonth = Clock.System.now().toYearMonth()
)

private fun Instant.toYearMonth(): YearMonth {
    return toLocalDateTime(TimeZone.currentSystemDefault()).let {
        YearMonth(it.year, it.month)
    }
}

class DashboardViewModel(
    repository: TransactionRepository
) : ViewModel() {

    val uiState = repository
        .getAllTransactions()
        .map { allTransactions ->
            val currentMonth = Clock.System.now().toYearMonth()

            val filteredTransactions = allTransactions.filter { transaction ->
                transaction.date.yearMonth == currentMonth
            }

            val income = filteredTransactions
                .filter { it.type == TransactionEntry.Type.INCOME }
                .sumOf { it.amount }

            val expense = filteredTransactions
                .filter { it.type == TransactionEntry.Type.EXPENSE }
                .sumOf { it.amount }

            DashboardUiState(
                recents = filteredTransactions.take(3),
                balance = BalanceStats(
                    income = income,
                    expense = expense,
                    balance = income - expense
                ),
                currentMonth = currentMonth
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState()
        )
}