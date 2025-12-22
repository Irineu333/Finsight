package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.InvoiceDao
import com.neoutils.finance.database.mapper.InvoiceMapper
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth

class InvoiceRepository(
    private val dao: InvoiceDao,
    private val mapper: InvoiceMapper
) : IInvoiceRepository {

    override fun observeAllInvoices(): Flow<List<Invoice>> {
        return dao.observeAllInvoices().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> {
        return dao.observeInvoicesByCreditCard(creditCardId).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getAllInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
        return dao.getAllInvoicesByCreditCard(creditCardId).map { mapper.toDomain(it) }
    }

    override fun observeById(id: Long): Flow<Invoice?> {
        return dao.observeById(id).map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun getAllInvoices(): List<Invoice> {
        return dao.getAllInvoices().map { mapper.toDomain(it) }
    }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? {
        return dao.getOpenInvoice(creditCardId)?.let { mapper.toDomain(it) }
    }

    override suspend fun getLatestUnpaidInvoice(creditCardId: Long): Invoice? {
        return dao.getLatestUnpaidInvoice(creditCardId)?.let { mapper.toDomain(it) }
    }

    override suspend fun getInvoiceForMonth(creditCardId: Long, month: YearMonth): Invoice? {
        return dao.getInvoiceForMonth(creditCardId, month)?.let { mapper.toDomain(it) }
    }

    override suspend fun getByOpeningMonth(creditCardId: Long, openingMonth: YearMonth): Invoice? {
        return dao.getByOpeningMonth(creditCardId, openingMonth)?.let { mapper.toDomain(it) }
    }

    override suspend fun getByClosingMonth(creditCardId: Long, closingMonth: YearMonth): Invoice? {
        return dao.getByClosingMonth(creditCardId, closingMonth)?.let { mapper.toDomain(it) }
    }

    override suspend fun getInvoiceById(id: Long): Invoice? {
        return dao.getById(id)?.let { mapper.toDomain(it) }
    }

    override suspend fun insert(invoice: Invoice): Long {
        return dao.insert(mapper.toEntity(invoice))
    }

    override suspend fun update(invoice: Invoice) {
        dao.update(mapper.toEntity(invoice))
    }

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
