package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionTarget
import kotlinx.datetime.LocalDate

sealed class ConfirmRecurringAction {
    data class TargetSelected(val target: TransactionTarget) : ConfirmRecurringAction()
    data class AccountSelected(val account: Account?) : ConfirmRecurringAction()
    data class CreditCardSelected(val creditCard: CreditCard) : ConfirmRecurringAction()
    data class DateChanged(val date: LocalDate) : ConfirmRecurringAction()
    data class InvoiceSelected(val invoice: Invoice) : ConfirmRecurringAction()
    /** `null` clears the classification: no dimension is a state of its own, not an error. */
    data class CategorySelected(val category: Category?) : ConfirmRecurringAction()
    data class Confirm(val amount: String, val title: String) : ConfirmRecurringAction()
    data object Skip : ConfirmRecurringAction()
}
