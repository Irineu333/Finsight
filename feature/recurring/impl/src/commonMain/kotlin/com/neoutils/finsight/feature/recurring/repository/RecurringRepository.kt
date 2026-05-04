package com.neoutils.finsight.feature.recurring.repository

import com.neoutils.finsight.core.database.dao.RecurringDao
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.mapper.RecurringMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurringRepository(
    private val dao: RecurringDao,
    private val mapper: RecurringMapper,
) : IRecurringRepository {

    override fun observeAllRecurring(): Flow<List<Recurring>> {
        return dao.observeAll().map { entities ->
            entities.map(mapper::toDomain)
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
