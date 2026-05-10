package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface EditInvoiceBalanceModalEntry {
    fun create(invoiceId: Long): ModalBottomSheet
}
