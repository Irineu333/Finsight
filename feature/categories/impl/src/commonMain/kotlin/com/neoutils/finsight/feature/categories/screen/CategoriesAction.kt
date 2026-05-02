package com.neoutils.finsight.feature.categories.screen

import com.neoutils.finsight.core.domain.model.Category

sealed class CategoriesAction {
    data object CreateDefaultCategories : CategoriesAction()
    data class SelectType(val type: Category.Type) : CategoriesAction()
}
