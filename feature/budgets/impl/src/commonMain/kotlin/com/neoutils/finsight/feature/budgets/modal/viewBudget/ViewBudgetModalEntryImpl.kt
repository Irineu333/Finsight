package com.neoutils.finsight.feature.budgets.modal.viewBudget

import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class ViewBudgetModalEntryImpl : ViewBudgetModalEntry {
    override fun create(budgetProgress: BudgetProgress): ModalBottomSheet =
        ViewBudgetModal(budgetProgress = budgetProgress)
}
