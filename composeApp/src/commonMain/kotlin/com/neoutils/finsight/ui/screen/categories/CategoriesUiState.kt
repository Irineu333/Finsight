package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.domain.model.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
)
