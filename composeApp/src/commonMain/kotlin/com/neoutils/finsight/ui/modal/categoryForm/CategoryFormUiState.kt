package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class CategoryFormUiState(
    val name: String = "",
    val validation: Map<CategoryField, Validation> = mapOf(),
    val selectedIcon: AppIcon = AppIcon.CATEGORY,
    val selectedType: Category.Type = Category.Type.EXPENSE,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)

enum class CategoryField {
    NAME
}
