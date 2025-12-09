@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val transaction: Transaction.Type?,
    private val category: Category?,
    private val target: Transaction.Target?,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        TransactionsFilters(
            category = category,
            type = transaction,
            target = target
        )
    )

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        categoryRepository.getAllCategories(),
        selectedYearMonth,
        filters
    ) { transactions, categories, yearMonth, filters ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions
                .filter(filters.category)
                .filter(filters.type)
                .filter(filters.target),
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
                    target = yearMonth.minusMonth(),
                    transactions = transactions,
                ),
                finalBalance = calculateBalanceUseCase(
                    target = yearMonth,
                    transactions = transactions,
                )
            ),
            selectedYearMonth = yearMonth,
            categories = categories,
            selectedCategory = filters.category,
            selectedType = filters.type,
            selectedTarget = filters.target
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onAction(action: TransactionsAction) = viewModelScope.launch {
        when (action) {
            TransactionsAction.NextMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.plusMonth()
            }

            TransactionsAction.PreviousMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.minusMonth()
            }

            is TransactionsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is TransactionsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is TransactionsAction.SelectTarget -> {
                filters.value = filters.value.copy(target = action.target)
            }
        }
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

private fun List<Transaction>.filter(
    target: Transaction.Target?
): List<Transaction> {
    if (target == null) return this
    return filter { it.target == target }
}