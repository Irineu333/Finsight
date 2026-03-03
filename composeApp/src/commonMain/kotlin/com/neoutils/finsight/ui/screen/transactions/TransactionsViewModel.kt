@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val filterType: Transaction.Type?,
    private val category: Category?,
    private val filterTarget: Transaction.Target?,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        TransactionsFilters(
            category = category,
            type = filterType,
            target = filterTarget,
        )
    )

    val uiState = combine(
        operationRepository.observeAllOperations(),
        categoryRepository.observeAllCategories(),
        selectedYearMonth,
        filters
    ) { operations, categories, yearMonth, filters ->
        val transactions = operations.flatMap { it.transactions }

        val transactionsForStats = operations
            .filterNot { it.kind == Operation.Kind.TRANSFER }
            .filterNot { it.kind == Operation.Kind.PAYMENT }
            .flatMap { it.transactions }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactionsForStats,
            forYearMonth = yearMonth,
        )

        TransactionsUiState(
            balanceOverview = TransactionsUiState.BalanceOverview(
                income = stats.income,
                expense = stats.expense,
                adjustment = stats.adjustment,
                payment = operations
                    .filter { it.kind == Operation.Kind.PAYMENT }
                    .filter { it.date.yearMonth == yearMonth }
                    .sumOf { it.amount },
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
            selectedTarget = filters.target,
            showRecurringOnly = filters.recurringOnly,
            operations = operations
                .filter(filters.recurringOnly)
                .filter(filters.category)
                .filter(filters.type)
                .filter(filters.target)
                .filter { it.date.yearMonth == yearMonth }
                .sortedByDescending { it.date }
                .groupBy { it.date },
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

            is TransactionsAction.SelectMonth -> {
                selectedYearMonth.value = action.yearMonth
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

            is TransactionsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }
        }
    }
}

private fun List<Operation>.filter(recurringOnly: Boolean): List<Operation> {
    if (!recurringOnly) return this
    return filter { operation -> operation.recurring != null }
}

private fun List<Operation>.filter(category: Category?): List<Operation> {
    if (category == null) return this
    return filter { it.category?.id == category.id || it.primaryTransaction.category?.id == category.id }
}

private fun List<Operation>.filter(type: Transaction.Type?): List<Operation> {
    if (type == null) return this
    return filter { operation ->
        operation.type == type
    }
}

private fun List<Operation>.filter(target: Transaction.Target?): List<Operation> {
    if (target == null) return this
    return filter { operation -> operation.transactions.any { it.target == target } }
}
