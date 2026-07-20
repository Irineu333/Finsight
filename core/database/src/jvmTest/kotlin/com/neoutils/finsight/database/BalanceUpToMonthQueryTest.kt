package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The month-cutoff SQL behind both balance figures the app shows — the running
 * balance and the period's opening balance. It had no test at any level: the
 * repository test feeds a fake DAO a hardcoded number, and the use-case test is
 * thin delegation, so the boundary itself (is the target month included? is the
 * previous month?) was unverified in a change whose declared risk is a number
 * changing in silence.
 *
 * Keep the SQL here in sync with `EntryDao.balanceUpToMonth` / `assetsBalanceUpToMonth`.
 */
class BalanceUpToMonthQueryTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, date TEXT)")
        connection.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, type TEXT)")
        connection.execSQL(
            "CREATE TABLE entries (id INTEGER PRIMARY KEY, transactionId INTEGER, accountId INTEGER, amount INTEGER)"
        )
        connection.execSQL("INSERT INTO accounts (id,type) VALUES (1,'ASSET'),(2,'ASSET'),(3,'EXPENSE')")

        // January, February and March movement on account 1; February on account 2.
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (1,'2026-01-31')")
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (2,'2026-02-01')")
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (3,'2026-02-28')")
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (4,'2026-03-01')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES (1,1,10000)")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES (2,1,-2500)")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES (3,2,700)")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES (4,1,-100)")
        // A category leg, which must never count towards an asset balance.
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount) VALUES (2,3,2500)")
    }

    @AfterTest
    fun teardown() = connection.close()

    private fun balanceUpTo(accountId: Long, yearMonth: String) = scalar(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "WHERE e.accountId = $accountId AND substr(o.date, 1, 7) <= '$yearMonth'"
    )

    private fun assetsBalanceUpTo(yearMonth: String) = scalar(
        "SELECT COALESCE(SUM(e.amount), 0) FROM entries e " +
            "JOIN transactions o ON o.id = e.transactionId " +
            "JOIN accounts a ON a.id = e.accountId " +
            "WHERE a.type = 'ASSET' AND substr(o.date, 1, 7) <= '$yearMonth'"
    )

    @Test
    fun `the target month is included and later months are not`() {
        assertEquals(10000L, balanceUpTo(1, "2026-01"))
        // February's last day counts; March does not.
        assertEquals(7500L, balanceUpTo(1, "2026-02"))
        assertEquals(7400L, balanceUpTo(1, "2026-03"))
    }

    @Test
    fun `a month before any movement reads zero`() {
        assertEquals(0L, balanceUpTo(1, "2025-12"))
    }

    @Test
    fun `an account with no entries reads zero rather than null`() {
        assertEquals(0L, balanceUpTo(99, "2026-03"))
    }

    @Test
    fun `the assets total spans every ASSET account and excludes the others`() {
        // 7500 on account 1 plus 700 on account 2; the EXPENSE leg is not an asset.
        assertEquals(8200L, assetsBalanceUpTo("2026-02"))
    }

    private fun scalar(sql: String): Long {
        val stmt = connection.prepare(sql)
        stmt.step()
        val value = stmt.getLong(0)
        stmt.close()
        return value
    }
}
