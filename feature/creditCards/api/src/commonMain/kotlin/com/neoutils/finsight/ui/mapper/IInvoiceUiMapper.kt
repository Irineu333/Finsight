package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.ui.model.InvoiceUi

interface IInvoiceUiMapper {
    suspend fun toUi(invoice: Invoice): InvoiceUi
}
