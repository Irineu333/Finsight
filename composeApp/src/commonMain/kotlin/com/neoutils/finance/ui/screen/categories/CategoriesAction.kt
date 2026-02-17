package com.neoutils.finance.ui.screen.categories

sealed class CategoriesAction {
    data object CreateDefaultCategories : CategoriesAction()
}
