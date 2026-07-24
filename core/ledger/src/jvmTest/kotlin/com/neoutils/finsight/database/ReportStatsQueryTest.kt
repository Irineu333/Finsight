package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.ScopeStatsTotals
import com.neoutils.finsight.database.entity.AccountEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The aggregate behind the report screen: income, expense and balance over a period,
 * plus the opening balance before it, for a scope of accounts — a perspective's
 * `ASSET` accounts, or a card's single `LIABILITY` one.
 *
 * Two rules make it more than a sum, and both are pinned here: an `EQUITY`
 * counter-leg makes a transaction an adjustment, so it moves the balance without
 * being income or expense; and a transfer whose `ASSET` legs all fall inside the
 * scope is internal, so it is excluded from both sides. These figures used to be
 * summed from a loaded transaction list in memory — that summation is gone, and
 * this is what replaced it.
 */
class ReportStatsQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()
    private val fixture = LedgerFixture(database)

    @AfterTest fun tearDown() = database.close()

    private suspend fun stats(scope: List<Long>, start: String, end: String): ScopeStatsTotals =
        entryDao.scopeStats(scope, LocalDate.parse(start), LocalDate.parse(end))

    @Test
    fun `account perspective includes adjustments in the period and the opening balance`() = runTest {
        with(fixture) {
            account(1, AccountEntity.Type.ASSET)     // in scope
            account(2, AccountEntity.Type.ASSET)     // out of scope
            account(100, AccountEntity.Type.INCOME)
            account(101, AccountEntity.Type.EXPENSE)
            account(102, AccountEntity.Type.EQUITY)

            transaction("2026-03-01", 1L posts 10_000, 100L posts -10_000)  // income → opening
            transaction("2026-03-05", 1L posts -3_000, 102L posts 3_000)    // adjustment → opening
            transaction("2026-03-10", 1L posts -4_000, 101L posts 4_000)    // expense → period
            transaction("2026-03-12", 1L posts 2_500, 102L posts -2_500)    // adjustment → period
            transaction("2026-03-12", 2L posts 50_000, 100L posts -50_000)  // other account
        }

        assertEquals(
            ScopeStatsTotals(income = 0, expense = 4_000, balance = -1_500, openingBalance = 7_000),
            stats(listOf(1), start = "2026-03-10", end = "2026-03-31"),
        )
    }

    @Test
    fun `credit card perspective includes adjustments in the period and the opening balance`() = runTest {
        with(fixture) {
            account(200, AccountEntity.Type.LIABILITY)  // Visa, in scope
            account(201, AccountEntity.Type.LIABILITY)  // Master, out of scope
            account(101, AccountEntity.Type.EXPENSE)
            account(102, AccountEntity.Type.EQUITY)
            account(103, AccountEntity.Type.ASSET)      // the payment source

            transaction("2026-02-25", 200L posts -10_000, 101L posts 10_000)  // → opening
            transaction("2026-02-28", 200L posts 3_000, 103L posts -3_000)    // → opening
            transaction("2026-02-28", 200L posts -500, 102L posts 500)        // → opening
            transaction("2026-03-15", 200L posts -20_000, 101L posts 20_000)  // → period
            transaction("2026-03-16", 200L posts 8_000, 103L posts -8_000)    // → period
            transaction("2026-03-16", 200L posts 1_000, 102L posts -1_000)    // → period
            transaction("2026-03-16", 201L posts -99_900, 101L posts 99_900)  // other card
        }

        assertEquals(
            ScopeStatsTotals(income = 8_000, expense = 20_000, balance = -11_000, openingBalance = -7_500),
            stats(listOf(200), start = "2026-03-01", end = "2026-03-31"),
        )
    }

    @Test
    fun `account perspective ignores internal transfers between selected accounts`() = runTest {
        with(fixture) {
            account(1, AccountEntity.Type.ASSET)
            account(2, AccountEntity.Type.ASSET)
            account(3, AccountEntity.Type.ASSET)   // outside the scope
            account(100, AccountEntity.Type.INCOME)
            account(101, AccountEntity.Type.EXPENSE)

            transaction("2026-03-10", 1L posts -10_000, 2L posts 10_000)  // internal → excluded
            transaction("2026-03-11", 1L posts -3_000, 3L posts 3_000)    // leaves the scope
            transaction("2026-03-12", 1L posts 5_000, 100L posts -5_000)
            transaction("2026-03-12", 2L posts -2_000, 101L posts 2_000)
        }

        val result = stats(listOf(1, 2), start = "2026-03-01", end = "2026-03-31")

        assertEquals(5_000L, result.income)
        assertEquals(5_000L, result.expense, "30 leaving the scope plus 20 spent")
        assertEquals(0L, result.balance)
    }

    @Test
    fun `the transaction date governs the period cut`() = runTest {
        with(fixture) {
            account(1, AccountEntity.Type.ASSET)
            account(100, AccountEntity.Type.INCOME)

            transaction("2026-02-28", 1L posts 10_000, 100L posts -10_000)  // → opening
            transaction("2026-03-01", 1L posts 4_000, 100L posts -4_000)    // → period
        }

        val result = stats(listOf(1), start = "2026-03-01", end = "2026-03-31")

        assertEquals(10_000L, result.openingBalance)
        assertEquals(4_000L, result.income)
        assertEquals(4_000L, result.balance)
    }
}
