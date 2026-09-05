package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

interface IInvoiceRepository {
    fun observeAllInvoices(): Flow<List<Invoice>>
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>>
    fun observeInvoiceById(invoiceId: Long): Flow<Invoice?>
    fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>>
    fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeUnpaidInvoices(): Flow<List<Invoice>>

    /**
     * The invoices to settle by [month]: not paid, and due on [month] or earlier.
     *
     * Unlike [observeUnpaidInvoices], this read **includes `RETROACTIVE`** — a
     * retroactive invoice with a balance is overdue debt in the middle of being
     * regularised, and the money it holds is going to leave the account. That is a
     * **local exception of this read**, stated here so nobody has to infer it: the app
     * still disagrees with itself about `RETROACTIVE` elsewhere (`Invoice.Status.isPayable`
     * and `isEditable` treat it as debt while `observeUnpaidInvoices` treats it as
     * settled), and the issue `retroactive-invoice-debt-is-invisible-to-the-available-limit`
     * remains open. The criterion here is written as the negation of `PAID` precisely so
     * that it needs no list of statuses of its own and keeps its meaning the day a single
     * predicate exists.
     */
    fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>>
    suspend fun getAllInvoices(): List<Invoice>
    suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice>
    suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice>

    /**
     * The unpaid invoices of each card in [creditCardIds], keyed by card — the batched
     * [getUnpaidInvoicesByCreditCard] a surface answering about many cards needs. A card
     * with no unpaid invoice is absent from the map, and N cards cost one read, not N.
     */
    suspend fun getUnpaidInvoicesByCreditCards(
        creditCardIds: Collection<Long>,
    ): Map<Long, List<Invoice>>
    suspend fun getOpenInvoice(creditCardId: Long): Invoice?
    suspend fun getInvoiceById(id: Long): Invoice?
    /**
     * Returns the invoice as it was persisted — with the `id` and the `dimensionId`
     * only the repository knows. A caller that rebuilt the invoice from a bare id
     * would lose the dimension, and every leg it tagged would land on no invoice.
     */
    suspend fun insert(invoice: Invoice): Invoice
    suspend fun update(invoice: Invoice)
    suspend fun deleteById(id: Long)
}

