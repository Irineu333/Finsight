package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the ledger aggregates that back the account screen (EntryDao
 * `accountPeriodTotals` and `entryCountInMonth`): income/expense/adjustment/
 * invoice-payment classified by a transaction's counter-legs, and the per-category
 * entry count. Keep the SQL in sync with the DAO.
 */
class AccountPeriodTotalsQueryTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, type TEXT)")
        connection.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, date TEXT)")
        connection.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, transactionId INTEGER, accountId INTEGER, amount INTEGER, invoiceId INTEGER)")

        // A(1) asset, Card(2) liability, B(3) asset; Food(10) expense, Salary(20) income, Recon(30) equity.
        connection.execSQL(
            "INSERT INTO accounts (id,name,type) VALUES " +
                "(1,'A','ASSET'),(2,'Card','LIABILITY'),(3,'B','ASSET')," +
                "(10,'Food','EXPENSE'),(20,'Salary','INCOME'),(30,'Recon','EQUITY')"
        )

        // op1: income 100 -> A +10000 / Salary -10000
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (1,'2026-01-05')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (1,1,10000,NULL),(1,20,-10000,NULL)")
        // op2: expense 30 -> A -3000 / Food +3000
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (2,'2026-01-10')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (2,1,-3000,NULL),(2,10,3000,NULL)")
        // op3: transfer 50 A -> B -> A -5000 / B +5000 (two ASSET legs, no counter-type)
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (3,'2026-01-12')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (3,1,-5000,NULL),(3,3,5000,NULL)")
        // op4: adjustment +40 -> A +4000 / Recon -4000 (EQUITY leg)
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (4,'2026-01-15')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (4,1,4000,NULL),(4,30,-4000,NULL)")
        // op5: invoice payment 80 A -> Card -> A -8000 / Card +8000 (LIABILITY leg tags invoice 1)
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (5,'2026-01-20')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (5,1,-8000,NULL),(5,2,8000,1)")
        // op6: expense 99 on A in the NEXT month -> must be excluded from January
        connection.execSQL("INSERT INTO transactions (id,date) VALUES (6,'2026-02-03')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (6,1,-9900,NULL),(6,10,9900,NULL)")
    }

    @AfterTest
    fun teardown() = connection.close()

    private data class Totals(val income: Long, val expense: Long, val adjustment: Long, val invoicePayment: Long)

    // Mirrors EntryDao.accountPeriodTotals.
    private fun totals(accountId: Long, yearMonth: String): Totals {
        val stmt = connection.prepare(
            """
            SELECT
              COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS income,
              COALESCE(SUM(CASE WHEN eq = 0 AND li = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
              COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment,
              COALESCE(SUM(CASE WHEN eq = 0 AND li = 1 THEN -amount ELSE 0 END), 0) AS invoicePayment
            FROM (
              SELECT e.amount AS amount,
                EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq,
                EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'LIABILITY') AS li
              FROM entries e
              JOIN transactions o ON o.id = e.transactionId
              WHERE e.accountId = $accountId AND substr(o.date, 1, 7) = '$yearMonth'
            )
            """
        )
        stmt.step()
        val totals = Totals(stmt.getLong(0), stmt.getLong(1), stmt.getLong(2), stmt.getLong(3))
        stmt.close()
        return totals
    }

    private fun entryCount(accountId: Long, yearMonth: String): Long {
        val stmt = connection.prepare(
            "SELECT COUNT(*) FROM entries e JOIN transactions o ON o.id = e.transactionId " +
                "WHERE e.accountId = $accountId AND substr(o.date, 1, 7) = '$yearMonth'"
        )
        stmt.step()
        val count = stmt.getLong(0)
        stmt.close()
        return count
    }

    @Test
    fun `asset account flows are classified by the transaction counter-legs`() {
        // income = op1 (+10000); expense = op2 (3000) + op3 transfer-out (5000);
        // adjustment = op4 (+4000, signed); invoicePayment = op5 (8000). Next month excluded.
        assertEquals(Totals(income = 10000, expense = 8000, adjustment = 4000, invoicePayment = 8000), totals(1, "2026-01"))
    }

    @Test
    fun `a transfer credits income on the destination account`() {
        assertEquals(Totals(income = 5000, expense = 0, adjustment = 0, invoicePayment = 0), totals(3, "2026-01"))
    }

    @Test
    fun `entry count per category counts the month's category legs`() {
        assertEquals(1L, entryCount(10, "2026-01")) // Food: only op2 in January
        assertEquals(1L, entryCount(20, "2026-01")) // Salary: only op1
        assertEquals(0L, entryCount(10, "2026-03")) // none in March
    }
}
