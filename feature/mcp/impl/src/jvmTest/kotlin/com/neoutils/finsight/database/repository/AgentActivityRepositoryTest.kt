package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import com.neoutils.finsight.feature.mcp.api.AgentActivityOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The journal over a real database — one row per tool call, refusals included, and a
 * retention policy that takes records and nothing else.
 */
class AgentActivityRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val repository = AgentActivityRepository(
        dao = db.agentActivityDao(),
        mapper = AgentActivityMapper(),
    )

    @AfterTest
    fun tearDown() = db.close()

    private fun activity(
        timestamp: Instant = NOW,
        client: String? = "Claude Code 2.1",
        tool: String = "finsight_create_transaction",
        arguments: String = """{"title":"lunch","cents":-1250}""",
        outcome: AgentActivityOutcome = AgentActivityOutcome.OK,
        affected: List<String> = listOf("transaction:1"),
    ) = AgentActivity(
        timestamp = timestamp,
        client = client,
        tool = tool,
        arguments = arguments,
        outcome = outcome,
        affected = affected,
    )

    /** Thirty lines written, one record — naming all thirty. */
    @Test
    fun oneCallLeavesOneRecordHoweverManyLinesItWrote() = runTest {
        val affected = List(30) { "transaction:${it + 1}" }

        repository.record(activity(affected = affected))

        val recorded = repository.observeRecent(limit = 50).first()
        assertEquals(1, recorded.size)
        assertEquals(affected, recorded.single().affected)
        assertTrue(recorded.single().id != 0L, "the store assigns the id")
    }

    @Test
    fun aRefusedCallIsRecordedToo() = runTest {
        repository.record(
            activity(
                outcome = AgentActivityOutcome.REFUSED,
                affected = emptyList(),
            )
        )

        val recorded = repository.observeRecent(limit = 50).first().single()
        assertEquals(AgentActivityOutcome.REFUSED, recorded.outcome)
        assertEquals(emptyList(), recorded.affected)
    }

    @Test
    fun aMissingClientDoesNotMakeTheWriteFail() = runTest {
        repository.record(activity(client = null))

        val recorded = repository.observeRecent(limit = 50).first().single()
        assertNull(recorded.client)
        assertEquals("finsight_create_transaction", recorded.tool)
    }

    @Test
    fun theRecordsComeBackNewestFirstAndCappedAtTheLimit() = runTest {
        repository.record(activity(timestamp = NOW, affected = listOf("a")))
        repository.record(activity(timestamp = NOW + ONE_MINUTE, affected = listOf("b")))
        repository.record(activity(timestamp = NOW + TWO_MINUTES, affected = listOf("c")))

        val recorded = repository.observeRecent(limit = 2).first()

        assertEquals(listOf(listOf("c"), listOf("b")), recorded.map { it.affected })
    }

    /**
     * The point of the journal living beside the facades and carrying no foreign key: what
     * retention removes is the record, never the transaction the record described.
     */
    @Test
    fun pruningRemovesTheRecordWithoutTouchingTheTransactionsItDescribed() = runTest {
        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "lunch", date = LocalDate(2026, 8, 14)),
        )
        repository.record(
            activity(timestamp = NOW, affected = listOf("transaction:$transactionId")),
        )
        repository.record(
            activity(timestamp = NOW + TWO_MINUTES, affected = listOf("transaction:kept")),
        )

        repository.prune(olderThan = NOW + ONE_MINUTE)

        val recorded = repository.observeRecent(limit = 50).first()
        assertEquals(listOf(listOf("transaction:kept")), recorded.map { it.affected })

        val transaction = db.transactionDao().getById(transactionId)
        assertNotNull(transaction, "the transaction the pruned record described is intact")
        assertEquals("lunch", transaction.title)
        assertEquals(LocalDate(2026, 8, 14), transaction.date)
    }

    companion object {
        private val NOW = Instant.fromEpochMilliseconds(1_770_000_000_000)
        private val ONE_MINUTE = 1.minutes
        private val TWO_MINUTES = 2.minutes
    }
}
