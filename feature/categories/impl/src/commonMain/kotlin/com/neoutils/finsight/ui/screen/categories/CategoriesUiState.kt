package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.domain.model.Category

sealed class CategoriesUiState {

    data object Loading : CategoriesUiState()

    data class Empty(
        val selectedType: Category.Type = Category.Type.EXPENSE,
    ) : CategoriesUiState()

    data class Content(
        val categories: List<Category>,
        val selectedType: Category.Type,
    ) : CategoriesUiState()
}
