package com.neoutils.finsight.feature.budgets.modal.viewBudget

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

class ViewBudgetModalEntryImpl : ViewBudgetModalEntry {
    override fun create(
        budgetId: Long,
    ): ModalBottomSheet = ViewBudgetModal(budgetId = budgetId)
}
