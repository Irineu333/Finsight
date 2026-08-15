package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one-shot reading cut by a period, next to the reactive one cut by a day.
 *
 * Two things are being pinned here. That the period is **inclusive on both ends** —
 * an exclusive end silently drops the last day of every month a caller asks for —
 * and that the twin did not drift from `observeBy`: with the two ends on the same
 * day the two queries must answer the same rows, in the same order. The filters are
 * copied predicate for predicate, so a change to one that forgets the other shows up
 * here rather than in a screen.
 */
class TransactionDaoPeriodFilterTest {

    private val database = ledgerDatabase()
    private val transactionDao = database.transactionDao()

    @AfterTest fun tearDown() = database.close()

    private companion object {
        const val CHECKING = 1L
        const val SAVINGS = 2L
        const val EXPENSES = 3L
        const val GROCERIES = 10L
        const val TRANSPORT = 11L
    }

    /**
     * Four days, two accounts and two dimensions — the smallest seed in which every
     * filter can exclude something the others keep.
     */
    private suspend fun seed(): Map<String, Long> {
        val fixture = LedgerFixture(database)
        fixture.account(CHECKING, AccountEntity.Type.ASSET, "Checking")
        fixture.account(SAVINGS, AccountEntity.Type.ASSET, "Savings")
        fixture.account(EXPENSES, AccountEntity.Type.EXPENSE, "Despesas")
        fixture.dimension(GROCERIES, DimensionKind.CATEGORY)
        fixture.dimension(TRANSPORT, DimensionKind.CATEGORY)

        return mapOf(
            "jan-01" to fixture.transaction(
                "2026-01-01",
                CHECKING posts -1_000,
                (EXPENSES posts 1_000).taggedWith(GROCERIES),
            ),
            "jan-15" to fixture.transaction(
                "2026-01-15",
                SAVINGS posts -2_000,
                (EXPENSES posts 2_000).taggedWith(TRANSPORT),
            ),
            "jan-31" to fixture.transaction(
                "2026-01-31",
                CHECKING posts -3_000,
                (EXPENSES posts 3_000).taggedWith(TRANSPORT),
            ),
            "feb-01" to fixture.transaction(
                "2026-02-01",
                CHECKING posts -4_000,
                (EXPENSES posts 4_000).taggedWith(GROCERIES),
            ),
        )
    }

    private suspend fun idsBy(
        startDate: String? = null,
        endDate: String? = null,
        dimensionId: Long? = null,
        accountId: Long? = null,
    ): List<Long> = transactionDao.getBy(
        startDate = startDate?.let(LocalDate::parse),
        endDate = endDate?.let(LocalDate::parse),
        dimensionId = dimensionId,
        accountId = accountId,
    ).map { it.id }

    @Test
    fun `no filter at all answers everything, newest first`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("feb-01"), seeded.getValue("jan-31"), seeded.getValue("jan-15"), seeded.getValue("jan-01")),
            idsBy(),
            "every predicate is null-neutral, and the order is the same one `observeBy` declares",
        )
    }

    @Test
    fun `both ends of the period are inclusive`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("jan-31"), seeded.getValue("jan-15"), seeded.getValue("jan-01")),
            idsBy(startDate = "2026-01-01", endDate = "2026-01-31"),
            "January asked for whole must contain the 1st and the 31st",
        )
    }

    @Test
    fun `an open start takes everything up to the end, and vice versa`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("jan-15"), seeded.getValue("jan-01")),
            idsBy(endDate = "2026-01-15"),
        )
        assertEquals(
            listOf(seeded.getValue("feb-01"), seeded.getValue("jan-31")),
            idsBy(startDate = "2026-01-31"),
        )
    }

    @Test
    fun `the account filter reads the legs, not the row`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("feb-01"), seeded.getValue("jan-31"), seeded.getValue("jan-01")),
            idsBy(accountId = CHECKING),
        )
        assertEquals(listOf(seeded.getValue("jan-15")), idsBy(accountId = SAVINGS))
    }

    @Test
    fun `the dimension filter reads the legs too`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("feb-01"), seeded.getValue("jan-01")),
            idsBy(dimensionId = GROCERIES),
        )
        assertEquals(
            listOf(seeded.getValue("jan-31"), seeded.getValue("jan-15")),
            idsBy(dimensionId = TRANSPORT),
        )
    }

    @Test
    fun `the three filters intersect`() = runTest {
        val seeded = seed()

        assertEquals(
            listOf(seeded.getValue("jan-31")),
            idsBy(
                startDate = "2026-01-01",
                endDate = "2026-01-31",
                dimensionId = TRANSPORT,
                accountId = CHECKING,
            ),
            "the 15th is TRANSPORT but on Savings, the 1st is on Checking but GROCERIES",
        )
        assertEquals(
            emptyList<Long>(),
            idsBy(startDate = "2026-02-01", dimensionId = TRANSPORT),
            "an intersection nothing satisfies is empty, not everything",
        )
    }

    @Test
    fun `a single-day period answers exactly what observeBy observes`() = runTest {
        seed()

        listOf("2026-01-01", "2026-01-15", "2026-01-31", "2026-02-01", "2026-03-01").forEach { day ->
            val date = LocalDate.parse(day)

            assertEquals(
                transactionDao.observeBy(date = date, dimensionId = null, accountId = null)
                    .first()
                    .map { it.id },
                idsBy(startDate = day, endDate = day),
                "the twin must not drift from the reading it was copied from ($day)",
            )
        }
    }

    @Test
    fun `it agrees with observeBy on the other filters too`() = runTest {
        seed()

        val date = LocalDate.parse("2026-01-31")

        assertEquals(
            transactionDao.observeBy(date = date, dimensionId = TRANSPORT, accountId = CHECKING)
                .first()
                .map { it.id },
            idsBy(
                startDate = "2026-01-31",
                endDate = "2026-01-31",
                dimensionId = TRANSPORT,
                accountId = CHECKING,
            ),
        )
    }
}
