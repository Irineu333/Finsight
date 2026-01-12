package com.neoutils.finance.ui.modal.editCategory

import com.neoutils.finance.util.CategoryIcon

sealed class EditCategoryAction {

    data class NameChanged(
        val name: String
    ) : EditCategoryAction()

    data class IconChanged(
        val icon: CategoryIcon
    ) : EditCategoryAction()

    data object Submit : EditCategoryAction()
}
