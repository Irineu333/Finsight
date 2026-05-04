package com.neoutils.finsight.feature.categories.modal.categoryForm

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.core.ui.util.AppIcon

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
