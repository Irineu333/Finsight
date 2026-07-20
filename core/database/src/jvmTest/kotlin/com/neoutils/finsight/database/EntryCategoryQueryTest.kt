package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the perspective-scoped category-spending SQL (EntryDao
 * `categoryTotalsWithSiblingLeg`): a category total is counted only when the
 * operation also has a leg on one of the perspective's accounts. Keep the SQL in
 * sync with the DAO.
 */
class EntryCategoryQueryTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, type TEXT)")
        connection.execSQL("CREATE TABLE operations (id INTEGER PRIMARY KEY, date TEXT)")
        connection.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, transactionId INTEGER, accountId INTEGER, amount INTEGER, invoiceId INTEGER)")

        // A(1) asset, card X account(2) liability, Food(10) expense category account.
        connection.execSQL("INSERT INTO accounts (id,name,type) VALUES (1,'A','ASSET'),(2,'CardX','LIABILITY'),(10,'Food','EXPENSE')")

        // op1: Food expense 50 paid from account A -> entries Food +5000 / A -5000
        connection.execSQL("INSERT INTO operations (id,date) VALUES (1,'2026-01-10')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (1,10,5000,NULL),(1,1,-5000,NULL)")
        // op2: Food expense 30 on card X, invoice 1 -> Food +3000 / CardX -3000 (card leg tags invoice 1)
        connection.execSQL("INSERT INTO operations (id,date) VALUES (2,'2026-01-15')")
        connection.execSQL("INSERT INTO entries (transactionId,accountId,amount,invoiceId) VALUES (2,10,3000,NULL),(2,2,-3000,1)")
    }

    @AfterTest
    fun teardown() = connection.close()

    // Mirrors EntryDao.categoryTotalsWithSiblingLeg with the given sibling ids inlined.
    private fun categoryTotal(categoryType: String, siblingIds: String): Long {
        val stmt = connection.prepare(
            "SELECT COALESCE(SUM(e.amount),0) FROM entries e " +
                "JOIN operations o ON o.id=e.transactionId JOIN accounts a ON a.id=e.accountId " +
                "WHERE a.type='$categoryType' AND o.date BETWEEN '2026-01-01' AND '2026-01-31' " +
                "AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId=o.id AND s.accountId IN ($siblingIds))"
        )
        stmt.step()
        val total = stmt.getLong(0)
        stmt.close()
        return total
    }

    @Test
    fun `account perspective counts only the direct account expense`() {
        // Sibling = account A(1): sees op1's Food (5000), not op2 (card, no A leg).
        assertEquals(5000L, categoryTotal("EXPENSE", "1"))
    }

    @Test
    fun `card perspective counts only the card expense`() {
        // Sibling = card X account(2): sees op2's Food (3000), not op1 (no card leg).
        assertEquals(3000L, categoryTotal("EXPENSE", "2"))
    }

    @Test
    fun `all-accounts perspective still excludes card-only operations`() {
        // Both asset accounts as siblings — still only op1 (op2 has no asset leg).
        assertEquals(5000L, categoryTotal("EXPENSE", "1,3"))
    }

    // Mirrors EntryDao.balanceInMonth (the dashboard category-spending query).
    private fun monthTotal(accountId: Long, yearMonth: String): Long {
        val stmt = connection.prepare(
            "SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN operations o ON o.id=e.transactionId " +
                "WHERE e.accountId=$accountId AND substr(o.date,1,7)='$yearMonth'"
        )
        stmt.step()
        val total = stmt.getLong(0)
        stmt.close()
        return total
    }

    @Test
    fun `month total sums a category account within the month`() {
        // Food(10): op1 +5000 (Jan 10) + op2 +3000 (Jan 15) = 8000 in January, 0 otherwise.
        assertEquals(8000L, monthTotal(10, "2026-01"))
        assertEquals(0L, monthTotal(10, "2026-02"))
    }

    // Mirrors EntryDao.categoryTotalsForInvoices.
    private fun categoryTotalForInvoices(categoryType: String, invoiceIds: String): Long {
        val stmt = connection.prepare(
            "SELECT COALESCE(SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.accountId " +
                "WHERE a.type='$categoryType' " +
                "AND EXISTS (SELECT 1 FROM entries s WHERE s.transactionId=e.transactionId AND s.invoiceId IN ($invoiceIds))"
        )
        stmt.step()
        val total = stmt.getLong(0)
        stmt.close()
        return total
    }

    @Test
    fun `invoice scope counts only category legs of operations touching the invoice`() {
        // Invoice 1 is tagged on op2's card leg -> only op2's Food (3000); op1 has no invoice.
        assertEquals(3000L, categoryTotalForInvoices("EXPENSE", "1"))
    }
}
