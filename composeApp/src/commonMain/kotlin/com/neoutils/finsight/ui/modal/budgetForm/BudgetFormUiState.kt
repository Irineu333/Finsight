package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.util.Validation

data class BudgetFormUiState(
    val availableCategories: List<Category> = emptyList(),
    val selectedCategories: List<Category> = emptyList(),
    val selectedIcon: CategoryIcon = CategoryIcon.DEFAULT,
    val title: String = "",
    val amount: String = "",
    val validation: Map<BudgetField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
) {
    val canSubmit: Boolean
        get() = validation[BudgetField.TITLE] == Validation.Valid &&
            selectedCategories.isNotEmpty() &&
            amount.moneyToDouble() > 0
}

enum class BudgetField {
    TITLE
}
