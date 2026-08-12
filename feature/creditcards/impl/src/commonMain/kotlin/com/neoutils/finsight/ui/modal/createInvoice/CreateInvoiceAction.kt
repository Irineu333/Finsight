package com.neoutils.finsight.ui.modal.createInvoice

import kotlinx.datetime.YearMonth

sealed class CreateInvoiceAction {
    data class SelectDueMonth(val dueMonth: YearMonth) : CreateInvoiceAction()
    data object Submit : CreateInvoiceAction()
}
