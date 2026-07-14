package com.neoutils.finsight.ui.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex

class ViewBudgetViewModel(
    private val budgetId: Long,
    budgetRepository: IBudgetRepository,
    operationRepository: IOperationRepository,
    recurringRepository: IRecurringRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewBudgetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        operationRepository.observeAllOperations(),
        recurringRepository.observeAllRecurring(),
    ) { budgets, operations, recurringList ->
        val transactions = operations.flatMap { it.transactions }
        calculateBudgetProgressUseCase(
            budgets = budgets,
            transactions = transactions,
            recurringList = recurringList,
            operations = operations,
        ).firstOrNull { it.budget.id == budgetId }
    }
        .distinctUntilChanged()
        .withIndex()
        .onEach { (index, budgetProgress) ->
            when {
                budgetProgress != null -> Unit
                index == 0 -> crashlytics.recordException(DetailNotFoundException("Budget", budgetId))
                else -> _events.send(ViewBudgetEvent.Dismiss)
            }
        }
        .filter { (index, budgetProgress) -> budgetProgress != null || index == 0 }
        .map { (_, budgetProgress) ->
            budgetProgress?.let { ViewBudgetUiState.Content(it) }
                ?: ViewBudgetUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewBudgetUiState.Loading,
        )
}
