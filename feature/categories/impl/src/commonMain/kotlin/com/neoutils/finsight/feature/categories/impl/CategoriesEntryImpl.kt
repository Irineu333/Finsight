package com.neoutils.finsight.feature.categories.impl

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.feature.categories.api.CategoriesEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryModal

internal class CategoriesEntryImpl : CategoriesEntry {
    override fun categoryFormModal(category: Category?, initialType: Category.Type?): Modal =
        CategoryFormModal(category, initialType)

    override fun viewCategoryModal(category: Category): Modal =
        ViewCategoryModal(category)
}
