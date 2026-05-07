package com.neoutils.finsight.feature.categories.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModalEntry

class ViewCategoryModalEntryImpl : ViewCategoryModalEntry {
    override fun create(categoryId: Long): ModalBottomSheet {
        return ViewCategoryModal(categoryId = categoryId)
    }
}
