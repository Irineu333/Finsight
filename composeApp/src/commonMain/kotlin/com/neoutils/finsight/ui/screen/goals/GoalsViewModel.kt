@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IGoalRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.usecase.CalculateGoalProgressUseCase
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

class GoalsViewModel(
    private val goalRepository: IGoalRepository,
    private val operationRepository: IOperationRepository,
    private val calculateGoalProgressUseCase: CalculateGoalProgressUseCase,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        goalRepository.observeAllGoals(),
        operationRepository.observeAllOperations(),
        selectedMonth,
    ) { goals, operations, selectedMonth ->
        val transactions = operations.flatMap { it.transactions }
        val systemToday = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val today = if (selectedMonth == systemToday.yearMonth) {
            systemToday
        } else {
            LocalDate(selectedMonth.year, selectedMonth.month, 1)
        }
        val goalProgress = calculateGoalProgressUseCase(
            goals = goals,
            transactions = transactions,
            today = today,
        )
        if (goalProgress.isEmpty()) {
            GoalsUiState.Empty(selectedMonth = selectedMonth)
        } else {
            GoalsUiState.Content(
                goalProgress = goalProgress,
                selectedMonth = selectedMonth,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState.Loading(selectedMonth = selectedMonth.value),
    )

    fun onAction(action: GoalsAction) {
        when (action) {
            is GoalsAction.SelectMonth -> selectedMonth.update { action.yearMonth }
        }
    }
}
