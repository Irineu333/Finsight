package com.neoutils.finsight.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * **Asking about many identities at once is bounded by what SQLite will bind, not by the caller.**
 *
 * Room writes one host parameter per element of an `IN (:ids)` list and chunks nothing, so a read
 * whose list is as long as the history it reads over stops working at a size the driver decides.
 * These fix both halves of the answer: the ask is answered whole however wide it is, and every
 * identity in it is asked exactly once — nothing counted twice at a chunk boundary, nothing
 * falling between two of them.
 */
class TransactionIdentityReadTest {

    private lateinit var database: LedgerDatabase

    @BeforeTest
    fun setUp() = runTest {
        database = ledgerDatabase()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                (1L..EXISTING).forEach { id ->
                    database.transactionDao().insert(
                        TransactionEntity(id = id, title = null, date = LocalDate(2026, 1, 1))
                    )
                }
            }
        }
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `an ask far wider than SQLite can bind is answered`() = runTest {
        val alive = repository().getExistingTransactionIds((1L..ASKED).toList())

        assertEquals(
            (1L..EXISTING).toSet(),
            alive,
            "the transactions that are still there were not the ones reported alive",
        )
    }

    @Test
    fun `a read wider than one chunk answers every identity exactly once`() = runTest {
        val answered = (1L..ASKED).toList().readByIdentity(database.transactionDao()::getExistingIds)

        assertEquals(
            EXISTING.toInt(),
            answered.size,
            "$ASKED identities over ${ASKED / MAX_BOUND_IDENTITIES + 1} chunks answered " +
                "${answered.size} rows where ${EXISTING.toInt()} exist, so a chunk boundary either " +
                "repeated a row or lost one",
        )
        assertEquals((1L..EXISTING).toSet(), answered.toSet())
    }

    /**
     * The ceiling the chunking exists for, stated as a fact about the driver rather than a number
     * copied from documentation: the same ask, in one query, is refused.
     */
    @Test
    fun `the driver refuses an ask this wide in a single query`() = runTest {
        assertFailsWith<SQLiteException> {
            database.transactionDao().getExistingIds((1L..ASKED).toList())
        }
    }

    private fun repository() = TransactionRepository(
        database = database,
        transactionDao = database.transactionDao(),
        entryDao = database.entryDao(),
        accountDao = database.accountDao(),
        writeGuard = DimensionWriteGuard.None,
        removalHook = TransactionRemovalHook.None,
        transactionMapper = TransactionMapper(),
        ledgerEntryWriter = LedgerEntryWriter(
            entryDao = database.entryDao(),
            accountDao = database.accountDao(),
            dimensionDao = database.dimensionDao(),
        ),
    )

    private companion object {

        /** More rows than one chunk holds, so the *answer* spans chunks and not only the ask. */
        const val EXISTING = MAX_BOUND_IDENTITIES * 2L

        /**
         * Wider than any single statement can bind — the ceiling measured against this project's
         * driver is 32 766 parameters — so an unchunked read of this ask fails outright.
         */
        const val ASKED = 120_000L
    }
}
