package com.neoutils.finsight.feature.categories.modal.categoryForm

import com.neoutils.finsight.feature.categories.model.form.CategoryForm
import com.neoutils.finsight.core.ui.util.Validation

sealed class CategoryFormUiState {
    data object Loading : CategoryFormUiState()

    data class Content(
        val form: CategoryForm,
        val validation: Map<CategoryField, Validation>,
        val isEditMode: Boolean,
        val canSubmit: Boolean,
    ) : CategoryFormUiState()
}

enum class CategoryField {
    NAME
}
