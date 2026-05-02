package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.datetime.LocalDate

sealed class ConfirmRecurringAction {
    data class TargetSelected(val target: Transaction.Target) : ConfirmRecurringAction()
    data class AccountSelected(val account: Account?) : ConfirmRecurringAction()
    data class CreditCardSelected(val creditCard: CreditCard) : ConfirmRecurringAction()
    data class DateChanged(val date: LocalDate) : ConfirmRecurringAction()
    data class InvoiceSelected(val invoice: Invoice) : ConfirmRecurringAction()
    data class Confirm(val amount: String) : ConfirmRecurringAction()
    data object Skip : ConfirmRecurringAction()
}
