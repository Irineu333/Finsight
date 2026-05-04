package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface EditInvoiceBalanceModalEntry {
    fun create(initialInvoice: Invoice): ModalBottomSheet
}
