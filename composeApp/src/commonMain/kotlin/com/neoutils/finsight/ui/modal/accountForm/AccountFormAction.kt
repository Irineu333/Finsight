package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.CategoryIcon

sealed class AccountFormAction {

    data class NameChanged(
        val name: String
    ) : AccountFormAction()

    data class IsDefaultChanged(
        val isDefault: Boolean
    ) : AccountFormAction()

    data class IconSelected(
        val icon: CategoryIcon,
    ) : AccountFormAction()

    data object Submit : AccountFormAction()
}
