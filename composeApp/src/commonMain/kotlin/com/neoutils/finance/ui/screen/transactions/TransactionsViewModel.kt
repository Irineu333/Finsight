@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val repository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val selectedType = MutableStateFlow<Transaction.Type?>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.getAllTransactions(),
        categoryRepository.getAllCategories(),
        selectedYearMonth,
        selectedCategoryId,
        selectedType
    ) { transactions, categories, yearMonth, categoryId, type ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = yearMonth,
            categoryId = categoryId,
            type = type
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
            categories = categories.associateBy { it.id },
            selectedCategoryId = categoryId,
            selectedType = type
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

            is TransactionsAction.SelectCategory -> {
                selectedCategoryId.value = action.categoryId
            }

            is TransactionsAction.SelectType -> {
                selectedType.value = action.type
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
