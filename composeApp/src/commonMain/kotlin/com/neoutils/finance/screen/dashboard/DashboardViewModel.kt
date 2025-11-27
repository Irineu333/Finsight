@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class BalanceStats(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val balance: Double = 0.0
)

data class YearMonth(
    val year: Int,
    val month: Month
) {
    val date = LocalDate(year, month, 1)

    fun previous(): YearMonth {
        val previousMonth = date.minus(1, DateTimeUnit.MONTH)
        return YearMonth(previousMonth.year, previousMonth.month)
    }

    fun next(): YearMonth {
        val nextMonth = date.plus(1, DateTimeUnit.MONTH)
        return YearMonth(nextMonth.year, nextMonth.month)
    }

    companion object {
        fun current(): YearMonth {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            return YearMonth(now.year, now.month)
        }

        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.month)
    }
}

data class DashboardUiState(
    val recents: List<TransactionEntry> = emptyList(),
    val balance: BalanceStats = BalanceStats(),
    val selectedYearMonth: YearMonth = YearMonth.current()
)

class DashboardViewModel(
    repository: TransactionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(YearMonth.current())

    val uiState = combine(
        repository.getAllTransactions(),
        selectedYearMonth
    ) { allTransactions, yearMonth ->
        val filteredTransactions = allTransactions.filter { transaction ->
            YearMonth.from(transaction.date) == yearMonth
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
            selectedYearMonth = yearMonth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun selectPreviousMonth() {
        selectedYearMonth.value = selectedYearMonth.value.previous()
    }

    fun selectNextMonth() {
        selectedYearMonth.value = selectedYearMonth.value.next()
    }

    fun selectMonth(yearMonth: YearMonth) {
        selectedYearMonth.value = yearMonth
    }
}