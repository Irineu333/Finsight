@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.screen.dashboard.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.ExperimentalTime

data class BalanceOverview(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val finalBalance: Double = 0.0
)

data class TransactionsUiState(
    val transactions: List<TransactionEntry> = emptyList(),
    val balanceOverview: BalanceOverview = BalanceOverview(),
)

class TransactionsViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = repository
        .getAllTransactions()
        .map { transactions ->
            val income = transactions
                .filter { it.type == TransactionEntry.Type.INCOME }
                .sumOf { it.amount }

            val expense = transactions
                .filter { it.type == TransactionEntry.Type.EXPENSE }
                .sumOf { it.amount }

            val finalBalance = income - expense

            TransactionsUiState(
                transactions = transactions.sortedByDescending { it.date },
                balanceOverview = BalanceOverview(
                    income = income,
                    expense = expense,
                    finalBalance = finalBalance
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionsUiState()
        )

    fun groupTransactionsByDate(
        transactions: List<TransactionEntry>
    ): Map<LocalDate, List<TransactionEntry>> {
        return transactions.groupBy { it.date }
    }
}
