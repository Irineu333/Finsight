package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.CategoryIcon

sealed class BudgetFormAction {
    data class TitleChanged(val title: String) : BudgetFormAction()
    data class CategoryToggled(val category: Category) : BudgetFormAction()
    data class AmountChanged(val amount: String) : BudgetFormAction()
    data class IconSelected(val icon: CategoryIcon) : BudgetFormAction()
    data object Submit : BudgetFormAction()
}
