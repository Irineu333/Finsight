package com.neoutils.finsight.feature.categories.entryPoint

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModalEntry

class ViewCategoryModalEntryImpl : ViewCategoryModalEntry {
    override fun create(category: Category): ModalBottomSheet {
        return ViewCategoryModal(category = category)
    }
}