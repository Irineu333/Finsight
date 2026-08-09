package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.AppIcon

sealed class AccountFormAction {

    data class NameChanged(
        val name: String
    ) : AccountFormAction()

    data class IsDefaultChanged(
        val isDefault: Boolean
    ) : AccountFormAction()

    data class YieldsInterestChanged(
        val yieldsInterest: Boolean
    ) : AccountFormAction()

    data class IconSelected(
        val icon: AppIcon,
    ) : AccountFormAction()

    /**
     * Only ever sent while creating: editing shows the currency as a locked state row,
     * and the domain refuses the change regardless (design D12).
     */
    data class CurrencySelected(
        val code: String,
    ) : AccountFormAction()

    data object Submit : AccountFormAction()
}
