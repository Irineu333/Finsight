package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.AccountPeriodTotals
import com.neoutils.finsight.database.dao.AssetMonthTotals
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The aggregates behind the account screen: income/expense/adjustment/invoice-payment
 * classified by a transaction's *counter-legs*, and the per-category entry count.
 *
 * The classification is the part worth pinning. Nothing is persisted saying "this
 * was a card payment" — it is read off the fact that the transaction also has a
 * `LIABILITY` leg, and an adjustment off an `EQUITY` one.
 */
class AccountPeriodTotalsQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "A")
        account(2, AccountEntity.Type.LIABILITY, "Card")
        account(3, AccountEntity.Type.ASSET, "B")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(20, AccountEntity.Type.INCOME, "Receitas")
        account(30, AccountEntity.Type.EQUITY, "Recon")
        dimension(1, DimensionKind.INVOICE)
        dimension(10, DimensionKind.CATEGORY) // Food
        dimension(20, DimensionKind.CATEGORY) // Salary

        transaction("2026-01-05", 1L posts 10_000, (20L posts -10_000).taggedWith(20))
        transaction("2026-01-10", 1L posts -3_000, (10L posts 3_000).taggedWith(10))
        // A transfer: two ASSET legs, no counter-type at all.
        transaction("2026-01-12", 1L posts -5_000, 3L posts 5_000)
        transaction("2026-01-15", 1L posts 4_000, 30L posts -4_000)
        transaction("2026-01-20", 1L posts -8_000, (2L posts 8_000).taggedWith(1))
        // Next month — must be excluded from January.
        transaction("2026-02-03", 1L posts -9_900, (10L posts 9_900).taggedWith(10))
    }

    @Test
    fun `asset account flows are classified by the transaction counter-legs`() = runTest {
        seed()

        assertEquals(
            AccountPeriodTotals(
                income = 10_000,      // the salary
                expense = 8_000,      // the expense (3000) plus the transfer out (5000)
                adjustment = 4_000,   // signed, and kept out of income
                settlement = 8_000,
            ),
            entryDao.accountPeriodTotals(1, "2026-01"),
        )
    }

    @Test
    fun `a transfer credits income on the destination account`() = runTest {
        seed()

        assertEquals(
            AccountPeriodTotals(income = 5_000, expense = 0, adjustment = 0, settlement = 0),
            entryDao.accountPeriodTotals(3, "2026-01"),
        )
    }

    @Test
    fun `asset month totals exclude transfers and card payments across every asset account`() = runTest {
        seed()

        // Month-wide, over accounts 1 and 3: only the salary (income), the expense, and
        // the reconciliation adjustment survive. The transfer (two ASSET legs) and the
        // card payment (LIABILITY counter-leg) move money between the user's own
        // accounts and are neither income nor expense — this is what the per-account
        // `accountPeriodTotals` deliberately does *not* do, and why the summary needs
        // its own read.
        assertEquals(
            AssetMonthTotals(income = 10_000, expense = 3_000, adjustment = 4_000),
            entryDao.assetMonthTotals("2026-01"),
        )
        assertEquals(
            AssetMonthTotals(income = 0, expense = 0, adjustment = 0),
            entryDao.assetMonthTotals("2026-03"),
        )
    }

    @Test
    fun `entry count per category counts the month's legs carrying its dimension`() = runTest {
        seed()

        assertEquals(1, entryDao.dimensionEntryCountInMonth(10, "2026-01"))
        assertEquals(1, entryDao.dimensionEntryCountInMonth(20, "2026-01"))
        assertEquals(0, entryDao.dimensionEntryCountInMonth(10, "2026-03"))
    }
}
