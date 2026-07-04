package com.neoutils.finsight.feature.categories.api

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.component.Modal

interface CategoriesEntry {
    fun categoryFormModal(category: Category? = null, initialType: Category.Type? = null): Modal
    fun viewCategoryModal(category: Category): Modal
}
