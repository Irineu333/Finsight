package com.neoutils.finance.ui.screen.categories

import com.neoutils.finance.data.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList()
)
