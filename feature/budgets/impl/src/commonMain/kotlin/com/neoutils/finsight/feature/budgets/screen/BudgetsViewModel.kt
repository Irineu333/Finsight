@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.budgets.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.utils.extension.toYearMonth
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.budgets.usecase.IGetBudgetProgressUseCase
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
    budgetRepository: IBudgetRepository,
    private val getBudgetProgress: IGetBudgetProgressUseCase,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        selectedMonth,
    ) { budgets, selectedMonth ->
        val systemToday = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val today = systemToday.takeIf {
            selectedMonth == systemToday.yearMonth
        } ?: selectedMonth.firstDay

        val budgetProgress = budgets.mapNotNull { budget ->
            getBudgetProgress(
                budgetId = budget.id,
                today = today,
            )
        }

        if (budgetProgress.isEmpty()) {
            BudgetsUiState.Empty(selectedMonth = selectedMonth)
        } else {
            BudgetsUiState.Content(
                budgetProgress = budgetProgress,
                selectedMonth = selectedMonth,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState.Loading(
            selectedMonth = selectedMonth.value
        ),
    )

    fun onAction(action: BudgetsAction) {
        when (action) {
            is BudgetsAction.SelectMonth -> selectedMonth.update { action.yearMonth }
        }
    }
}
