package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.creditCards.modal.PayInvoiceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.payInvoice.PayInvoiceModal

class PayInvoiceModalEntryImpl : PayInvoiceModalEntry {
    override fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet =
        PayInvoiceModal(invoice = invoice, currentBillAmount = currentBillAmount)
}