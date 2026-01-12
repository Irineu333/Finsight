package com.neoutils.finance.ui.modal.categoryForm

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.util.CategoryIcon

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
