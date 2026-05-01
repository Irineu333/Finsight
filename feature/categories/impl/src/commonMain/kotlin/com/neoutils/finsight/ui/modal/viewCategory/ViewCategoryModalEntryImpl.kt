package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.component.ModalBottomSheet

class ViewCategoryModalEntryImpl : ViewCategoryModalEntry {
    override fun create(category: Category): ModalBottomSheet =
        ViewCategoryModal(category = category)
}
