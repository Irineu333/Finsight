@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class BudgetsViewModel(
    private val budgetRepository: IBudgetRepository,
    private val operationRepository: IOperationRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        operationRepository.observeAllOperations(),
        selectedMonth,
    ) { budgets, operations, selectedMonth ->
        val transactions = operations.flatMap { it.transactions }
        val systemToday = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val today = if (selectedMonth == systemToday.yearMonth) {
            systemToday
        } else {
            LocalDate(selectedMonth.year, selectedMonth.month, 1)
        }
        BudgetsUiState(
            budgetProgress = calculateBudgetProgressUseCase(
                budgets = budgets,
                transactions = transactions,
                today = today,
            ),
            selectedMonth = selectedMonth,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState(
            selectedMonth = selectedMonth.value,
        ),
    )

    fun onAction(action: BudgetsAction) {
        when (action) {
            is BudgetsAction.SelectMonth -> selectedMonth.update { action.yearMonth }
        }
    }
}
