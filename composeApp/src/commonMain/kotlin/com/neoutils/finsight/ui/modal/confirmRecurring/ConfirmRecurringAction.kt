package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.LocalDate

sealed class ConfirmRecurringAction {
    data class DateChanged(val date: LocalDate) : ConfirmRecurringAction()
    data class InvoiceSelected(val invoice: Invoice) : ConfirmRecurringAction()
    data object Confirm : ConfirmRecurringAction()
    data object Skip : ConfirmRecurringAction()
}
