package com.neoutils.finsight.ui.screen.budgets

import com.neoutils.finsight.domain.model.BudgetProgress
import kotlinx.datetime.YearMonth

data class BudgetsUiState(
    val budgetProgress: List<BudgetProgress> = emptyList(),
    val selectedMonth: YearMonth? = null,
    val isLoading: Boolean = true,
)
