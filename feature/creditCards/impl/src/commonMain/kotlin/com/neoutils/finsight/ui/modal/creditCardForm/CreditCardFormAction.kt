package com.neoutils.finsight.ui.modal.creditCardForm

import com.neoutils.finsight.util.AppIcon

sealed class CreditCardFormAction {

    data class NameChanged(
        val name: String
    ) : CreditCardFormAction()

    data class LimitChanged(
        val limit: String
    ) : CreditCardFormAction()

    data class ClosingDayChanged(
        val closingDay: String
    ) : CreditCardFormAction()

    data class DueDayChanged(
        val dueDay: String
    ) : CreditCardFormAction()

    data class IconSelected(
        val icon: AppIcon
    ) : CreditCardFormAction()

    data object Submit : CreditCardFormAction()
}
