package com.neoutils.finsight.ui.screen.categories

sealed class CategoriesAction {
    data object CreateDefaultCategories : CategoriesAction()
}
