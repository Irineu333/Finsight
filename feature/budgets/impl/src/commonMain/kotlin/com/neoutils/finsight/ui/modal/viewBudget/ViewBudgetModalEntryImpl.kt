package com.neoutils.finsight.ui.modal.viewBudget

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.ui.component.ModalBottomSheet

class ViewBudgetModalEntryImpl : ViewBudgetModalEntry {
    override fun create(budgetProgress: BudgetProgress): ModalBottomSheet =
        ViewBudgetModal(budgetProgress = budgetProgress)
}
