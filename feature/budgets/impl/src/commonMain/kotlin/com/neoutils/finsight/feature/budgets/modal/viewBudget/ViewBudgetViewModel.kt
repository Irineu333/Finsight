@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.feature.budgets.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.theme.budgetProgressColor
import com.neoutils.finsight.feature.budgets.error.BudgetError
import com.neoutils.finsight.feature.budgets.exception.BudgetException
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.budgets.usecase.ICalculateBudgetProgressUseCase
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ViewBudgetViewModel(
    private val budgetId: Long,
    budgetRepository: IBudgetRepository,
    operationRepository: IOperationRepository,
    recurringRepository: IRecurringRepository,
    categoryRepository: ICategoryRepository,
    private val calculateBudgetProgress: ICalculateBudgetProgressUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val budget = budgetRepository.observeBudgetById(budgetId)

    val uiState = budget.flatMapLatest { budget ->
        if (budget == null) {
            crashlytics.recordException(BudgetException(BudgetError.NOT_FOUND))
            return@flatMapLatest flowOf(ViewBudgetUiState.Error)
        }

        combine(
            operationRepository.observeAllOperations(),
            recurringRepository.observeAllRecurring(),
            categoryRepository.observeCategoriesByIds(budget.categoryIds),
        ) { operations, recurringList, categories ->
            val transactions = operations.flatMap { it.transactions }
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val budgetProgress = calculateBudgetProgress(
                budgets = listOf(budget),
                transactions = transactions,
                recurringList = recurringList,
                operations = operations,
                today = today,
            ).first()

            ViewBudgetUiState.Content(
                budgetProgress = budgetProgress,
                categories = categories,
                accentColor = budgetProgressColor(budgetProgress.progress),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewBudgetUiState.Loading,
    )
}
