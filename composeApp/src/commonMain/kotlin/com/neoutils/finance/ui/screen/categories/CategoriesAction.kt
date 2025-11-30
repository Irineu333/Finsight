package com.neoutils.finance.ui.screen.categories

import com.neoutils.finance.data.Category

sealed interface CategoriesAction {
    data object AddCategory : CategoriesAction
    data class EditCategory(val category: Category) : CategoriesAction
    data class DeleteCategory(val category: Category) : CategoriesAction
}