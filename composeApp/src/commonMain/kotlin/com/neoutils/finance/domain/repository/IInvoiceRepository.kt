package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Invoice
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

interface IInvoiceRepository {
    fun observeAllInvoices(): Flow<List<Invoice>>
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>>
    fun observeInvoiceById(invoiceId: Long): Flow<Invoice?>
    fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeLatestUnpaidInvoice(creditCardId: Long): Flow<Invoice?>
    fun observeLatestInvoice(creditCardId: Long): Flow<Invoice?>
    suspend fun getAllInvoices(): List<Invoice>
    suspend fun getAllInvoicesByCreditCard(creditCardId: Long): List<Invoice>
    suspend fun getOpenInvoice(creditCardId: Long): Invoice?
    suspend fun getLatestUnpaidInvoice(creditCardId: Long): Invoice?
    suspend fun getLatestInvoice(creditCardId: Long) : Invoice?
    suspend fun getInvoiceForMonth(creditCardId: Long, month: YearMonth): Invoice?
    suspend fun getByOpeningMonth(creditCardId: Long, openingMonth: YearMonth): Invoice?
    suspend fun getByClosingMonth(creditCardId: Long, closingMonth: YearMonth): Invoice?
    suspend fun getInvoiceById(id: Long): Invoice?
    suspend fun insert(invoice: Invoice): Long
    suspend fun update(invoice: Invoice)
    suspend fun deleteById(id: Long)
}

