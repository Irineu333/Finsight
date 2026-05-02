package com.neoutils.finsight.ui.modal.creditCardOps

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

interface CloseInvoiceModalEntry {
    fun create(invoiceId: Long, closingDate: LocalDate): ModalBottomSheet
}

interface PayInvoiceModalEntry {
    fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet
}

interface AdvancePaymentModalEntry {
    fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet
}

interface EditInvoiceBalanceModalEntry {
    fun create(initialInvoice: Invoice): ModalBottomSheet
}
