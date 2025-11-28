@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class BalanceOverview(
    val initialBalance: Double = 0.0,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val adjustment: Double = 0.0,
    val finalBalance: Double = 0.0
)

data class TransactionsUiState(
    val transactions: Map<LocalDate, List<TransactionEntry>> = emptyMap(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
    val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth()
)

private fun Instant.toYearMonth(): YearMonth {
    return toLocalDateTime(TimeZone.currentSystemDefault()).let {
        YearMonth(it.year, it.month)
    }
}

class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.getAllTransactions(),
        selectedYearMonth
    ) { allTransactions, yearMonth ->
        val filteredTransactions = allTransactions.filter { transaction ->
            transaction.date.yearMonth == yearMonth
        }

        val previousMonthsTransactions = allTransactions.filter { transaction ->
            transaction.date.yearMonth < yearMonth
        }

        val initialBalance = previousMonthsTransactions.sumOf { transaction ->
            when (transaction.type) {
                TransactionEntry.Type.INCOME -> transaction.amount
                TransactionEntry.Type.EXPENSE -> -transaction.amount
                TransactionEntry.Type.ADJUSTMENT -> transaction.amount
            }
        }

        val income = filteredTransactions
            .filter { it.type == TransactionEntry.Type.INCOME }
            .sumOf { it.amount }

        val expense = filteredTransactions
            .filter { it.type == TransactionEntry.Type.EXPENSE }
            .sumOf { it.amount }

        val adjustment = filteredTransactions
            .filter { it.type == TransactionEntry.Type.ADJUSTMENT }
            .sumOf { it.amount }

        val finalBalance = initialBalance + income - expense + adjustment

        TransactionsUiState(
            transactions = filteredTransactions
                .sortedByDescending { it.date }
                .groupBy { it.date },
            balanceOverview = BalanceOverview(
                initialBalance = initialBalance,
                income = income,
                expense = expense,
                adjustment = adjustment,
                finalBalance = finalBalance
            ),
            selectedYearMonth = yearMonth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun selectPreviousMonth() {
        selectedYearMonth.value = selectedYearMonth.value.minusMonth()
    }

    fun selectNextMonth() {
        selectedYearMonth.value = selectedYearMonth.value.plusMonth()
    }
}
