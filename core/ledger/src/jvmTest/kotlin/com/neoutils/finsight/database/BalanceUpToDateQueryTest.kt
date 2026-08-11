package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The date cutoff behind every balance figure the app shows — the running balance, the
 * period's opening balance and the reference value of an adjustment. The boundary itself
 * (is the target day included? the next one?) is what this fixes, since a number changing
 * in silence is the risk the read carries.
 *
 * The per-currency aggregates below still cut by month, and are exercised here as such:
 * the day resolution stays with the scalar read by account.
 */
class BalanceUpToDateQueryTest {

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
    fun `the target day is included and later days are not`() = runTest {
        seed()

        assertEquals(10_000L, entryDao.balanceUpToDate(1, "2026-01-31"))
        // February's last day counts; March does not.
        assertEquals(7_500L, entryDao.balanceUpToDate(1, "2026-02-28"))
        assertEquals(7_400L, entryDao.balanceUpToDate(1, "2026-03-31"))
    }

    /**
     * The resolution the cut gained: a day inside the month, not the month itself.
     */
    @Test
    fun `a balance up to a day inside the month splits that month`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET)

            transaction("2026-04-05", 1L posts 5_000)
            transaction("2026-04-20", 1L posts 3_000)
        }

        assertEquals(5_000L, entryDao.balanceUpToDate(1, "2026-04-10"))
        assertEquals(8_000L, entryDao.balanceUpToDate(1, "2026-04-30"))
    }

    /**
     * The accumulated balance up to a month is this same query at the month's last day —
     * the same number asked with less precision, not a second read.
     */
    @Test
    fun `the accumulated balance up to a month is the balance up to its last day`() = runTest {
        seed()

        assertEquals(
            entryDao.balanceUpToDate(1, "2026-02-28"),
            entryDao.balanceUpToDate(1, LocalDate(2026, 2, 28).toString()),
        )
        assertEquals(7_500L, entryDao.balanceUpToDate(1, YearMonth(2026, 2).lastDay.toString()))
    }

    @Test
    fun `a date before any movement reads zero`() = runTest {
        seed()

        assertEquals(0L, entryDao.balanceUpToDate(1, "2025-12-31"))
    }

    @Test
    fun `an account with no entries reads zero rather than null`() = runTest {
        seed()

        assertEquals(0L, entryDao.balanceUpToDate(99, "2026-03-31"))
    }

    /**
     * The transaction date is the only reference of the cut: an entry has no date of its
     * own to diverge from it.
     */
    @Test
    fun `the cut follows the transaction date and nothing else`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET)
            account(3, AccountEntity.Type.EXPENSE)

            transaction("2026-05-15", 1L posts -2_000, 3L posts 2_000)
        }

        assertEquals(0L, entryDao.balanceUpToDate(1, "2026-05-14"))
        assertEquals(-2_000L, entryDao.balanceUpToDate(1, "2026-05-15"))
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

        assertEquals(4_000L, entryDao.balanceUpToDate(2, "2026-01-31"))
    }
}
