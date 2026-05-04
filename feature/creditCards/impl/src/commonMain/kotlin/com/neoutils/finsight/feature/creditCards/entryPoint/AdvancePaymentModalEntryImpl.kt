package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.creditCards.modal.AdvancePaymentModalEntry
import com.neoutils.finsight.feature.creditCards.modal.advancePayment.AdvancePaymentModal

class AdvancePaymentModalEntryImpl : AdvancePaymentModalEntry {
    override fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet =
        AdvancePaymentModal(invoice = invoice, currentBillAmount = currentBillAmount)
}