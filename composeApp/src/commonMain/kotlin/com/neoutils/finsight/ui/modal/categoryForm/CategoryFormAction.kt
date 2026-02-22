package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.CategoryIcon

sealed class CategoryFormAction {

    data class NameChanged(
        val name: String
    ) : CategoryFormAction()

    data class TypeChanged(
        val type: Category.Type
    ) : CategoryFormAction()

    data class IconChanged(
        val icon: CategoryIcon
    ) : CategoryFormAction()

    data object Submit : CategoryFormAction()
}
