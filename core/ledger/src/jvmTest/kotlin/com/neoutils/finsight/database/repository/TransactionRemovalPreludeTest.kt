package com.neoutils.finsight.database.repository

import androidx.room.useWriterConnection
import com.neoutils.finsight.database.LedgerDatabase
import com.neoutils.finsight.database.LedgerFixture
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.ledgerDatabase
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.posts
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The prelude's whole contract is *when* it is spoken, so that is what these pin.
 *
 * The probe is the statement the caller actually runs — `VACUUM INTO`, which SQLite
 * refuses from inside a transaction. Asserting merely that the prelude ran would pass
 * with the call moved below `useWriterConnection`, which is the one mistake that
 * matters and the one that never fails loudly: the copy is simply never taken, at the
 * moment a user destroys something they cannot retype.
 */
internal class TransactionRemovalPreludeTest {

    private lateinit var database: LedgerDatabase
    private lateinit var fixture: LedgerFixture
    private lateinit var scratch: File

    @BeforeTest
    fun setUp() {
        database = ledgerDatabase()
        fixture = LedgerFixture(database)
        scratch = File.createTempFile("ledger-prelude", ".db").also { it.delete() }
    }

    @AfterTest
    fun tearDown() {
        database.close()
        scratch.delete()
    }

    @Test
    fun `the prelude speaks outside the write transaction, with the rows still there`() = runTest {
        val id = seededTransaction()
        var copied: Result<Unit>? = null
        var rowStillThere: Boolean? = null

        repository(
            prelude = {
                rowStillThere = database.transactionDao().getById(id) != null
                copied = runCatching { copyDatabase() }
            },
        ).deleteTransactionById(id)

        assertEquals(true, rowStillThere, "the prelude ran after the row was already gone")
        assertNotNull(copied, "the prelude never ran").getOrThrow()
        assertTrue(scratch.length() > 0, "the copy taken by the prelude is empty")
        assertNull(database.transactionDao().getById(id), "the removal itself did not happen")
    }

    @Test
    fun `the same holds for a batch removal`() = runTest {
        val ids = listOf(seededTransaction(), seededTransaction())
        var copied: Result<Unit>? = null

        repository(prelude = { copied = runCatching { copyDatabase() } })
            .deleteTransactionsByIds(ids)

        assertNotNull(copied, "the prelude never ran").getOrThrow()
        ids.forEach { assertNull(database.transactionDao().getById(it)) }
    }

    @Test
    fun `a batch is one removal, so the prelude is spoken once`() = runTest {
        val ids = List(3) { seededTransaction() }
        var spoken = 0

        repository(prelude = { spoken++ }).deleteTransactionsByIds(ids)

        assertEquals(1, spoken)
    }

    @Test
    fun `a prelude that throws leaves the transaction where it was`() = runTest {
        val id = seededTransaction()
        val repository = repository(prelude = { error("not now") })

        assertFailsWith<IllegalStateException> { repository.deleteTransactionById(id) }

        assertNotNull(database.transactionDao().getById(id))
    }

    @OptIn(WithheldAnnouncement::class)
    @Test
    fun `a withheld announcement removes the same rows and says nothing`() = runTest {
        val single = seededTransaction()
        val batch = listOf(seededTransaction(), seededTransaction())
        var spoken = 0
        val repository = repository(prelude = { spoken++ })

        repository.deleteTransactionById(single, RemovalAnnouncement.Withheld)
        repository.deleteTransactionsByIds(batch, RemovalAnnouncement.Withheld)

        assertEquals(0, spoken, "the announcement was withheld and still spoken")
        (batch + single).forEach { assertNull(database.transactionDao().getById(it)) }
    }

    @Test
    fun `an announcement spelled out is the same removal as one left unsaid`() = runTest {
        val id = seededTransaction()
        var spoken = 0

        repository(prelude = { spoken++ })
            .deleteTransactionById(id, RemovalAnnouncement.Announced)

        assertEquals(1, spoken)
        assertNull(database.transactionDao().getById(id))
    }

    /**
     * The production statement, run from wherever the prelude is called from. Inside a
     * write transaction SQLite refuses it outright, so this both takes a real copy and
     * proves where the call sits.
     */
    private suspend fun copyDatabase() = database.useWriterConnection { connection ->
        connection.usePrepared("VACUUM INTO '${scratch.absolutePath}'") { it.step() }
        Unit
    }

    private suspend fun seededTransaction(): Long {
        if (database.accountDao().getAllLedgerAccounts().isEmpty()) {
            fixture.account(id = 1, type = AccountEntity.Type.ASSET)
            fixture.account(id = 2, type = AccountEntity.Type.EXPENSE)
        }
        return fixture.transaction("2026-03-10", 2L posts 5_000, 1L posts -5_000)
    }

    private fun repository(prelude: TransactionRemovalPrelude) = TransactionRepository(
        database = database,
        transactionDao = database.transactionDao(),
        entryDao = database.entryDao(),
        accountDao = database.accountDao(),
        writeGuard = DimensionWriteGuard.None,
        removalHook = TransactionRemovalHook.None,
        removalPrelude = prelude,
        transactionMapper = TransactionMapper(),
        ledgerEntryWriter = LedgerEntryWriter(
            database.entryDao(),
            database.accountDao(),
            database.dimensionDao(),
        ),
    )
}
