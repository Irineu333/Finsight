package com.neoutils.finsight.ui.modal.viewBudget

import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewBudgetModalEntry {
    fun create(budgetProgress: BudgetProgress): ModalBottomSheet
}
