package com.neoutils.finance.domain.repository

import com.neoutils.finance.domain.model.Invoice
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

interface IInvoiceRepository {
    fun observeAllInvoices(): Flow<List<Invoice>>
    fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>>
    fun observeById(id: Long): Flow<Invoice?>
    suspend fun getOpenInvoice(creditCardId: Long): Invoice?
    suspend fun getLatestUnpaidInvoice(creditCardId: Long): Invoice?
    suspend fun getInvoiceForMonth(creditCardId: Long, month: YearMonth): Invoice?
    suspend fun getByOpeningMonth(creditCardId: Long, openingMonth: YearMonth): Invoice?
    suspend fun getByClosingMonth(creditCardId: Long, closingMonth: YearMonth): Invoice?
    suspend fun getById(id: Long): Invoice?
    suspend fun insert(invoice: Invoice): Long
    suspend fun update(invoice: Invoice)
    suspend fun deleteById(id: Long)
}

