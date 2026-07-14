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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewBudgetViewModel(
    private val budgetId: Long,
    budgetRepository: IBudgetRepository,
    operationRepository: IOperationRepository,
    recurringRepository: IRecurringRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
) : ViewModel() {

    private var loadedOnce = false

    private val _events = Channel<ViewBudgetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        operationRepository.observeAllOperations(),
        recurringRepository.observeAllRecurring(),
    ) { budgets, operations, recurringList ->
        val transactions = operations.flatMap { it.transactions }
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = budgets,
            transactions = transactions,
            recurringList = recurringList,
            operations = operations,
        ).firstOrNull { it.budget.id == budgetId }

        when {
            budgetProgress != null -> {
                loadedOnce = true
                ViewBudgetUiState.Content(budgetProgress)
            }

            loadedOnce -> {
                _events.send(ViewBudgetEvent.Dismiss)
                ViewBudgetUiState.Loading
            }

            else -> ViewBudgetUiState.Error
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewBudgetUiState.Loading,
    )
}
