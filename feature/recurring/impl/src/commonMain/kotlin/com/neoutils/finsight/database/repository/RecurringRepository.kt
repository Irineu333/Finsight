package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RecurringRepository(
    private val database: AppDatabase,
    private val dao: RecurringDao,
    private val mapper: RecurringMapper,
    private val categoryRepository: ICategoryRepository,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
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

    override suspend fun hasTransactionForRecurring(recurringId: Long) =
        dao.existsTransactionFor(recurringId)

    override suspend fun insert(recurring: Recurring): Long =
        dao.insert(mapper.toEntity(recurring))

    /**
     * The template and its first cycle are one unit of work, for the reason spelled
     * out in [IRecurringRepository.createWithFirstCycle].
     *
     * The cycle itself is not written here: [IRecurringOccurrenceRepository.confirmCycle]
     * already owns "transaction plus occurrence, together", and it reenters this
     * connection as a `SAVEPOINT` rather than opening a second one. Its re-entry check
     * comes along — trivially satisfied by a template created a statement ago, and
     * defence in depth for free.
     *
     * The id the two writes need exists only after the insert, which is why they are
     * completed here and not by the caller. **Nothing on this path may switch
     * dispatchers**: three writer connections are nested in one coroutine, and the
     * reentrancy that makes that safe travels in the coroutine context.
     */
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = database.useWriterConnection { connection ->
        connection.immediateTransaction {
            val recurringId = dao.insert(mapper.toEntity(recurring))

            occurrenceRepository.confirmCycle(
                intent = firstCycle.copy(recurringId = recurringId),
                occurrence = occurrence.copy(recurringId = recurringId),
            )
        }
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
                dao.detachTransactions(recurring.id)
                dao.delete(mapper.toEntity(recurring))
            }
        }
    }
}
