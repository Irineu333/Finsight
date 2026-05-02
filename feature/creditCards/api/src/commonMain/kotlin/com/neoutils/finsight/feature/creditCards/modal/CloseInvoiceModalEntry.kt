package com.neoutils.finsight.feature.creditCards.modal

import com.neoutils.finsight.core.ui.component.ModalBottomSheet
import kotlinx.datetime.LocalDate

interface CloseInvoiceModalEntry {
    fun create(invoiceId: Long, closingDate: LocalDate): ModalBottomSheet
}