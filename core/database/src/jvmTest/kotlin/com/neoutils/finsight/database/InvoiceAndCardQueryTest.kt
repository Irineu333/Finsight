package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the ledger aggregates added for the card/invoice readers (task 4.11):
 * EntryDao `invoicePeriodTotals` (per-invoice expense/advance-payment/adjustment from
 * the LIABILITY legs), `cardMonthTotals` (month-wide card expense/payment) and the
 * all-time `balanceOf`. Keep the SQL in sync with the DAO.
 */
class InvoiceAndCardQueryTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, type TEXT)")
        connection.execSQL("CREATE TABLE operations (id INTEGER PRIMARY KEY, date TEXT)")
        connection.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, transactionId INTEGER, accountId INTEGER, amount INTEGER, invoiceId INTEGER)")

        // Card(2) liability; Bank(1) asset; Food(10) expense; Recon(30) equity.
        connection.execSQL(
            "INSERT INTO accounts (id,name,type) VALUES " +
                "(1,'Bank','ASSET'),(2,'Card','LIABILITY'),(10,'Food','EXPENSE'),(30,'Recon','EQUITY')"
        )

        // opA: card expense 60 -> Card -6000 (inv 1) / Food +6000
        connection.execSQL("INSERT INTO operations (id,date) VALUES (1,'2026-03-05')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (1,2,-6000,1),(1,10,6000,NULL)")
        // opB: card expense 40 -> Card -4000 (inv 1) / Food +4000
        connection.execSQL("INSERT INTO operations (id,date) VALUES (2,'2026-03-08')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (2,2,-4000,1),(2,10,4000,NULL)")
        // opC: advance payment 30 -> Card +3000 (inv 1) / Bank -3000
        connection.execSQL("INSERT INTO operations (id,date) VALUES (3,'2026-03-10')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (3,2,3000,1),(3,1,-3000,NULL)")
        // opD: card adjustment +10 -> Card +1000 (inv 1) / Recon -1000 (EQUITY leg)
        connection.execSQL("INSERT INTO operations (id,date) VALUES (4,'2026-03-12')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (4,2,1000,1),(4,30,-1000,NULL)")
        // opE: card expense 99 next month -> excluded from March card totals; other invoice (2)
        connection.execSQL("INSERT INTO operations (id,date) VALUES (5,'2026-04-03')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (5,2,-9900,2),(5,10,9900,NULL)")
    }

    @AfterTest
    fun teardown() = connection.close()

    private data class InvoiceTotals(val expense: Long, val advancePayment: Long, val adjustment: Long)

    // Mirrors EntryDao.invoicePeriodTotals.
    private fun invoiceTotals(invoiceId: Long): InvoiceTotals {
        val stmt = connection.prepare(
            """
            SELECT
              COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
              COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS advancePayment,
              COALESCE(SUM(CASE WHEN eq = 1 THEN amount ELSE 0 END), 0) AS adjustment
            FROM (
              SELECT e.amount AS amount,
                EXISTS(SELECT 1 FROM entries x JOIN accounts a ON a.id = x.accountId WHERE x.transactionId = e.transactionId AND a.type = 'EQUITY') AS eq
              FROM entries e
              WHERE e.invoiceId = $invoiceId
            )
            """
        )
        stmt.step()
        val totals = InvoiceTotals(stmt.getLong(0), stmt.getLong(1), stmt.getLong(2))
        stmt.close()
        return totals
    }

    private data class CardTotals(val expense: Long, val payment: Long)

    // Mirrors EntryDao.cardMonthTotals.
    private fun cardTotals(yearMonth: String): CardTotals {
        val stmt = connection.prepare(
            """
            SELECT
              COALESCE(SUM(CASE WHEN eq = 0 AND amount < 0 THEN -amount ELSE 0 END), 0) AS expense,
              COALESCE(SUM(CASE WHEN eq = 0 AND amount > 0 THEN amount ELSE 0 END), 0) AS payment
            FROM (
              SELECT e.amount AS amount,
                EXISTS(SELECT 1 FROM entries x JOIN accounts a2 ON a2.id = x.accountId WHERE x.transactionId = e.transactionId AND a2.type = 'EQUITY') AS eq
              FROM entries e
              JOIN accounts a ON a.id = e.accountId
              JOIN operations o ON o.id = e.transactionId
              WHERE a.type = 'LIABILITY' AND substr(o.date, 1, 7) = '$yearMonth'
            )
            """
        )
        stmt.step()
        val totals = CardTotals(stmt.getLong(0), stmt.getLong(1))
        stmt.close()
        return totals
    }

    // Mirrors EntryDao.balanceOf.
    private fun balanceOf(accountId: Long): Long {
        val stmt = connection.prepare("SELECT COALESCE(SUM(amount), 0) FROM entries WHERE accountId = $accountId")
        stmt.step()
        val v = stmt.getLong(0)
        stmt.close()
        return v
    }

    @Test
    fun `invoice period totals classify the liability legs`() {
        val totals = invoiceTotals(invoiceId = 1)
        assertEquals(10000, totals.expense, "60 + 40")
        assertEquals(3000, totals.advancePayment)
        assertEquals(1000, totals.adjustment)
    }

    @Test
    fun `card month totals sum across cards and exclude other months and adjustments`() {
        val totals = cardTotals(yearMonth = "2026-03")
        assertEquals(10000, totals.expense, "the April expense is excluded")
        assertEquals(3000, totals.payment, "the adjustment (EQUITY) is not a payment")
    }

    @Test
    fun `balanceOf is the all-time natural balance of the card account`() {
        // -6000 -4000 +3000 +1000 -9900 = -15900 (owed 159.00 across both invoices)
        assertEquals(-15900, balanceOf(accountId = 2))
    }
}
