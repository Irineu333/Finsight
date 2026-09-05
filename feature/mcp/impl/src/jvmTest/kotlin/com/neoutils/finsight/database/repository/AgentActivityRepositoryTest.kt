package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AgentActivityRetention
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AgentActivityEntity
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The activity log over a real database: what a recorded act keeps, what the declared retention
 * discards, and what happens on the read path of an app that was closed for months.
 */
class AgentActivityRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    /** A clock the test moves by hand — retention is about time passing, and it has to be stated. */
    private class MovableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val start = Instant.parse("2026-01-10T12:00:00Z")
    private val clock = MovableClock(start)

    private val dao = db.agentActivityDao()
    private val repository = AgentActivityRepository(
        dao = dao,
        mapper = AgentActivityMapper(),
        clock = clock,
    )

    /**
     * Writes straight to the table, at an instant the test chooses. Recording through the
     * repository would prune on the way in, which is exactly what the retention tests need to
     * set up *before* it runs.
     */
    private suspend fun store(at: Instant, operation: String = "create_transaction") = dao.insert(
        AgentActivityEntity(
            at = at,
            operation = operation,
            summary = "Feira de 50,00 na Carteira",
            outcome = AgentActivityEntity.Outcome.APPLIED,
        )
    )

    // --- What an act keeps ---

    @Test
    fun `a recorded act keeps when, which operation, what it was about and what it reached`() = runTest {
        repository.record(
            operation = "create_transaction",
            summary = "Feira de 50,00 na Carteira, em Mercado",
            outcome = AgentActivity.Outcome.APPLIED,
            reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 42),
        )

        val act = repository.observeRecent().first().single()

        assertEquals(start, act.at)
        assertEquals("create_transaction", act.operation)
        assertEquals("Feira de 50,00 na Carteira, em Mercado", act.summary)
        assertEquals(AgentActivity.Outcome.APPLIED, act.outcome)
        assertEquals(AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 42), act.reference)
        assertNull(act.detail, "an act that went through has nothing to explain")
    }

    /** A refusal is recorded so the user learns why the agent said it could not do something. */
    @Test
    fun `a refusal keeps the attempt and the reason`() = runTest {
        repository.record(
            operation = "delete_account",
            summary = "Apagar a conta Carteira",
            outcome = AgentActivity.Outcome.REFUSED,
            detail = "A conta tem lançamentos e só pode ser arquivada",
        )

        val act = repository.observeRecent().first().single()

        assertEquals(AgentActivity.Outcome.REFUSED, act.outcome)
        assertEquals("A conta tem lançamentos e só pode ser arquivada", act.detail)
        assertNull(act.reference, "nothing was written, so there is nothing to reach")
    }

    /**
     * The defence against the duplication that the absence of idempotency permits: an agent that
     * repeats a call it lost the answer to posts twice, and the log is where the twins meet.
     */
    @Test
    fun `the same act recorded twice appears twice, side by side`() = runTest {
        repository.record("create_transaction", "Feira de 50,00", AgentActivity.Outcome.APPLIED)
        clock.instant = start + 2.minutes
        repository.record("create_transaction", "Feira de 50,00", AgentActivity.Outcome.APPLIED)

        val acts = repository.observeRecent().first()

        assertEquals(2, acts.size)
        assertEquals(listOf(start + 2.minutes, start), acts.map { it.at }, "newest first")
    }

    @Test
    fun `the log is read newest first`() = runTest {
        store(start - 2.days, operation = "oldest")
        store(start - 1.days, operation = "middle")
        store(start, operation = "newest")

        assertEquals(
            listOf("newest", "middle", "oldest"),
            repository.observeAll().first().map { it.operation },
        )
    }

    // --- Retention: the log does not grow without bound ---

    @Test
    fun `an act older than the declared age is discarded`() = runTest {
        val kept = store(at = clock.instant - (AgentActivityRetention.MAX_AGE - 1.days))
        store(at = clock.instant - (AgentActivityRetention.MAX_AGE + 1.days))
        assertEquals(2, dao.count())

        dao.prune(clock.instant)

        assertEquals(listOf(kept), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `beyond the declared number of acts the oldest are discarded`() = runTest {
        // One past the ceiling, each a minute after the last, so "oldest" is unambiguous.
        val oldest = store(at = start)
        repeat(AgentActivityRetention.MAX_ENTRIES) { store(at = start + (it + 1).minutes) }
        assertEquals(AgentActivityRetention.MAX_ENTRIES + 1, dao.count())

        dao.prune(clock.instant)

        assertEquals(AgentActivityRetention.MAX_ENTRIES, dao.count())
        assertTrue(
            repository.observeAll().first().none { it.id == oldest },
            "the ceiling discards from the old end",
        )
    }

    /**
     * Writing is what makes the log grow, so writing is where the ceiling has to hold — without
     * anyone having opened the section.
     */
    @Test
    fun `recording an act applies the retention`() = runTest {
        store(at = clock.instant - (AgentActivityRetention.MAX_AGE + 1.days))

        repository.record("create_transaction", "Feira de 50,00", AgentActivity.Outcome.APPLIED)

        assertEquals(1, dao.count(), "the stale act went out with the new one coming in")
    }

    /**
     * The app closed for months: nothing writes, so the write path never runs. Reading is what
     * makes the declared age true again, and it does so before the first emission.
     */
    @Test
    fun `reading the log applies the retention, even with nothing written since`() = runTest {
        store(at = start)
        clock.instant = start + AgentActivityRetention.MAX_AGE + 1.days

        val acts = repository.observeRecent().first()

        assertEquals(emptyList(), acts)
        assertEquals(0, dao.count())
    }

    @Test
    fun `the recent read answers at most what it was asked for`() = runTest {
        repeat(5) { store(at = start + it.minutes) }

        assertEquals(3, repository.observeRecent(limit = 3).first().size)
        assertEquals(5, repository.observeAll().first().size)
    }

    /**
     * The log is a table and not a buffer, and this is why it had to be: a trace that dies with
     * the process is gone exactly when someone comes to investigate — the user notices the odd
     * figure the next morning, reopens the app, and there is nothing left to look at.
     *
     * A file, therefore, and not the in-memory database the rest of this class uses.
     */
    @Test
    fun `the trace survives the app being closed and opened again`() = runTest {
        val file = File.createTempFile("finsight-activity", ".db").also { it.delete() }
        try {
            openFileDatabase(file).let { first ->
                AgentActivityRepository(first.agentActivityDao(), AgentActivityMapper(), clock)
                    .record(
                        operation = "create_transaction",
                        summary = "Feira de 50,00 na Carteira",
                        outcome = AgentActivity.Outcome.APPLIED,
                        reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 7),
                    )
                first.close()
            }

            val reopened = openFileDatabase(file)
            val act = AgentActivityRepository(reopened.agentActivityDao(), AgentActivityMapper(), clock)
                .observeAll().first().single()

            assertEquals(start, act.at)
            assertEquals("create_transaction", act.operation)
            assertEquals(AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 7), act.reference)
            reopened.close()
        } finally {
            file.delete()
        }
    }

    private fun openFileDatabase(file: File): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
