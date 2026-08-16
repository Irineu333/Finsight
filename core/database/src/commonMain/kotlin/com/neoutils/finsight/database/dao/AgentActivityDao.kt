@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.neoutils.finsight.database.AgentActivityRetention
import com.neoutils.finsight.database.entity.AgentActivityEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The agent activity log: rows go in, rows are read newest first, rows are discarded by the
 * retention policy or by the user, and nothing is ever updated.
 *
 * There is no read of the ledger here and no write to it. The log describes acts; the ledger
 * holds what those acts produced, and the two never consult each other.
 *
 * Every ordering is `at DESC, id DESC`. The tie-break is not decoration: two acts of the same
 * millisecond are exactly the case the log exists to show — a repeated call that posted twice
 * — and without a total order the pair could swap places between two readings of the same
 * table.
 */
@Dao
interface AgentActivityDao {

    /** The whole log, newest first — the full history the section offers access to. */
    @Query("SELECT * FROM agent_activity ORDER BY at DESC, id DESC")
    fun observeAll(): Flow<List<AgentActivityEntity>>

    /** The newest [limit] acts — the recent activity the section shows without being asked. */
    @Query("SELECT * FROM agent_activity ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AgentActivityEntity>>

    /**
     * Records an act. There is no update and no conflict strategy: a second execution of the
     * same call is a second act, and it is meant to appear beside the first.
     */
    @Insert
    suspend fun insert(activity: AgentActivityEntity): Long

    @Query("SELECT COUNT(*) FROM agent_activity")
    suspend fun count(): Int

    /**
     * Empties the log at the user's request.
     *
     * It names one table and touches nothing else, which is the whole of the guarantee: the
     * postings the discarded rows described stay where they are.
     */
    @Query("DELETE FROM agent_activity")
    suspend fun clear()

    /**
     * Applies [AgentActivityRetention] — the single implementation of the declared policy, and
     * the only way the log is trimmed other than by the user.
     *
     * The two deletes are independent and idempotent, so running them apart from one another
     * is harmless: whichever ceiling did not get its turn gets it on the next call.
     */
    suspend fun prune(now: Instant) {
        deleteOlderThan(now - AgentActivityRetention.MAX_AGE)
        deleteBeyondNewest(AgentActivityRetention.MAX_ENTRIES)
    }

    @Query("DELETE FROM agent_activity WHERE at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant)

    @Query(
        "DELETE FROM agent_activity WHERE id NOT IN (" +
            "SELECT id FROM agent_activity ORDER BY at DESC, id DESC LIMIT :keep" +
            ")"
    )
    suspend fun deleteBeyondNewest(keep: Int)
}
