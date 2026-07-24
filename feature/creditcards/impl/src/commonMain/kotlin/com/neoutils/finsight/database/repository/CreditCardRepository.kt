package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.CreditCardDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.domain.model.BASE_CURRENCY
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CreditCardRepository(
    private val database: AppDatabase,
    private val dao: CreditCardDao,
    private val accountDao: AccountDao,
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

    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> =
        dao.getAllCreditCardsIncludingClosed().map { mapper.toDomain(it) }

    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> =
        dao.observeAllCreditCardsIncludingClosed().map { rows -> rows.map { mapper.toDomain(it) } }

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? {
        return dao.getCreditCardWithArchivalById(creditCardId)?.let { mapper.toDomain(it) }
    }

    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> {
        return dao.observeCreditCardWithArchivalById(creditCardId).map { row ->
            row?.let { mapper.toDomain(it) }
        }
    }

    /** The card and its `LIABILITY` account are one creation — see `CategoryRepository`. */
    override suspend fun insert(creditCard: CreditCard): Long {
        return database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val accountId = accountDao.insert(
                    AccountEntity(
                        name = creditCard.name,
                        type = AccountEntity.Type.LIABILITY,
                        currency = BASE_CURRENCY,
                        iconKey = creditCard.iconKey,
                        createdAt = creditCard.createdAt,
                    )
                )
                dao.insert(mapper.toEntity(creditCard).copy(accountId = accountId))
            }
        }
    }

    override suspend fun update(creditCard: CreditCard) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.update(mapper.toEntity(creditCard))
                accountDao.getAccountById(creditCard.accountId)?.let { account ->
                    accountDao.update(
                        account.copy(name = creditCard.name, iconKey = creditCard.iconKey)
                    )
                }
            }
        }
    }

    /** Reopens the card's `LIABILITY` account — the inverse of archiving (design D3). */
    override suspend fun unarchive(accountId: Long) {
        accountDao.reopen(accountId)
    }

    /** Facade then account, in one transaction — see `CategoryRepository.delete`. */
    override suspend fun delete(creditCard: CreditCard) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                dao.delete(mapper.toEntity(creditCard))
                accountDao.getAccountById(creditCard.accountId)?.let { accountDao.delete(it) }
            }
        }
    }
}
