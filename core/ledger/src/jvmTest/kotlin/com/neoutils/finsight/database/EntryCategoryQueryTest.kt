package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.DimensionTotal
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The per-category reads, all of which are now per-*dimension*: a category is an
 * analytic axis, not an account, so the nominal leg says how much and the dimension
 * says what it was for.
 *
 * The perspective rule is what these pin down: a total is counted only when the
 * transaction also has a leg on one of the perspective's accounts — which is what
 * makes "spending seen from this account" different from "spending".
 */
class EntryCategoryQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    private val january = LocalDate(2026, 1, 1) to LocalDate(2026, 1, 31)

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "A")
        account(2, AccountEntity.Type.LIABILITY, "CardX")
        account(3, AccountEntity.Type.ASSET, "B")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        dimension(1, DimensionKind.INVOICE)
        dimension(10, DimensionKind.CATEGORY) // Food

        // Food expense of 50 paid from account A.
        transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
        // Food expense of 30 on card X, on invoice 1 — the card leg carries the invoice.
        transaction("2026-01-15", (10L posts 3_000).taggedWith(10), (2L posts -3_000).taggedWith(1))
    }

    @Test
    fun `account perspective counts only the direct account expense`() = runTest {
        seed()

        assertEquals(
            listOf(DimensionTotal(dimensionId = 10, total = 5_000)),
            entryDao.totalsByDimensionWithSiblingLeg("EXPENSE", january.first, january.second, listOf(1)),
        )
    }

    @Test
    fun `card perspective counts only the card expense`() = runTest {
        seed()

        assertEquals(
            listOf(DimensionTotal(dimensionId = 10, total = 3_000)),
            entryDao.totalsByDimensionWithSiblingLeg("EXPENSE", january.first, january.second, listOf(2)),
        )
    }

    @Test
    fun `all-accounts perspective still excludes card-only transactions`() = runTest {
        seed()

        // Both asset accounts as siblings — still only the first, since the card
        // purchase has no asset leg at all.
        assertEquals(
            listOf(DimensionTotal(dimensionId = 10, total = 5_000)),
            entryDao.totalsByDimensionWithSiblingLeg("EXPENSE", january.first, january.second, listOf(1, 3)),
        )
    }

    @Test
    fun `an unclassified nominal leg comes back as its own group`() = runTest {
        // An expense with no category: the nominal leg carries no dimension, and
        // "uncategorized" is that absence rather than a bucket of its own.
        seed().transaction("2026-01-20", 10L posts 1_500, 1L posts -1_500)

        val totals = entryDao.totalsByDimensionWithSiblingLeg("EXPENSE", january.first, january.second, listOf(1))

        assertEquals(setOf(DimensionTotal(null, 1_500), DimensionTotal(10, 5_000)), totals.toSet())
    }

    @Test
    fun `month total sums the legs carrying a dimension within the month`() = runTest {
        seed()

        assertEquals(8_000L, entryDao.dimensionBalanceInMonth(10, "2026-01"))
        assertEquals(0L, entryDao.dimensionBalanceInMonth(10, "2026-02"))
    }

    @Test
    fun `sub-ledger scope counts only category legs of transactions touching it`() = runTest {
        seed()

        // Invoice 1 is carried by the card leg of the second transaction only.
        assertEquals(
            listOf(DimensionTotal(dimensionId = 10, total = 3_000)),
            entryDao.totalsByDimensionInScope("EXPENSE", listOf(1)),
        )
    }
}
