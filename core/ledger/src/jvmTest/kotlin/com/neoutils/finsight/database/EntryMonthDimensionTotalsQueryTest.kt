package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.DimensionCurrencyTotal
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The month-wide per-dimension aggregate: a whole breakdown of one nominal nature in
 * a single read, with the absence of a dimension as a group of that same aggregate.
 *
 * What these pin down is the perimeter. The nature filter is the reason the null
 * group means "unclassified spending" and not "every leg in the ledger that happens
 * to carry no dimension" — conversion residue, asset legs and liability legs all
 * qualify for the latter, and none of them is spending without a category.
 */
class EntryMonthDimensionTotalsQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "A")
        account(2, AccountEntity.Type.LIABILITY, "CardX")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(20, AccountEntity.Type.INCOME, "Receitas")
        dimension(1, DimensionKind.INVOICE)
        dimension(10, DimensionKind.CATEGORY) // Food
    }

    @Test
    fun `a whole month comes back in one read, classified and unclassified alike`() = runTest {
        seed().apply {
            transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
            transaction("2026-01-20", 10L posts 1_500, 1L posts -1_500)
            // February is a different month and must not leak into January's totals.
            transaction("2026-02-02", (10L posts 9_900).taggedWith(10), 1L posts -9_900)
        }

        assertEquals(
            setOf(
                DimensionCurrencyTotal(dimensionId = 10, currency = "BRL", total = 5_000),
                DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = 1_500),
            ),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01").toSet(),
        )
    }

    @Test
    fun `each currency stands on its own, in both groups`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET, "Nubank")
            account(2, AccountEntity.Type.ASSET, "Chase", currency = "USD")
            account(10, AccountEntity.Type.EXPENSE, "Despesas")
            account(11, AccountEntity.Type.EXPENSE, "Expenses", currency = "USD")
            dimension(10, DimensionKind.CATEGORY)

            transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
            transaction(
                "2026-01-12",
                (11L posts 1_200).taggedWith(10) inCurrency "USD",
                2L posts -1_200 inCurrency "USD",
            )
            transaction("2026-01-14", 11L posts 800 inCurrency "USD", 2L posts -800 inCurrency "USD")
        }

        assertEquals(
            setOf(
                DimensionCurrencyTotal(10, "BRL", 5_000),
                DimensionCurrencyTotal(10, "USD", 1_200),
                DimensionCurrencyTotal(null, "USD", 800),
            ),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01").toSet(),
        )
    }

    @Test
    fun `a card purchase with no category counts as unclassified`() = runTest {
        // The invoice's dimension lands on the LIABILITY leg, so the EXPENSE leg of a
        // card purchase carries no dimension when the user picked no category — which
        // is precisely the unclassified group, and the reason this read is not scoped
        // by sibling asset accounts.
        seed().transaction("2026-01-15", 10L posts 3_000, (2L posts -3_000).taggedWith(1))

        assertEquals(
            listOf(DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = 3_000)),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01"),
        )
    }

    @Test
    fun `conversion residue stays out of the unclassified total`() = runTest {
        seed().apply {
            account(3, AccountEntity.Type.ASSET, "Chase", currency = "USD")
            account(90, AccountEntity.Type.CONVERSION, "Conversão")
            account(91, AccountEntity.Type.CONVERSION, "Conversion", currency = "USD")

            transaction("2026-01-20", 10L posts 1_500, 1L posts -1_500)
            // A cross-currency transfer leaves residue on CONVERSION, with no dimension.
            transaction(
                "2026-01-22",
                1L posts -5_500,
                90L posts 5_500,
                3L posts 1_000 inCurrency "USD",
                91L posts -1_000 inCurrency "USD",
            )
        }

        assertEquals(
            listOf(DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = 1_500)),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01"),
            "CONVERSION is a nature of its own, so its dimensionless legs are not spending",
        )
    }

    @Test
    fun `the asset leg of the same expense does not double the total`() = runTest {
        // Both legs of an uncategorized expense carry no dimension; only the nominal
        // one is counted, because the nature filter is what selects it.
        seed().transaction("2026-01-20", 10L posts 1_500, 1L posts -1_500)

        assertEquals(
            listOf(DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = 1_500)),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01"),
        )
    }

    @Test
    fun `expense and income are separate aggregates`() = runTest {
        seed().apply {
            transaction("2026-01-20", 10L posts 1_500, 1L posts -1_500)
            transaction("2026-01-21", 20L posts -7_000, 1L posts 7_000)
        }

        assertEquals(
            listOf(DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = 1_500)),
            entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01"),
        )
        assertEquals(
            listOf(DimensionCurrencyTotal(dimensionId = null, currency = "BRL", total = -7_000)),
            entryDao.totalsByDimensionInMonth("INCOME", "2026-01"),
            "income posts in credit; the display sign is the reader's business, not the ledger's",
        )
    }

    @Test
    fun `a month with no nominal movement produces no rows`() = runTest {
        seed()

        assertEquals(emptyList(), entryDao.totalsByDimensionInMonth("EXPENSE", "2026-01"))
    }
}
