package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface AdvancePaymentModalEntry {
    fun create(invoice: Invoice, currentBillAmount: Double): ModalBottomSheet
}