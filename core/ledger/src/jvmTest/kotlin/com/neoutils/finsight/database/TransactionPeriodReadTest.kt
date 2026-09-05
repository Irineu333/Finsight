package com.neoutils.finsight.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.ledger.DimensionWriteGuard
import com.neoutils.finsight.domain.ledger.TransactionRemovalHook
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **Reading a period: which days it holds, in what order, and what it costs.**
 *
 * A period read is the one read whose correctness is decided entirely by its two edges, and an edge
 * is invisible from inside: a range that slips by a day does not empty an answer, it **moves a
 * posting from one period's answer into its neighbour's**, and only asking for both periods makes
 * the two halves of that visible. So the ledger here spans four months and every day that decides
 * the cut carries a posting of its own.
 *
 * Two more things the read promises and this pins beside the cut:
 *
 * - **the order is total** — the day the posting is dated, then the identity the ledger assigned,
 *   which is unique by construction, so the same period always comes back the same way;
 * - **the legs come in bulk** — one read for the whole period rather than one per posting, chunked
 *   to what the driver will bind, so a period wider than a single statement can express is still
 *   answered whole and every posting still gets its own legs and nobody else's.
 */
class TransactionPeriodReadTest {

    private val database = ledgerDatabase()
    private val transactionDao = database.transactionDao()
    private val fixture = LedgerFixture(database)

    @AfterTest fun tearDown() = database.close()

    // ----------------------------------------------------------------------------------
    // The edges
    // ----------------------------------------------------------------------------------

    @Test
    fun `the period holds its first and its last day, and neither neighbour`() = runTest {
        fourMonths()

        assertEquals(
            listOf(LAST_OF_MARCH, MID_MARCH, FIRST_OF_MARCH),
            between("2026-03-01", "2026-03-31").map { it.date.toString() },
            "the first and the last day of the period are inside it, and the day before and the " +
                "day after are not",
        )
    }

    /**
     * The same ledger read a month either side.
     *
     * A posting the period wrongly kept is a posting its neighbour wrongly lost, and asserting only
     * the middle month sees one half of that at most.
     */
    @Test
    fun `each neighbouring period answers with its own posting and never with March's`() = runTest {
        fourMonths()

        assertEquals(
            listOf(LAST_OF_FEBRUARY),
            between("2026-02-01", "2026-02-28").map { it.date.toString() },
        )
        assertEquals(
            listOf(FIRST_OF_APRIL),
            between("2026-04-01", "2026-04-30").map { it.date.toString() },
        )
    }

    @Test
    fun `a period the ledger holds nothing in is answered emptily`() = runTest {
        fourMonths()

        assertEquals(emptyList(), between("2026-01-01", "2026-01-31"))
    }

    /** A period of one day is its two edges meeting, and it holds that day. */
    @Test
    fun `a period of a single day holds that day`() = runTest {
        fourMonths()

        assertEquals(
            listOf(FIRST_OF_MARCH),
            between("2026-03-01", "2026-03-01").map { it.date.toString() },
        )
    }

    // ----------------------------------------------------------------------------------
    // The order
    // ----------------------------------------------------------------------------------

    /**
     * Newest first, ties broken by the identity, descending.
     *
     * The fixture is built so that no weaker order passes it: the postings are seeded out of date
     * sequence, and two of them share a day, so insertion order, the identity alone and an
     * ascending tie-break each produce a different list from the one asserted.
     */
    @Test
    fun `the period is answered newest first, ties broken by the identity`() = runTest {
        with(fixture) {
            transaction(EARLY)          // id 1
            transaction(SHARED_DAY)     // id 2
            transaction(SHARED_DAY)     // id 3
            transaction(MID_MARCH)      // id 4
        }

        assertEquals(
            listOf(3L, 2L, 4L, 1L),
            between("2026-03-01", "2026-03-31").map { it.id },
        )
    }

    // ----------------------------------------------------------------------------------
    // The legs, in bulk
    // ----------------------------------------------------------------------------------

    /**
     * Every posting of the period keeps its own legs across a chunk boundary.
     *
     * The legs of a period are read in chunks, so the failure a single-posting fixture cannot show
     * is one at a boundary: legs lost between two chunks, or a posting handed another's.
     */
    @Test
    fun `every posting of a period wider than one chunk keeps its own legs`() = runTest {
        val postings = MAX_BOUND_IDENTITIES * 2 + 1
        seed(postings)

        val transactions = repository().getTransactionsBetween(
            startDate = LocalDate.parse("2026-03-01"),
            endDate = LocalDate.parse("2026-03-31"),
        )

        assertEquals(postings, transactions.size, "the period lost postings")
        assertTrue(
            transactions.all { transaction ->
                transaction.entries.singleOrNull()?.amount == transaction.id
            },
            "a posting came back with legs that are not its own, or with none",
        )
    }

    /**
     * A period holding more postings than one statement can bind is answered whole.
     *
     * The ceiling is the driver's, not the caller's, and it is stated as a fact about it rather than
     * as a number read somewhere: the same ask, unchunked, is refused outright.
     */
    @Test
    fun `a period wider than the driver can bind is answered whole`() = runTest {
        seed(WIDER_THAN_ONE_STATEMENT)

        val transactions = repository().getTransactionsBetween(
            startDate = LocalDate.parse("2026-03-01"),
            endDate = LocalDate.parse("2026-03-31"),
        )

        assertEquals(WIDER_THAN_ONE_STATEMENT, transactions.size)
        assertEquals(
            WIDER_THAN_ONE_STATEMENT,
            transactions.count { it.entries.size == 1 },
            "a chunk boundary dropped a posting's legs, or gave it a second one",
        )

        assertFailsWith<SQLiteException> {
            database.entryDao().getByTransactionIds((1L..WIDER_THAN_ONE_STATEMENT).toList())
        }
    }

    // ----------------------------------------------------------------------------------

    private suspend fun between(start: String, end: String): List<TransactionEntity> =
        transactionDao.getBetween(
            startDate = LocalDate.parse(start),
            endDate = LocalDate.parse(end),
        )

    /**
     * Four months of one ledger, arranged so that every edge this class asks for has something to
     * leave out: a posting on the day before the period, one on each of its two boundary days, one
     * inside it and one on the day after.
     */
    private suspend fun fourMonths() = with(fixture) {
        transaction(LAST_OF_FEBRUARY)
        transaction(FIRST_OF_MARCH)
        transaction(MID_MARCH)
        transaction(LAST_OF_MARCH)
        transaction(FIRST_OF_APRIL)
    }

    /** [count] postings inside the period, each carrying one leg whose amount is its own identity. */
    private suspend fun seed(count: Int) {
        database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT,
                name = "account",
                type = AccountEntity.Type.ASSET,
                currency = LEGACY_CURRENCY,
            )
        )
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                (1L..count).forEach { id ->
                    transactionDao.insert(
                        TransactionEntity(id = id, title = null, date = LocalDate.parse(MID_MARCH))
                    )
                    database.entryDao().insertAll(
                        listOf(
                            EntryEntity(
                                transactionId = id,
                                accountId = ACCOUNT,
                                amount = id,
                                currency = LEGACY_CURRENCY,
                            )
                        )
                    )
                }
            }
        }
    }

    private fun repository() = TransactionRepository(
        database = database,
        transactionDao = transactionDao,
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

        const val ACCOUNT = 1L

        const val LAST_OF_FEBRUARY = "2026-02-28"
        const val FIRST_OF_MARCH = "2026-03-01"
        const val EARLY = "2026-03-05"
        const val MID_MARCH = "2026-03-15"
        const val SHARED_DAY = "2026-03-20"
        const val LAST_OF_MARCH = "2026-03-31"
        const val FIRST_OF_APRIL = "2026-04-01"

        /**
         * More postings in one period than a single statement can name: the driver measured for
         * [MAX_BOUND_IDENTITIES] accepts 32 766 host parameters and refuses the next one.
         */
        const val WIDER_THAN_ONE_STATEMENT = 33_000
    }
}
