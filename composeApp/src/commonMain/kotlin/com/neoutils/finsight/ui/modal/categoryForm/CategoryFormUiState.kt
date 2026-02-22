package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.util.Validation

data class CategoryFormUiState(
    val name: String = "",
    val validation: Map<CategoryField, Validation> = mapOf(),
    val selectedIcon: CategoryIcon = CategoryIcon.SHOPPING_CART,
    val selectedType: Category.Type = Category.Type.EXPENSE,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)

enum class CategoryField {
    NAME
}