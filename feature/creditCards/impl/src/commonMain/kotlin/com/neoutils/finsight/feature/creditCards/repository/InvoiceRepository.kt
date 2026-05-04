package com.neoutils.finsight.feature.creditCards.repository

import com.neoutils.finsight.core.database.dao.InvoiceDao
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.creditCards.mapper.InvoiceMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class InvoiceRepository(
    private val dao: InvoiceDao,
    private val mapper: InvoiceMapper
) : IInvoiceRepository {

    override fun observeAllInvoices(): Flow<List<Invoice>> {
        return dao.observeAllInvoices().map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> {
        return dao.observeInvoicesByCreditCard(creditCardId).map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
        return dao.getAllInvoicesByCreditCard(creditCardId).map(mapper::toDomain)
    }

    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> {
        return dao.observeInvoiceById(invoiceId).map { entity ->
            entity?.let(mapper::toDomain)
        }
    }

    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> {
        return dao.observeOpenInvoice(creditCardId).map { entity ->
            entity?.let(mapper::toDomain)
        }
    }

    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> {
        return dao.observeAvailableInvoices(creditCardId).map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> {
        return dao.observeUnpaidInvoice(creditCardId).map { entity ->
            entity?.let(mapper::toDomain)
        }
    }

    override fun observeUnpaidInvoices(): Flow<List<Invoice>> {
        return dao.observeUnpaidInvoices().map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun getAllInvoices(): List<Invoice> {
        return dao.getAllInvoices().map(mapper::toDomain)
    }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
        return dao.getUnpaidInvoicesByCreditCard(creditCardId).map(mapper::toDomain)
    }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? {
        return dao.getOpenInvoice(creditCardId)?.let(mapper::toDomain)
    }

    override suspend fun getInvoiceById(id: Long): Invoice? {
        return dao.observeInvoiceById(id).first()?.let(mapper::toDomain)
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
