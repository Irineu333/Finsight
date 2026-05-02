package com.neoutils.finsight.feature.budgets.screen

import kotlinx.datetime.YearMonth

sealed class BudgetsAction {
    data class SelectMonth(val yearMonth: YearMonth) : BudgetsAction()
}
