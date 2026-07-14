package com.neoutils.finsight.ui.modal.viewBudget

import com.neoutils.finsight.domain.model.BudgetProgress

sealed interface ViewBudgetUiState {

    data object Loading : ViewBudgetUiState

    data class Content(
        val budgetProgress: BudgetProgress,
    ) : ViewBudgetUiState
}
