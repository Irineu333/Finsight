package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.ui.component.ModalBottomSheet

interface EditInvoiceBalanceModalEntry {
    fun create(initialInvoice: Invoice): ModalBottomSheet
}
