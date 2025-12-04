@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val transaction: Transaction.Type?,
    private val category: Category?,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase
) : ViewModel() {

    private val dateTime get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())
    private val selectedCategory = MutableStateFlow(category)
    private val selectedType = MutableStateFlow(transaction)

    val uiState = combine(
        transactionRepository.getAllTransactions(),
        categoryRepository.getAllCategories(),
        selectedYearMonth,
        selectedCategory,
        selectedType
    ) { transactions, categories, yearMonth, category, type ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions
                .filter(category)
                .filter(type),
            forYearMonth = yearMonth,
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
            categories = categories,
            selectedCategory = category,
            selectedType = type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onAction(action: TransactionsAction) = viewModelScope.launch {
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
                selectedCategory.value = action.category
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
                adjustmentDate = dateTime.date,
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

private fun List<Transaction>.filter(
    category: Category?
): List<Transaction> {
    if (category == null) return this
    return filter { it.category?.id == category.id }
}

private fun List<Transaction>.filter(
    type: Transaction.Type?
): List<Transaction> {
    if (type == null) return this
    return filter { it.type == type }
}