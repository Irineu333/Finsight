package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.AppIcon

sealed class AccountFormAction {

    data class NameChanged(
        val name: String
    ) : AccountFormAction()

    data class IsDefaultChanged(
        val isDefault: Boolean
    ) : AccountFormAction()

    data class IconSelected(
        val icon: AppIcon,
    ) : AccountFormAction()

    /** Only ever reachable in creation: editing has no control that emits it (design D12). */
    data class CurrencySelected(
        val currency: String,
    ) : AccountFormAction()

    data object Submit : AccountFormAction()
}
