package com.neoutils.finance.ui.modal.editCreditCard

sealed class EditCreditCardAction {

    data class NameChanged(
        val name: String
    ) : EditCreditCardAction()

    data class LimitChanged(
        val limit: String
    ) : EditCreditCardAction()

    data class ClosingDayChanged(
        val closingDay: String
    ) : EditCreditCardAction()

    data class DueDayChanged(
        val dueDay: String
    ) : EditCreditCardAction()

    data object Submit : EditCreditCardAction()
}
