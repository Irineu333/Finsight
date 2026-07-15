package com.neoutils.finsight.feature.budgets.api

import com.neoutils.finsight.ui.component.AdaptiveModal

interface BudgetsEntry {
    fun viewBudgetModal(budgetId: Long): AdaptiveModal
}
