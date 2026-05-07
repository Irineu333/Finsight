package com.neoutils.finsight.feature.categories.entryPoint

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormModal
import com.neoutils.finsight.feature.categories.modal.categoryForm.CategoryFormModalEntry

class CategoryFormModalEntryImpl : CategoryFormModalEntry {
    override fun create(
        categoryId: Long?,
        initialType: Category.Type?,
    ): ModalBottomSheet = CategoryFormModal(
        categoryId = categoryId,
        initialType = initialType,
    )
}
