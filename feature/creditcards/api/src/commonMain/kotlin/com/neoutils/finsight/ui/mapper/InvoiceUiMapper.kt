package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.ui.model.InvoiceUi

interface InvoiceUiMapper {
    /**
     * [cardInvoices] are the card's invoices, needed to derive [InvoiceUi.canReopen] —
     * a relational rule (only the latest closed invoice reopens). Pass the full list
     * the caller already observes.
     *
     * [limit] arrives already read, and is not looked up here: a screen showing a list
     * of cards maps one invoice per card, and a lookup inside the mapper would be a
     * ledger read per card (design D7). The caller asks
     * `CalculateAvailableLimitUseCase` once for every card it is about to show and
     * hands each answer in; a card the batch had no answer for is [Limit.NONE].
     */
    suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>, limit: Limit): InvoiceUi
}
