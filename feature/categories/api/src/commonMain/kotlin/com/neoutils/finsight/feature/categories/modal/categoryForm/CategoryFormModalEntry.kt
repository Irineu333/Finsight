package com.neoutils.finsight.feature.categories.modal.categoryForm

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface CategoryFormModalEntry {
    fun create(
        categoryId: Long? = null,
        initialType: Category.Type? = null,
    ): ModalBottomSheet
}
