package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The month-cutoff behind both balance figures the app shows — the running balance
 * and the period's opening balance. Neither had a test at any level: the repository
 * test feeds a fake DAO a hardcoded number and the use case is thin delegation, so
 * the boundary itself (is the target month included? the previous one?) was
 * unverified in a change whose declared risk is a number changing in silence.
 */
class BalanceUpToMonthQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET)
        account(2, AccountEntity.Type.ASSET)
        account(3, AccountEntity.Type.EXPENSE)
        account(4, AccountEntity.Type.LIABILITY)

        transaction("2026-01-31", 1L posts 10_000)
        // A category leg, which must never count towards an asset balance.
        transaction("2026-02-01", 1L posts -2_500, 3L posts 2_500)
        transaction("2026-02-28", 2L posts 700)
        // A card purchase: the liability leg is stored in credit.
        transaction("2026-02-10", 4L posts -3_000, 3L posts 3_000)
        transaction("2026-03-01", 1L posts -100)
        transaction("2026-03-04", 4L posts -1_500, 3L posts 1_500)
    }

    @Test
    fun `the target month is included and later months are not`() = runTest {
        seed()

        assertEquals(10_000L, entryDao.balanceUpToMonth(1, "2026-01"))
        // February's last day counts; March does not.
        assertEquals(7_500L, entryDao.balanceUpToMonth(1, "2026-02"))
        assertEquals(7_400L, entryDao.balanceUpToMonth(1, "2026-03"))
    }

    @Test
    fun `a month before any movement reads zero`() = runTest {
        seed()

        assertEquals(0L, entryDao.balanceUpToMonth(1, "2025-12"))
    }

    @Test
    fun `an account with no entries reads zero rather than null`() = runTest {
        seed()

        assertEquals(0L, entryDao.balanceUpToMonth(99, "2026-03"))
    }

    @Test
    fun `the assets total spans every ASSET account and excludes the others`() = runTest {
        seed()

        // 7500 on account 1 plus 700 on account 2; the EXPENSE leg is not an asset.
        assertEquals(8_200L, entryDao.balanceUpToMonthByType("ASSET", "2026-02", emptySet()).sole().total)
    }

    @Test
    fun `liabilities accumulate by the same mechanism, in credit`() = runTest {
        seed()

        assertEquals(-3_000L, entryDao.balanceUpToMonthByType("LIABILITY", "2026-02", emptySet()).sole().total)
        assertEquals(-4_500L, entryDao.balanceUpToMonthByType("LIABILITY", "2026-03", emptySet()).sole().total)
    }

    @Test
    fun `the consolidated figure is the sum of the two natures`() = runTest {
        seed()

        val assets = entryDao.balanceUpToMonthByType("ASSET", "2026-03", emptySet()).sole().total
        val liabilities = entryDao.balanceUpToMonthByType("LIABILITY", "2026-03", emptySet()).sole().total

        // 8100 held minus 4500 owed — no aggregate of its own and no sign rule of its own.
        assertEquals(3_600L, assets + liabilities)
    }

    // --- the accumulated balance by nature is per currency (task 4.5) ---

    @Test
    fun `the assets total keeps each currency apart`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET)
            account(2, AccountEntity.Type.ASSET, currency = "USD")

            transaction("2026-01-10", 1L posts 10_000)
            transaction("2026-01-11", 2L posts 4_000 inCurrency "USD")
        }

        val totals = entryDao.balanceUpToMonthByType("ASSET", "2026-01", emptySet())

        assertEquals(10_000L, totals.forCurrency("BRL")?.total)
        assertEquals(4_000L, totals.forCurrency("USD")?.total)
        assertEquals(2, totals.size)
    }

    @Test
    fun `a nature with no movement produces no row at all`() = runTest {
        seed()

        // Not a row of zeros: a grouped aggregate has no group to report. The empty
        // list is what says "there is no movement", which is a different fact from a
        // total of zero in some currency.
        assertEquals(emptyList(), entryDao.balanceUpToMonthByType("INCOME", "2026-03", emptySet()))
    }

    // --- the accumulated balance admits a set of accounts to leave out ---

    @Test
    fun `excluding nothing is the read as it always was`() = runTest {
        seed()

        // The design leans on SQLite accepting an empty `NOT IN ()`, which is true for
        // every row. That is a premise about the engine, so it is proven here rather
        // than assumed: same value, same currency, same shape as the parameterless read.
        assertEquals(
            entryDao.balanceUpToMonthByType("ASSET", "2026-02", emptySet()),
            entryDao.balanceUpToMonthByType("ASSET", "2026-02", emptyList()),
        )
        assertEquals(8_200L, entryDao.balanceUpToMonthByType("ASSET", "2026-02", emptySet()).sole().total)
    }

    @Test
    fun `an excluded account does not take part in the sum`() = runTest {
        seed()

        // 8200 held across both, minus the 700 of account 2.
        assertEquals(7_500L, entryDao.balanceUpToMonthByType("ASSET", "2026-02", setOf(2L)).sole().total)
    }

    @Test
    fun `excluding every account of the nature leaves no row`() = runTest {
        seed()

        // No group to report — the same empty aggregate a month without movement gives,
        // which the consolidation layer denominates as a zero.
        assertEquals(
            emptyList(),
            entryDao.balanceUpToMonthByType("ASSET", "2026-02", setOf(1L, 2L)),
        )
    }

    @Test
    fun `an id matching no account excludes nothing`() = runTest {
        seed()

        assertEquals(8_200L, entryDao.balanceUpToMonthByType("ASSET", "2026-02", setOf(99L)).sole().total)
    }

    @Test
    fun `exclusion keeps the grouping by currency`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET)
            account(2, AccountEntity.Type.ASSET, currency = "USD")
            account(3, AccountEntity.Type.ASSET, currency = "USD")

            transaction("2026-01-10", 1L posts 10_000)
            transaction("2026-01-11", 2L posts 4_000 inCurrency "USD")
            transaction("2026-01-12", 3L posts 1_000 inCurrency "USD")
        }

        val totals = entryDao.balanceUpToMonthByType("ASSET", "2026-01", setOf(3L))

        assertEquals(10_000L, totals.forCurrency("BRL")?.total)
        assertEquals(4_000L, totals.forCurrency("USD")?.total)
        assertEquals(2, totals.size)
    }

    @Test
    fun `excluding the only account of a currency drops that currency`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET)
            account(2, AccountEntity.Type.ASSET, currency = "USD")

            transaction("2026-01-10", 1L posts 10_000)
            transaction("2026-01-11", 2L posts 4_000 inCurrency "USD")
        }

        val totals = entryDao.balanceUpToMonthByType("ASSET", "2026-01", setOf(2L))

        // One term left, so the figure above is exact — the perimeter doing what it
        // promises, not a special case to compensate for.
        assertEquals(10_000L, totals.sole().total)
        assertEquals("BRL", totals.sole().currency)
    }

    @Test
    fun `a single account balance stays scalar, since one account is one currency`() = runTest {
        LedgerFixture(database).apply {
            account(2, AccountEntity.Type.ASSET, currency = "USD")
            transaction("2026-01-11", 2L posts 4_000 inCurrency "USD")
        }

        assertEquals(4_000L, entryDao.balanceUpToMonth(2, "2026-01"))
    }
}
