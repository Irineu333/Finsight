package com.neoutils.finsight.feature.creditCards.mapper

import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.feature.creditCards.model.InvoiceUi
interface IInvoiceUiMapper {
    suspend fun toUi(invoice: Invoice): InvoiceUi
}
