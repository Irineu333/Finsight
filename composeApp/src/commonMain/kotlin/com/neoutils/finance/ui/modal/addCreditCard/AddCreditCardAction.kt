package com.neoutils.finance.ui.modal.addCreditCard

sealed class AddCreditCardAction {

    data class NameChanged(
        val name: String
    ) : AddCreditCardAction()

    data class LimitChanged(
        val limit: String
    ) : AddCreditCardAction()

    data class ClosingDayChanged(
        val closingDay: String
    ) : AddCreditCardAction()

    data class DueDayChanged(
        val dueDay: String
    ) : AddCreditCardAction()

    data object Submit : AddCreditCardAction()
}
