@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.extension.toYearMonth
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
    private val repository: TransactionRepository
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val transactions = combine(
        repository.getAllTransactions(),
        selectedYearMonth
    ) { transactions, yearMonth ->
        transactions.filter { transaction ->
            transaction.date.yearMonth <= yearMonth
        }
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactions,
        selectedYearMonth
    ) { transactions, yearMonth ->

        val previousTransactions = transactions.filter { transaction ->
            transaction.date.yearMonth < yearMonth
        }

        val currentTransactions = transactions.filter { transaction ->
            transaction.date.yearMonth == yearMonth
        }

        TransactionsUiState(
            transactions = currentTransactions
                .sortedByDescending { it.date }
                .groupBy { it.date },
            balanceOverview = TransactionsUiState.BalanceOverview(
                income = currentTransactions.filter { it.type.isIncome }.sumOf { it.amount },
                expense = currentTransactions.filter { it.type.isExpense }.sumOf { it.amount },
                adjustment = currentTransactions.filter { it.type.isAdjustment }.sumOf { it.amount },
                initialBalance = previousTransactions.sumOf { transaction ->
                    when (transaction.type) {
                        TransactionEntry.Type.INCOME -> transaction.amount
                        TransactionEntry.Type.EXPENSE -> -transaction.amount
                        TransactionEntry.Type.ADJUSTMENT -> transaction.amount
                    }
                },
                finalBalance = transactions.sumOf { transaction ->
                    when (transaction.type) {
                        TransactionEntry.Type.INCOME -> transaction.amount
                        TransactionEntry.Type.EXPENSE -> -transaction.amount
                        TransactionEntry.Type.ADJUSTMENT -> transaction.amount
                    }
                }
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

        val currentFinalBalance = uiState.value.balanceOverview.finalBalance

        if (targetBalance == currentFinalBalance) return@launch

        val adjustmentDate = if (selectedMonth == currentMonth) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        } else {
            selectedMonth.lastDay
        }

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = TransactionEntry.Type.ADJUSTMENT,
            date = adjustmentDate
        )

        val difference = targetBalance - currentFinalBalance

        if (existingAdjustment == null) {
            repository.insert(
                TransactionEntry(
                    type = TransactionEntry.Type.ADJUSTMENT,
                    amount = difference,
                    description = "Ajuste de Saldo",
                    date = adjustmentDate
                )
            )
            return@launch
        }

        val newAmount = existingAdjustment.amount + difference

        if (newAmount == 0.0) {
            repository.delete(existingAdjustment)
            return@launch
        }

        repository.update(
            existingAdjustment.copy(amount = newAmount)
        )
    }

    private fun adjustInitialBalance(targetInitialBalance: Double) = viewModelScope.launch {
        val currentMonth = Clock.System.now().toYearMonth()
        val selectedMonth = selectedYearMonth.value

        if (selectedMonth > currentMonth) return@launch

        val currentInitialBalance = uiState.value.balanceOverview.initialBalance

        if (targetInitialBalance == currentInitialBalance) return@launch

        val adjustmentDate = selectedMonth.minus(1, DateTimeUnit.MONTH).lastDay

        val existingAdjustment = repository.getTransactionByTypeAndDate(
            type = TransactionEntry.Type.ADJUSTMENT,
            date = adjustmentDate,
        )

        val difference = targetInitialBalance - currentInitialBalance

        if (existingAdjustment == null) {
            repository.insert(
                TransactionEntry(
                    type = TransactionEntry.Type.ADJUSTMENT,
                    amount = difference,
                    description = "Ajuste de Saldo",
                    date = adjustmentDate
                )
            )
            return@launch
        }

        val newAmount = existingAdjustment.amount + difference

        if (newAmount == 0.0) {
            repository.delete(existingAdjustment)

            return@launch
        }

        repository.update(existingAdjustment.copy(amount = newAmount))
    }
}
