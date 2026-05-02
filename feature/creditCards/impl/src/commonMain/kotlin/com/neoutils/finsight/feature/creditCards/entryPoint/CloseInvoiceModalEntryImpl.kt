package com.neoutils.finsight.feature.creditCards.entryPoint

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import com.neoutils.finsight.feature.creditCards.modal.CloseInvoiceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.closeInvoice.CloseInvoiceModal
import kotlinx.datetime.LocalDate

class CloseInvoiceModalEntryImpl : CloseInvoiceModalEntry {
    override fun create(invoiceId: Long, closingDate: LocalDate): ModalBottomSheet =
        CloseInvoiceModal(invoiceId = invoiceId, closingDate = closingDate)
}