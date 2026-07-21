package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.RecurringDao
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
    private val dao: RecurringDao,
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

    override suspend fun delete(recurring: Recurring) {
        dao.delete(mapper.toEntity(recurring))
    }
}
