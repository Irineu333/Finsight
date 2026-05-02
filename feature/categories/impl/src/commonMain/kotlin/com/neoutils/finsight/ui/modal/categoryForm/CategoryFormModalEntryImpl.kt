package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.ui.component.ModalBottomSheet

class CategoryFormModalEntryImpl : CategoryFormModalEntry {
    override fun create(
        category: Category?,
        initialType: Category.Type?,
    ): ModalBottomSheet = CategoryFormModal(
        category = category,
        initialType = initialType,
    )
}
