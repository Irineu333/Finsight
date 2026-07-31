package com.neoutils.finsight.feature.budgets.api

import com.neoutils.finsight.ui.component.AdaptiveModal
import kotlinx.datetime.YearMonth

interface BudgetsEntry {
    /**
     * @param month the month whose spending the detail answers for. Required, and without
     * a default: a budget's progress is a fact about a month, and a screen that shows one
     * month while its detail reads another is the defect this parameter exists to make
     * unutterable.
     */
    fun viewBudgetModal(budgetId: Long, month: YearMonth): AdaptiveModal
}
