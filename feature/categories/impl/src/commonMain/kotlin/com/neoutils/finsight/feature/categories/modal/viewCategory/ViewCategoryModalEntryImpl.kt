package com.neoutils.finsight.feature.categories.modal.viewCategory

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
class ViewCategoryModalEntryImpl : ViewCategoryModalEntry {
    override fun create(category: Category): ModalBottomSheet =
        ViewCategoryModal(category = category)
}
