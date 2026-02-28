package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.moneyToDouble

data class BudgetFormUiState(
    val availableCategories: List<Category> = emptyList(),
    val selectedCategories: List<Category> = emptyList(),
    val iconCategoryId: Long = 0,
    val title: String = "",
    val amount: String = "",
    val isEditMode: Boolean = false,
) {
    val iconCategory: Category?
        get() = selectedCategories.find { it.id == iconCategoryId } ?: selectedCategories.firstOrNull()

    val canSubmit: Boolean
        get() = title.isNotBlank() && selectedCategories.isNotEmpty() && amount.moneyToDouble() > 0
}
