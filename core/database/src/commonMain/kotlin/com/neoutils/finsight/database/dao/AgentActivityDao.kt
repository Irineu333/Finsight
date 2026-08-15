@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.entity.AgentActivityEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Dao
interface AgentActivityDao {

    @Insert
    suspend fun insert(activity: AgentActivityEntity): Long

    /**
     * The most recent calls, newest first — a `Flow`, because the screen showing them
     * is open while an agent writes, and it must not need a reload to say so.
     */
    @Query("SELECT * FROM agent_activity ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AgentActivityEntity>>

    /**
     * The retention policy, applied: everything strictly older than [olderThan] goes.
     *
     * It is a requirement and not housekeeping. These rows hold the arguments as
     * received — whole statements, with descriptions, amounts and counterparties — so a
     * journal without an expiry is a second, perpetual copy of the user's financial
     * history. Deleting from here touches nothing else: no ledger row points at it.
     */
    @Query("DELETE FROM agent_activity WHERE timestamp < :olderThan")
    suspend fun prune(olderThan: Instant)
}
