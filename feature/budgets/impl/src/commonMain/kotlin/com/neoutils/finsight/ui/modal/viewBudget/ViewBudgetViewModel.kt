package com.neoutils.finsight.ui.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewBudgetViewModel(
    private val budgetId: Long,
    budgetRepository: IBudgetRepository,
    operationRepository: IOperationRepository,
    recurringRepository: IRecurringRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
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
        .onEach { if (it == null) _events.send(ViewBudgetEvent.Dismiss) }
        .filterNotNull()
        .map { ViewBudgetUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewBudgetUiState.Loading,
        )
}
