package com.neoutils.finance.database.repository

import com.neoutils.finance.database.dao.CreditCardDao
import com.neoutils.finance.database.mapper.CreditCardMapper
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CreditCardRepository(
    private val dao: CreditCardDao,
    private val mapper: CreditCardMapper
) : ICreditCardRepository {

    override fun observeAllCreditCards(): Flow<List<CreditCard>> {
        return dao.observeAllCreditCards().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun getAllCreditCards(): List<CreditCard> {
        return dao.getAllCreditCardsList().map { mapper.toDomain(it) }
    }

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? {
        return dao.getCreditCardById(creditCardId)?.let { mapper.toDomain(it) }
    }

    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> {
        return dao.observeCreditCardById(creditCardId).map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun insert(creditCard: CreditCard): Long {
        return dao.insert(mapper.toEntity(creditCard))
    }

    override suspend fun update(creditCard: CreditCard) {
        dao.update(mapper.toEntity(creditCard))
    }

    override suspend fun delete(creditCard: CreditCard) {
        dao.delete(mapper.toEntity(creditCard))
    }
}
