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

        transaction("2026-01-31", 1L posts 10_000)
        // A category leg, which must never count towards an asset balance.
        transaction("2026-02-01", 1L posts -2_500, 3L posts 2_500)
        transaction("2026-02-28", 2L posts 700)
        transaction("2026-03-01", 1L posts -100)
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
        assertEquals(8_200L, entryDao.assetsBalanceUpToMonth("2026-02"))
    }
}
