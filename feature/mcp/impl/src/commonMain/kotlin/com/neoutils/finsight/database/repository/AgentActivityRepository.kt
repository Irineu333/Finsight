package com.neoutils.finsight.database.repository

import com.neoutils.finsight.database.dao.AgentActivityDao
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.IAgentActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * The journal over its table — append, observe, prune, and nothing else.
 *
 * There is no update and no delete by id on purpose: a record says what was asked for at one
 * instant, and a journal whose entries can be edited answers no question worth asking. The
 * only removal is the retention policy, applied to everything older than a deadline.
 */
class AgentActivityRepository(
    private val dao: AgentActivityDao,
    private val mapper: AgentActivityMapper,
) : IAgentActivityRepository {

    /**
     * Room's own `Flow`, mapped — so a record appended while the activity screen is open
     * reaches it without the user acting and without a reload. Collecting a snapshot here and
     * re-querying would be the reload, only hidden.
     */
    override fun observeRecent(limit: Int): Flow<List<AgentActivity>> =
        dao.observeRecent(limit).map { entities -> entities.map(mapper::toDomain) }

    /** One row per tool call, whatever the outcome and however many lines the call wrote. */
    override suspend fun record(activity: AgentActivity) {
        dao.insert(mapper.toEntity(activity))
    }

    /**
     * Deleting from this table touches nothing else: no ledger row points at it, and the
     * transactions a removed record described stay exactly as they were.
     */
    override suspend fun prune(olderThan: Instant) = dao.prune(olderThan)
}
