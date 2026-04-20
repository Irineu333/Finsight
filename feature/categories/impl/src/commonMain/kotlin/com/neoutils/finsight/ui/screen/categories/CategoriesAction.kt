package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.domain.model.Category

sealed class CategoriesAction {
    data object CreateDefaultCategories : CategoriesAction()
    data class SelectType(val type: Category.Type) : CategoriesAction()
}
