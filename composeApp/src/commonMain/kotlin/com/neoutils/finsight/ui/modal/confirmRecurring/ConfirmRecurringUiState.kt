package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import kotlinx.datetime.LocalDate

data class ConfirmRecurringUiState(
    val recurring: Recurring,
    val confirmDate: LocalDate,
    val invoices: List<Invoice> = emptyList(),
    val selectedInvoice: Invoice? = null,
)
