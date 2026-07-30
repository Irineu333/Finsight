package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        assertEquals(10_000L, entryDao.balanceUpToMonth(1, "2026-01").cents())
        // February's last day counts; March does not.
        assertEquals(7_500L, entryDao.balanceUpToMonth(1, "2026-02").cents())
        assertEquals(7_400L, entryDao.balanceUpToMonth(1, "2026-03").cents())
    }

    @Test
    fun `a month before any movement reads zero`() = runTest {
        seed()

        assertEquals(0L, entryDao.balanceUpToMonth(1, "2025-12").cents())
    }

    @Test
    fun `an account that is not in the chart has no balance at all`() = runTest {
        seed()

        // Not zero: the figure is denominated by the account, so with no account there is
        // no currency to denominate it in, and `0` would be a number in nobody's currency.
        assertNull(entryDao.balanceUpToMonth(99, "2026-03"))
    }

    @Test
    fun `an account with no entries still reads its own currency, at zero`() = runTest {
        seed().account(5, AccountEntity.Type.ASSET, currency = "USD")

        val balance = entryDao.balanceUpToMonth(5, "2026-03")

        assertEquals("USD", balance?.currency)
        assertEquals(0L, balance.cents())
    }

    @Test
    fun `the assets total spans every ASSET account and excludes the others`() = runTest {
        seed()

        // 7500 on account 1 plus 700 on account 2; the EXPENSE leg is not an asset.
        assertEquals(8_200L, entryDao.balanceUpToMonthByType("ASSET", "2026-02").cents())
    }

    @Test
    fun `liabilities accumulate by the same mechanism, in credit`() = runTest {
        seed()

        assertEquals(-3_000L, entryDao.balanceUpToMonthByType("LIABILITY", "2026-02").cents())
        assertEquals(-4_500L, entryDao.balanceUpToMonthByType("LIABILITY", "2026-03").cents())
    }

    @Test
    fun `the consolidated figure is the sum of the two natures`() = runTest {
        seed()

        val assets = entryDao.balanceUpToMonthByType("ASSET", "2026-03").cents()
        val liabilities = entryDao.balanceUpToMonthByType("LIABILITY", "2026-03").cents()

        // 8100 held minus 4500 owed — no aggregate of its own and no sign rule of its own.
        assertEquals(3_600L, assets + liabilities)
    }

    @Test
    fun `the assets total keeps two currencies apart instead of adding them`() = runTest {
        seed().apply {
            account(5, AccountEntity.Type.ASSET, currency = "USD")
            transaction("2026-02-15", (5L posts 4_000).denominatedIn("USD"))
        }

        val assets = entryDao.balanceUpToMonthByType("ASSET", "2026-02")

        // Two groups, and neither number is the sum of the two: 8200 BRL and 40 USD are not
        // 12_200 of anything.
        assertEquals(2, assets.size)
        assertEquals(8_200L, assets.cents())
        assertEquals(4_000L, assets.cents("USD"))
    }
}
