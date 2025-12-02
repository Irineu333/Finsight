package com.neoutils.finance.ui.screen.categories

import com.neoutils.finance.domain.model.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList()
)
