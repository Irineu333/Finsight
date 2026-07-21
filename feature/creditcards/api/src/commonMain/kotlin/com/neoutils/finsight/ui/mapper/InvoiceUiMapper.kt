package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.model.InvoiceUi

interface InvoiceUiMapper {
    /**
     * [cardInvoices] are the card's invoices, needed to derive [InvoiceUi.canReopen] —
     * a relational rule (only the latest closed invoice reopens). Pass the full list
     * the caller already observes.
     */
    suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi
}
