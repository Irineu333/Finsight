package com.neoutils.finsight.ui.screen.budgets

import com.neoutils.finsight.domain.model.BudgetProgress
import kotlinx.datetime.YearMonth

sealed class BudgetsUiState {

    abstract val selectedMonth: YearMonth

    data class Loading(
        override val selectedMonth: YearMonth,
    ) : BudgetsUiState()

    data class Empty(
        override val selectedMonth: YearMonth,
    ) : BudgetsUiState()

    data class Content(
        val budgetProgress: List<BudgetProgress>,
        override val selectedMonth: YearMonth,
    ) : BudgetsUiState()
}