package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.ui.component.ModalBottomSheet

interface ViewCategoryModalEntry {
    fun create(category: Category): ModalBottomSheet
}
