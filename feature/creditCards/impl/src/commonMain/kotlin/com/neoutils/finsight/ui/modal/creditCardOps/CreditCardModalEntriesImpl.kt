package com.neoutils.finsight.ui.modal.creditCardOps

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.component.ModalBottomSheet
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentModal
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceModal
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceModal
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceModal
import kotlinx.datetime.LocalDate

class CloseInvoiceModalEntryImpl : CloseInvoiceModalEntry {
    override fun create(invoiceId: Long, closingDate: LocalDate): ModalBottomSheet =
        CloseInvoiceModal(invoiceId = invoiceId, closingDate = closingDate)
}

class PayInvoiceModalEntryImpl : PayInvoiceModalEntry {
    override fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet =
        PayInvoiceModal(invoice = invoice, currentBillAmount = currentBillAmount)
}

class AdvancePaymentModalEntryImpl : AdvancePaymentModalEntry {
    override fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet =
        AdvancePaymentModal(invoice = invoice, currentBillAmount = currentBillAmount)
}

class EditInvoiceBalanceModalEntryImpl : EditInvoiceBalanceModalEntry {
    override fun create(initialInvoice: Invoice): ModalBottomSheet =
        EditInvoiceBalanceModal(initialInvoice = initialInvoice)
}
