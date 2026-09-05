package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AgentActivityDao
import com.neoutils.finsight.database.entity.AgentActivityEntity
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * The activity log over the `agent_activity` table, and the place the declared retention is
 * triggered from.
 *
 * **Retention bites on both paths, and for different reasons.** On the way in, because writing
 * is the only thing that makes the log grow, so that is where a ceiling has to hold. On the way
 * out, because of the app that is closed for months: nothing writes, so nothing on the write
 * path ever runs, and the age ceiling would quietly stop being true — the user would open the
 * section and read acts the declared policy says are gone. Pruning once when the log is
 * collected is what makes what is shown obey what was declared.
 *
 * It runs once per collection and not per emission: it sits outside the DAO's `Flow`, so the
 * invalidation its own deletes cause re-runs the query and not the prune.
 *
 * The [Clock] is injected rather than read from the system at each call, so *when* an act
 * happened is the same decision the rest of the app makes, and a test can state it.
 */
class AgentActivityRepository(
    private val dao: AgentActivityDao,
    private val mapper: AgentActivityMapper,
    private val clock: Clock,
) : IAgentActivityRepository {

    override fun observeRecent(limit: Int): Flow<List<AgentActivity>> = pruned {
        dao.observeRecent(limit)
    }

    override fun observeAll(): Flow<List<AgentActivity>> = pruned {
        dao.observeAll()
    }

    override suspend fun record(
        operation: String,
        summary: String,
        outcome: AgentActivity.Outcome,
        detail: String?,
        reference: AgentActivity.Reference?,
    ): Long {
        val now = clock.now()
        val id = dao.insert(
            mapper.toEntity(
                AgentActivity(
                    at = now,
                    operation = operation,
                    summary = summary,
                    outcome = outcome,
                    detail = detail,
                    reference = reference,
                )
            )
        )
        dao.prune(now)
        return id
    }

    override suspend fun clear() = dao.clear()

    private fun pruned(
        source: () -> Flow<List<AgentActivityEntity>>,
    ): Flow<List<AgentActivity>> = flow {
        dao.prune(clock.now())
        emitAll(source().map { rows -> rows.map(mapper::toDomain) })
    }
}
