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

class RecurringRepository(
    private val dao: RecurringDao,
    private val mapper: RecurringMapper,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
) : IRecurringRepository {

    override fun observeAllRecurring(): Flow<List<Recurring>> {
        return combine(
            dao.observeAll(),
            categoryRepository.observeAllCategories(),
            accountRepository.observeAllAccounts(),
            creditCardRepository.observeAllCreditCards(),
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
