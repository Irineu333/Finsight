package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RecurringRepository(
    private val database: AppDatabase,
    private val dao: RecurringDao,
    private val transactionDao: TransactionDao,
    private val mapper: RecurringMapper,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) : IRecurringRepository {

    /**
     * Hydrated from the *including closed* lookups, not the active facades: a
     * recurring on an archived account still knows its account, and hiding that
     * would read as if the link had been erased. The closure travels with the
     * model, so consumers can render it as retired and refuse to post to it.
     */
    override fun observeAllRecurring(): Flow<List<Recurring>> {
        return combine(
            dao.observeAll(),
            categoryRepository.observeAllCategoriesIncludingClosed(),
            accountRepository.observeAllAccountsIncludingClosed(),
            creditCardRepository.observeAllCreditCardsIncludingClosed(),
        ) { entities, categories, accounts, creditCards ->
            val categoryMap = categories.associateBy { it.id }
            val accountMap = accounts.associateBy { it.id }
            val creditCardMap = creditCards.associateBy { it.id }
            entities.map { entity ->
                mapper.toDomain(
                    entity = entity,
                    category = entity.categoryId?.let { categoryMap[it] },
                    account = entity.accountId?.let { accountMap[it] },
                    creditCard = entity.creditCardId?.let { creditCardMap[it] },
                )
            }
        }
    }

    override suspend fun getRecurringById(id: Long): Recurring? {
        val entity = dao.getAll().firstOrNull { it.id == id } ?: return null
        return mapper.toDomain(
            entity = entity,
            category = entity.categoryId?.let { categoryRepository.getCategoryById(it) },
            account = entity.accountId?.let { accountRepository.getAccountById(it) },
            creditCard = entity.creditCardId?.let { creditCardRepository.getCreditCardById(it) },
        )
    }

    override fun observeRecurringById(id: Long): Flow<Recurring?> {
        return observeAllRecurring()
            .map { list -> list.firstOrNull { it.id == id } }
            // Derived from the full list, so it re-runs on any recurring/lookup change; only notify
            // consumers when the target actually changed.
            .distinctUntilChanged()
    }

    override suspend fun hasRecurringForAccount(accountId: Long) =
        dao.countByAccount(accountId) > 0

    override suspend fun hasRecurringForCreditCard(creditCardId: Long) =
        dao.countByCreditCard(creditCardId) > 0

    override suspend fun hasRecurringForCategory(categoryId: Long) =
        dao.countByCategory(categoryId) > 0

    override suspend fun insert(recurring: Recurring) {
        dao.insert(mapper.toEntity(recurring))
    }

    override suspend fun update(recurring: Recurring) {
        dao.update(mapper.toEntity(recurring))
    }

    /**
     * Removing the recurring and detaching the transactions it generated are one unit
     * of work: a transaction left naming a recurring that no longer exists would
     * render as an occurrence of nothing (design D12).
     */
    override suspend fun delete(recurring: Recurring) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                transactionDao.detachFromRecurring(recurring.id)
                dao.delete(mapper.toEntity(recurring))
            }
        }
    }
}
