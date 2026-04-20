@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.InvoiceDao
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class InvoiceRepository(
    private val dao: InvoiceDao,
    private val creditCardRepository: ICreditCardRepository,
    private val mapper: InvoiceMapper
) : IInvoiceRepository {

    private val creditCardsFlow = creditCardRepository
        .observeAllCreditCards()
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

        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }

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
