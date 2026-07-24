@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.DimensionDao
import com.neoutils.finsight.database.dao.InvoiceDao
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class InvoiceRepository(
    private val database: AppDatabase,
    private val dao: InvoiceDao,
    private val dimensionDao: DimensionDao,
    private val creditCardRepository: ICreditCardRepository,
    private val mapper: InvoiceMapper
) : IInvoiceRepository {

    // Resolving the card of a stored invoice, not offering a choice: closed cards
    // too. An archived card keeps its (paid) invoices, so the open-only list dropped
    // them here — silently in the observers, and with a NPE in getAllInvoices below.
    private val creditCardsFlow = creditCardRepository
        .observeAllCreditCardsIncludingClosed()
        .map { creditCards ->
            creditCards.associateBy { creditCard -> creditCard.id }
        }

    override fun observeAllInvoices(): Flow<List<Invoice>> {
        return combine(
            dao.observeAllInvoices(),
            creditCardsFlow,
        ) { entities, creditCards ->
            entities.mapNotNull { entity ->
                creditCards[entity.creditCardId]?.let { creditCard ->
                    mapper.toDomain(
                        entity = entity,
                        creditCard = creditCard,
                    )
                }
            }
        }
    }

    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> {
        return combine(
            dao.observeInvoicesByCreditCard(creditCardId),
            creditCardRepository.observeCreditCardById(creditCardId),
        ) { entities, creditCard ->
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    creditCard = creditCard!!
                )
            }
        }
    }

    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> {

        val creditCard = creditCardRepository.getCreditCardById(creditCardId)!!

        return dao.getAllInvoicesByCreditCard(
            creditCardId = creditCardId,
        ).map {
            mapper.toDomain(
                entity = it,
                creditCard = creditCard,
            )
        }
    }

    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> {
        return dao.observeInvoiceById(invoiceId).flatMapMerge { entity ->

            if (entity == null) {
                return@flatMapMerge emptyFlow()
            }

            creditCardRepository.observeCreditCardById(
                creditCardId = entity.creditCardId,
            ).map { creditCard ->
                mapper.toDomain(
                    entity = entity,
                    creditCard = creditCard!!
                )
            }
        }
    }

    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> {
        return combine(
            dao.observeOpenInvoice(creditCardId),
            creditCardRepository.observeCreditCardById(creditCardId),
        ) { entity, creditCard ->
            entity?.let {
                creditCard?.let {
                    mapper.toDomain(
                        entity = entity,
                        creditCard = creditCard
                    )
                }
            }
        }
    }

    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> {
        return combine(
            dao.observeAvailableInvoices(creditCardId),
            creditCardRepository.observeCreditCardById(creditCardId),
        ) { entities, creditCard ->
            if (creditCard == null) return@combine emptyList()
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    creditCard = creditCard
                )
            }
        }
    }

    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> {
        return combine(
            dao.observeUnpaidInvoice(creditCardId),
            creditCardRepository.observeCreditCardById(creditCardId),
        ) { entity, creditCard ->
            entity?.let {
                creditCard?.let {
                    mapper.toDomain(
                        entity = entity,
                        creditCard = creditCard
                    )
                }
            }
        }
    }

    override fun observeUnpaidInvoices(): Flow<List<Invoice>> {
        return combine(
            dao.observeUnpaidInvoices(),
            creditCardsFlow,
        ) { entities, creditCards ->
            entities.mapNotNull { entity ->
                creditCards[entity.creditCardId]?.let { creditCard ->
                    mapper.toDomain(
                        entity = entity,
                        creditCard = creditCard,
                    )
                }
            }
        }
    }

    override suspend fun getAllInvoices(): List<Invoice> {

        val creditCards = creditCardRepository.getAllCreditCardsIncludingClosed().associateBy { it.id }

        return dao.getAllInvoices().map { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCards[entity.creditCardId]!!
            )
        }
    }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId) ?: return emptyList()

        return dao.getUnpaidInvoicesByCreditCard(creditCardId).map { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCard
            )
        }
    }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId) ?: return null

        return dao.getOpenInvoice(creditCardId)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCard
            )
        }
    }

    override suspend fun getInvoiceById(id: Long): Invoice? {
        return observeInvoiceById(id).first()
    }

    /**
     * The invoice and the ledger identity its legs will carry are born together. A
     * row in `invoices` without a dimension could never be summed.
     */
    override suspend fun insert(invoice: Invoice): Long {
        return database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val dimensionId = dimensionDao.emit(DimensionKind.INVOICE)
                dao.insert(mapper.toEntity(invoice.copy(dimensionId = dimensionId)))
            }
        }
    }

    override suspend fun update(invoice: Invoice) {
        dao.update(mapper.toEntity(invoice))
    }

    /**
     * Removing the dimension is what detaches the legs that were tagged with it —
     * the `ON DELETE SET NULL` on `entries.dimensionId` does the rest. It is the
     * replacement for the cascade that used to come from `entries.invoiceId`, so it
     * has to happen in the same unit of work as the invoice's own removal.
     */
    override suspend fun deleteById(id: Long) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val dimensionId = dao.observeInvoiceById(id).first()?.dimensionId
                dao.deleteById(id)
                dimensionId?.let { dimensionDao.deleteById(it) }
            }
        }
    }
}
