package com.neoutils.finsight.feature.budgets.modal.viewBudget

import androidx.compose.ui.graphics.Color
import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.feature.categories.model.Category

sealed class ViewBudgetUiState {
    data object Loading : ViewBudgetUiState()
    data object Error : ViewBudgetUiState()
    data class Content(
        val budgetProgress: BudgetProgress,
        val categories: List<Category>,
        val accentColor: Color,
    ) : ViewBudgetUiState()
}
