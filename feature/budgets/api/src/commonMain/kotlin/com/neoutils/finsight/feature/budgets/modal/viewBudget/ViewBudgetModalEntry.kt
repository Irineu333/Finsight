package com.neoutils.finsight.feature.budgets.modal.viewBudget

import com.neoutils.finsight.feature.budgets.model.BudgetProgress
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewBudgetModalEntry {
    fun create(budgetProgress: BudgetProgress): ModalBottomSheet
}
