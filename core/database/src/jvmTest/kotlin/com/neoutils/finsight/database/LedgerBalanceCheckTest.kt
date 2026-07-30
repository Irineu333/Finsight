package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The gate the v10 migration will stand on. It runs over `entries` alone, which is
 * what lets the same check guard both sides of a rewrite of the chart of accounts.
 */
class LedgerBalanceCheckTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        connection.execSQL(
            """
            CREATE TABLE `entries` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `transactionId` INTEGER NOT NULL,
                `accountId` INTEGER NOT NULL,
                `amount` INTEGER NOT NULL,
                `currency` TEXT NOT NULL DEFAULT 'BRL'
            )
            """
        )
    }

    @AfterTest
    fun tearDown() = connection.close()

    @Test
    fun `given balanced transactions then the check passes`() {
        insert(transactionId = 1, amount = 5000, currency = "BRL")
        insert(transactionId = 1, amount = -5000, currency = "BRL")
        insert(transactionId = 2, amount = 300, currency = "USD")
        insert(transactionId = 2, amount = -300, currency = "USD")

        connection.verifyLedgerBalanced(stage = "test")
    }

    @Test
    fun `given an empty ledger then the check passes`() {
        connection.verifyLedgerBalanced(stage = "test")
    }

    @Test
    fun `given a transaction that does not sum to zero then the check names it`() {
        insert(transactionId = 1, amount = 5000, currency = "BRL")
        insert(transactionId = 1, amount = -4999, currency = "BRL")

        val failure = assertFailsWith<UnbalancedLedgerException> {
            connection.verifyLedgerBalanced(stage = "pre-migration")
        }

        assertEquals(listOf(UnbalancedTransaction(1, "BRL", 1)), failure.offenders)
        assertTrue(failure.message!!.contains("pre-migration"))
    }

    /**
     * Balance is per currency, not per transaction: a transaction whose legs cancel
     * only when currencies are mixed together is unbalanced, and summing across
     * currencies would hide exactly that.
     */
    @Test
    fun `given legs that only balance across currencies then the check fails per currency`() {
        insert(transactionId = 1, amount = 5000, currency = "BRL")
        insert(transactionId = 1, amount = -5000, currency = "USD")

        val failure = assertFailsWith<UnbalancedLedgerException> {
            connection.verifyLedgerBalanced(stage = "test")
        }

        assertEquals(
            listOf(
                UnbalancedTransaction(1, "BRL", 5000),
                UnbalancedTransaction(1, "USD", -5000),
            ),
            failure.offenders.sortedBy { it.currency },
        )
    }

    /**
     * The transaction that crosses currencies passes, and passes **here** — reading
     * `entries` alone, with no idea that a conversion account exists.
     *
     * It is the whole reason the invariant was not given an exception for the
     * crossing. Had it been, this four-line `HAVING total <> 0` would have to tell a
     * legitimate exception apart from corrupt data, and the only way to know would be
     * to consult the accounts of the legs — which is exactly what it avoids doing in
     * order to stay valid before and after any rewrite of the chart.
     */
    @Test
    fun `given a cross-currency transaction then the check passes reading only the entries`() {
        // BRL 550 left one account, USD 100 arrived in another, and the residue of
        // each currency sits on that currency's conversion account.
        insert(transactionId = 1, amount = -55_000, currency = "BRL")
        insert(transactionId = 1, amount = 55_000, currency = "BRL")
        insert(transactionId = 1, amount = -10_000, currency = "USD")
        insert(transactionId = 1, amount = 10_000, currency = "USD")

        connection.verifyLedgerBalanced(stage = "test")
    }

    private fun insert(transactionId: Long, amount: Long, currency: String) {
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`,`accountId`,`amount`,`currency`) " +
                "VALUES ($transactionId, 1, $amount, '$currency')"
        )
    }
}
