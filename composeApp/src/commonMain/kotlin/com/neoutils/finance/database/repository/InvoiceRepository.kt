@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.InvoiceDao
import com.neoutils.finance.database.mapper.InvoiceMapper
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth

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
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    creditCard = creditCards[entity.creditCardId]!!
                )
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

    override suspend fun getAllInvoicesByCreditCard(creditCardId: Long): List<Invoice> {

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

    override fun observeLatestUnpaidInvoice(creditCardId: Long): Flow<Invoice?> {
        return combine(
            dao.observeLatestUnpaidInvoice(creditCardId),
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

    override fun observeLatestInvoice(creditCardId: Long): Flow<Invoice?> {
        return combine(
            dao.observeLatestInvoice(creditCardId),
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

    override suspend fun getAllInvoices(): List<Invoice> {

        val creditCards = creditCardRepository.getAllCreditCards().associateBy { it.id }

        return dao.getAllInvoices().map { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCards[entity.creditCardId]!!
            )
        }
    }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? {
        return dao.getOpenInvoice(
            creditCardId = creditCardId,
        )?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getLatestUnpaidInvoice(creditCardId: Long): Invoice? {
        return dao.getLatestUnpaidInvoice(creditCardId)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getLatestInvoice(creditCardId: Long): Invoice? {
        return dao.getLatestInvoice(creditCardId)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getInvoiceForMonth(creditCardId: Long, month: YearMonth): Invoice? {
        return dao.getInvoiceForMonth(creditCardId, month)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getByOpeningMonth(creditCardId: Long, openingMonth: YearMonth): Invoice? {
        return dao.getByOpeningMonth(creditCardId, openingMonth)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getByClosingMonth(creditCardId: Long, closingMonth: YearMonth): Invoice? {
        return dao.getByClosingMonth(creditCardId, closingMonth)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
    }

    override suspend fun getInvoiceById(id: Long): Invoice? {
        return dao.getById(id)?.let { entity ->
            mapper.toDomain(
                entity = entity,
                creditCard = creditCardRepository.getCreditCardById(entity.creditCardId)!!
            )
        }
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
