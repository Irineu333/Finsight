package com.neoutils.finsight.feature.categories.modal.viewCategory

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
interface ViewCategoryModalEntry {
    fun create(category: Category): ModalBottomSheet
}
