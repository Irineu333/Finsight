package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.flow.Flow

interface IInvoiceRepository {
    fun observeAllInvoices(): Flow<List<Invoice>>
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>>
    fun observeInvoiceById(invoiceId: Long): Flow<Invoice?>
    fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>>
    fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeUnpaidInvoices(): Flow<List<Invoice>>
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

