package com.neoutils.finsight.feature.budgets.impl

import com.neoutils.finsight.feature.budgets.api.BudgetsEntry
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetModal

internal class BudgetsEntryImpl : BudgetsEntry {
    override fun viewBudgetModal(budgetId: Long): AdaptiveModal =
        ViewBudgetModal(budgetId)
}
