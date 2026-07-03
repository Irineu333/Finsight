package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.model.InvoiceUi

interface InvoiceUiMapper {
    suspend fun toUi(invoice: Invoice): InvoiceUi
}
