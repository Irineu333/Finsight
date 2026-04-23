package com.neoutils.finsight.ui.screen.budgets

import kotlinx.datetime.YearMonth

sealed class BudgetsAction {
    data class SelectMonth(val yearMonth: YearMonth) : BudgetsAction()
}
