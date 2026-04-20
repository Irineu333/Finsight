package com.neoutils.finsight.ui.modal.categoryForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.util.AppIcon

sealed class CategoryFormAction {

    data class NameChanged(
        val name: String
    ) : CategoryFormAction()

    data class TypeChanged(
        val type: Category.Type
    ) : CategoryFormAction()

    data class IconChanged(
        val icon: AppIcon
    ) : CategoryFormAction()

    data object Submit : CategoryFormAction()
}
