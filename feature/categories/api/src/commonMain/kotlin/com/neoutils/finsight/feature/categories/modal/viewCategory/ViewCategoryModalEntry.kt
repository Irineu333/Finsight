package com.neoutils.finsight.feature.categories.modal.viewCategory

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface ViewCategoryModalEntry {
    fun create(categoryId: Long): ModalBottomSheet
}
