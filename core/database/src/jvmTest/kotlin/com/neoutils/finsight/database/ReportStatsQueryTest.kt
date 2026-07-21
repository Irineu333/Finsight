package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the ledger aggregate that backs the report screen (EntryDao `reportStats`):
 * income/expense/balance for a period and the opening balance before it, over a scope of
 * accounts (a perspective's ASSET accounts, or a card's LIABILITY account), with internal
 * transfers among the scope excluded. These are the same figures the use case produced by
 * summing a loaded transaction list in memory — that summation is gone; this pins the SQL.
 * Keep the query in sync with the DAO.
 */
class ReportStatsQueryTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, type TEXT)")
        connection.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, date TEXT)")
        connection.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, transactionId INTEGER, accountId INTEGER, amount INTEGER)")
    }

    @AfterTest
    fun teardown() = connection.close()

    private var nextTx = 1L
    private fun account(id: Long, type: String) =
        connection.execSQL("INSERT INTO accounts (id,name,type) VALUES ($id,'a$id','$type')")

    /** One transaction whose legs are (accountId to cents) pairs, on [date]. */
    private fun tx(date: String, vararg legs: Pair<Long, Long>) {
        val id = nextTx++
        connection.execSQL("INSERT INTO transactions (id,date) VALUES ($id,'$date')")
        legs.forEach { (accountId, amount) ->
            connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES ($id,$accountId,$amount)")
        }
    }

    private data class Stats(val income: Long, val expense: Long, val balance: Long, val openingBalance: Long)

    // Mirrors EntryDao.reportStats.
    private fun stats(scopeIds: List<Long>, start: String, end: String): Stats {
        val scope = scopeIds.joinToString(",")
        val stmt = connection.prepare(
            """
            SELECT
              COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
              COALESCE(SUM(CASE WHEN inPeriod = 1 AND eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
              COALESCE(SUM(CASE WHEN inPeriod = 1 THEN amount ELSE 0 END), 0) AS balance,
              COALESCE(SUM(CASE WHEN inPeriod = 0 THEN amount ELSE 0 END), 0) AS openingBalance
            FROM (
              SELECT e.amount AS amount,
                CASE WHEN o.date >= '$start' THEN 1 ELSE 0 END AS inPeriod,
                EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId
                       WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
              FROM entries e
              JOIN transactions o ON o.id = e.transactionId
              WHERE e.accountId IN ($scope)
                AND o.date <= '$end'
                AND NOT (
                  (SELECT COUNT(DISTINCT x.accountId) FROM entries x JOIN accounts a ON a.id = x.accountId
                   WHERE x.transactionId = e.transactionId AND a.type = 'ASSET') >= 2
                  AND NOT EXISTS (
                    SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId
                    WHERE x.transactionId = e.transactionId AND a.type = 'ASSET' AND x.accountId NOT IN ($scope)
                  )
                )
            )
            """
        )
        stmt.step()
        val result = Stats(stmt.getLong(0), stmt.getLong(1), stmt.getLong(2), stmt.getLong(3))
        stmt.close()
        return result
    }

    @Test
    fun `account perspective includes adjustments in the period and the opening balance`() {
        account(1, "ASSET")   // Carteira (in scope)
        account(2, "ASSET")   // other account (out of scope)
        account(100, "INCOME")
        account(101, "EXPENSE")
        account(102, "EQUITY")

        tx("2026-03-01", 1L to 10000, 100L to -10000)  // income 100 → opening
        tx("2026-03-05", 1L to -3000, 102L to 3000)    // adjustment -30 → opening
        tx("2026-03-10", 1L to -4000, 101L to 4000)    // expense 40 → period
        tx("2026-03-12", 1L to 2500, 102L to -2500)    // adjustment +25 → period
        tx("2026-03-12", 2L to 50000, 100L to -50000)  // other account → excluded from scope

        val result = stats(listOf(1), start = "2026-03-10", end = "2026-03-31")

        assertEquals(Stats(income = 0, expense = 4000, balance = -1500, openingBalance = 7000), result)
    }

    @Test
    fun `credit card perspective includes adjustments in the period and the opening balance`() {
        account(200, "LIABILITY")  // Visa (in scope)
        account(201, "LIABILITY")  // Master (out of scope)
        account(101, "EXPENSE")
        account(102, "EQUITY")
        account(103, "ASSET")      // checking (payment source)

        tx("2026-02-25", 200L to -10000, 101L to 10000)  // card expense 100 → opening
        tx("2026-02-28", 200L to 3000, 103L to -3000)    // card payment 30 → opening
        tx("2026-02-28", 200L to -500, 102L to 500)      // card adjustment -5 → opening
        tx("2026-03-15", 200L to -20000, 101L to 20000)  // card expense 200 → period
        tx("2026-03-16", 200L to 8000, 103L to -8000)    // card payment 80 → period
        tx("2026-03-16", 200L to 1000, 102L to -1000)    // card adjustment +10 → period
        tx("2026-03-16", 201L to -99900, 101L to 99900)  // other card → excluded from scope

        val result = stats(listOf(200), start = "2026-03-01", end = "2026-03-31")

        assertEquals(Stats(income = 8000, expense = 20000, balance = -11000, openingBalance = -7500), result)
    }

    @Test
    fun `account perspective ignores internal transfers between selected accounts`() {
        account(1, "ASSET")
        account(2, "ASSET")
        account(3, "ASSET")   // outside the scope
        account(100, "INCOME")
        account(101, "EXPENSE")

        tx("2026-03-10", 1L to -10000, 2L to 10000)  // A→B internal (both in scope) → excluded
        tx("2026-03-11", 1L to -3000, 3L to 3000)    // A→C (C outside) → A leg counts as expense
        tx("2026-03-12", 1L to 5000, 100L to -5000)  // income 50
        tx("2026-03-12", 2L to -2000, 101L to 2000)  // expense 20 on B

        val result = stats(listOf(1, 2), start = "2026-03-01", end = "2026-03-31")

        assertEquals(5000, result.income)
        assertEquals(5000, result.expense)   // 30 (A→C) + 20 (B)
        assertEquals(0, result.balance)      // -30 + 50 - 20
    }

    @Test
    fun `the transaction date governs the period cut`() {
        account(1, "ASSET")
        account(100, "INCOME")

        tx("2026-02-28", 1L to 10000, 100L to -10000)  // before the period → opening
        tx("2026-03-01", 1L to 4000, 100L to -4000)    // inside the period → income

        val result = stats(listOf(1), start = "2026-03-01", end = "2026-03-31")

        assertEquals(10000, result.openingBalance)
        assertEquals(4000, result.income)
        assertEquals(4000, result.balance)
    }
}
