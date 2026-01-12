package com.neoutils.finance.ui.modal.categoryForm

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.util.FieldForm

data class CategoryFormUiState(
    val name: FieldForm = FieldForm(),
    val selectedIcon: CategoryIcon = CategoryIcon.SHOPPING_CART,
    val selectedType: Category.Type = Category.Type.EXPENSE,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)
