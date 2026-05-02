package com.neoutils.finsight.feature.categories.modal.categoryForm

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class CategoryFormModalEntryImpl : CategoryFormModalEntry {
    override fun create(
        category: Category?,
        initialType: Category.Type?,
    ): ModalBottomSheet = CategoryFormModal(
        category = category,
        initialType = initialType,
    )
}
