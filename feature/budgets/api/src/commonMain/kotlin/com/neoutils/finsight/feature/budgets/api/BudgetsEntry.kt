package com.neoutils.finsight.feature.budgets.api

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.ui.component.AdaptiveModal

interface BudgetsEntry {
    fun viewBudgetModal(budgetProgress: BudgetProgress): AdaptiveModal
}
