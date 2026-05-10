package com.neoutils.finsight.feature.budgets.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.theme.budgetProgressColor
import com.neoutils.finsight.feature.budgets.error.BudgetError
import com.neoutils.finsight.feature.budgets.exception.BudgetException
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.budgets.usecase.IGetBudgetProgressUseCase
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ViewBudgetViewModel(
    private val budgetId: Long,
    categoryRepository: ICategoryRepository,
    private val getBudgetProgress: IGetBudgetProgressUseCase,
    private val budgetRepository: IBudgetRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val budget = flow {
        val budget = budgetRepository.getBudgetById(budgetId)

        if (budget == null) {
            crashlytics.recordException(BudgetException(BudgetError.NOT_FOUND))
        }

        emit(budget)
    }

    val uiState = budget.map { budget ->

        if (budget == null) {
            return@map ViewBudgetUiState.Error
        }

        val progress = getBudgetProgress(budget)

        ViewBudgetUiState.Content(
            budgetProgress = progress,
            categories = categoryRepository.getCategoriesByIds(budget.categoryIds),
            accentColor = budgetProgressColor(progress.progress),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewBudgetUiState.Loading,
    )
}
