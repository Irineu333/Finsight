package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth

class RecurringOccurrenceRepository(
    private val dao: RecurringOccurrenceDao,
    private val mapper: RecurringOccurrenceMapper,
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
}
