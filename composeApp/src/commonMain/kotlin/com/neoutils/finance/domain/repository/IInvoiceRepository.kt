package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Invoice
import kotlinx.coroutines.flow.Flow

interface IInvoiceRepository {
    fun observeAllInvoices(): Flow<List<Invoice>>
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>>
    fun observeInvoiceById(invoiceId: Long): Flow<Invoice?>
    fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeUnpaidInvoices(): Flow<List<Invoice>>
    suspend fun getAllInvoices(): List<Invoice>
    suspend fun getAllInvoicesByCreditCard(creditCardId: Long): List<Invoice>
    suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice>
    suspend fun getInvoiceById(id: Long): Invoice?
    suspend fun insert(invoice: Invoice): Long
    suspend fun update(invoice: Invoice)
    suspend fun deleteById(id: Long)
}

