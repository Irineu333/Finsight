package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface PayInvoiceModalEntry {
    fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet
}