package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category

sealed class BudgetFormAction {
    data class TitleChanged(val title: String) : BudgetFormAction()
    data class CategoryToggled(val category: Category) : BudgetFormAction()
    data class AmountChanged(val amount: String) : BudgetFormAction()
    data class IconCategorySelected(val categoryId: Long) : BudgetFormAction()
    data object Submit : BudgetFormAction()
}
