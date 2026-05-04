package com.neoutils.finsight.feature.categories.modal.viewCategory

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface ViewCategoryModalEntry {
    fun create(category: Category): ModalBottomSheet
}
