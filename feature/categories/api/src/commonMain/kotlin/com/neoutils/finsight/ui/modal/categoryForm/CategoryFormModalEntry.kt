package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface CategoryFormModalEntry {
    fun create(
        category: Category? = null,
        initialType: Category.Type? = null,
    ): ModalBottomSheet
}
