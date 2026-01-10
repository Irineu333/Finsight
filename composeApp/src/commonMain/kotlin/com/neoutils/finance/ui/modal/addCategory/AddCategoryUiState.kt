package com.neoutils.finance.ui.modal.addCategory

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.util.FieldForm

data class AddCategoryUiState(
    val name: FieldForm = FieldForm(),
    val selectedIcon: CategoryIcon = CategoryIcon.SHOPPING_CART,
    val selectedType: Category.Type = Category.Type.EXPENSE,
    val canSubmit: Boolean = false,
)