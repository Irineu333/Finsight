package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface PayInvoiceModalEntry {
    fun create(invoiceId: Long): ModalBottomSheet
}
