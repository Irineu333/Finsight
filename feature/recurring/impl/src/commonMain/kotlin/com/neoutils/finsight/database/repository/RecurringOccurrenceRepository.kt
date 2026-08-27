package com.neoutils.finsight.database.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.database.entity.RecurringOccurrenceEntity
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth

class RecurringOccurrenceRepository(
    private val database: AppDatabase,
    private val dao: RecurringOccurrenceDao,
    private val mapper: RecurringOccurrenceMapper,
    private val transactionRepository: ITransactionRepository,
) : IRecurringOccurrenceRepository {

    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> {
        return dao.observeAll().map { entities ->
            entities.map(mapper::toDomain)
        }
    }

    override suspend fun getAllOccurrences(): List<RecurringOccurrence> {
        return dao.getAll().map(mapper::toDomain)
    }

    override suspend fun getOccurrenceBy(
        recurringId: Long,
        yearMonth: YearMonth,
    ): RecurringOccurrence? {
        return dao.getByRecurringAndMonth(recurringId, yearMonth)?.let(mapper::toDomain)
    }

    override suspend fun getOccurrenceBy(
        recurringId: Long,
        cycleNumber: Int,
    ): RecurringOccurrence? {
        return dao.getByRecurringAndCycle(recurringId, cycleNumber)?.let(mapper::toDomain)
    }

    override suspend fun save(occurrence: RecurringOccurrence): Long {
        val existing = dao.getByRecurringAndMonth(occurrence.recurringId, occurrence.yearMonth)
        val entity = mapper.toEntity(
            occurrence.copy(id = existing?.id ?: occurrence.id)
        )

        return if (existing == null) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }
    }

    /**
     * The transaction and the occurrence that records it are one unit of work — in
     * the same spirit as `RecurringRepository.delete`, and for the reason spelled
     * out in [IRecurringOccurrenceRepository.confirmCycle].
     *
     * `createTransaction` opens its own writer transaction, and that is fine: the
     * Room pool is reentrant, so a nested call on the *same coroutine context*
     * reuses the confined connection and emits a `SAVEPOINT` instead of a `BEGIN`.
     * That reentrancy travels in a coroutine context element — nothing here may
     * switch dispatchers between the two writes, or the inner call would take a
     * second connection and deadlock.
     */
    override suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = database.useWriterConnection { connection ->
        connection.immediateTransaction {
            val existing = dao.getByRecurringAndMonth(occurrence.recurringId, occurrence.yearMonth)
            require(existing?.status != RecurringOccurrenceEntity.Status.CONFIRMED) {
                "Recurring already confirmed for ${occurrence.yearMonth}"
            }

            val transaction = transactionRepository.createTransaction(intent)
            save(occurrence.copy(transactionId = transaction.id))
            transaction
        }
    }

    /**
     * One grouped query, lifted out of cents by the ledger's own `toMoney` — the single
     * path [com.neoutils.finsight.database.dao.CurrencyScoped] exists for. Cents are the
     * ledger's storage convention, and a facade that divided by a hundred itself would
     * be keeping a copy of that convention with nothing to keep it in step.
     *
     * A month with no confirmed cycle returns no row, which is the honest answer — and
     * the empty figure it becomes is what lets the reducer decide, one layer up, what a
     * zero is denominated in.
     */
    override suspend fun settledIn(month: YearMonth): RecurringSettledMoney {
        val rows = dao.settledTotalsIn(month)
        return RecurringSettledMoney(
            expense = rows.toMoney { it.expense },
            income = rows.toMoney { it.income },
        )
    }
}
