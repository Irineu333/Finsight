package com.neoutils.finsight.ui.screen.categories

sealed class CategoriesAction {
    data object CreateDefaultCategories : CategoriesAction()
    data class SelectFilter(val filter: CategoryFilter) : CategoriesAction()
}
