@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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
    private val repository: TransactionRepository
) : ViewModel() {

    val uiState = repository
        .getAllTransactions()
        .map { allTransactions ->
            val currentMonth = Clock.System.now().toYearMonth()

            val filteredTransactions = allTransactions.filter { transaction ->
                transaction.date.yearMonth == currentMonth
            }

            val previousMonthsBalance = allTransactions
                .filter { transaction -> transaction.date.yearMonth < currentMonth }
                .sumOf { transaction ->
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

            DashboardUiState(
                recents = filteredTransactions.take(3),
                balance = BalanceStats(
                    income = income,
                    expense = expense,
                    balance = previousMonthsBalance + income - expense + adjustment
                ),
                currentMonth = currentMonth
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState()
        )

    fun adjustBalance(targetBalance: Double) {
        viewModelScope.launch {
            val currentBalance = uiState.value.balance.balance
            val difference = targetBalance - currentBalance

            if (difference == 0.0) {
                return@launch
            }

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val existingAdjustment = repository.getTransactionByTypeAndDate(
                type = TransactionEntry.Type.ADJUSTMENT,
                date = today
            )

            if (existingAdjustment != null) {
                val newAmount = existingAdjustment.amount + difference

                if (newAmount == 0.0) {
                    repository.delete(existingAdjustment)
                } else {
                    val updatedAdjustment = existingAdjustment.copy(amount = newAmount)
                    repository.update(updatedAdjustment)
                }
            } else {
                val newAdjustment = TransactionEntry(
                    type = TransactionEntry.Type.ADJUSTMENT,
                    amount = difference,
                    description = "Ajuste de Saldo",
                    date = today
                )
                repository.insert(newAdjustment)
            }
        }
    }
}