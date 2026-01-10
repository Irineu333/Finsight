package com.neoutils.finance.ui.modal.addCategory

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.util.CategoryIcon

sealed class AddCategoryAction {

    data class SelectedType(
        val type: Category.Type
    ) : AddCategoryAction()

    data class IconChanged(
        val icon: CategoryIcon
    ) : AddCategoryAction()

    data class NameChanged(
        val name: String
    ) : AddCategoryAction()

    data object Submit : AddCategoryAction()
}