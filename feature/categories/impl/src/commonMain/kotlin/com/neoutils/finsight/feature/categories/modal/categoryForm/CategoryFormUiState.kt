package com.neoutils.finsight.feature.categories.modal.categoryForm

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation

sealed class CategoryFormUiState {
    data object Loading : CategoryFormUiState()

    data class Content(
        val name: String,
        val validation: Map<CategoryField, Validation>,
        val selectedIcon: AppIcon,
        val selectedType: Category.Type,
        val isEditMode: Boolean,
        val canSubmit: Boolean,
    ) : CategoryFormUiState()
}

enum class CategoryField {
    NAME
}
