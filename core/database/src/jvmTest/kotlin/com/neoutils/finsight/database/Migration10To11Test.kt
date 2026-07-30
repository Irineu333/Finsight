package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Schema 11 over the real v10 shape: a table appears, a column appears, and **nothing
 * else moves**.
 *
 * That last part is the claim worth testing. The change this migration belongs to
 * denominates money everywhere, and the one thing it promises is that no stored figure
 * changes — every existing database is entirely in `'BRL'`, so the currency the new
 * column receives is exactly the one that already denominated each limit. A migration
 * that adjusted an `amount` would be indistinguishable from this one on a fresh
 * install and catastrophic on a real device.
 */
class Migration10To11Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V10_SCHEMA.forEach(connection::execSQL)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun migrate(relabelCurrency: String? = null) =
        migration1011(relabelCurrency).migrate(connection)

    private fun seedBudget(id: Long, title: String, amount: Double) {
        connection.execSQL(
            "INSERT INTO `budgets` " +
                "(`id`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, " +
                "`limitType`, `percentage`, `recurringId`, `createdAt`) " +
                "VALUES ($id, 1, 'food', '$title', $amount, 'MONTHLY', 'FIXED', NULL, NULL, 1000)"
        )
    }

    @Test
    fun `the rate table is created, and it is born empty`() {
        migrate()

        assertTrue(connection.tableExists("exchange_rates"))
        assertEquals(
            listOf("id", "currency", "date", "rate", "source"),
            connection.getColumns("exchange_rates"),
        )
        assertTrue(connection.indexExists("index_exchange_rates_currency_date_source"))
        assertTrue(connection.indexExists("index_exchange_rates_currency_date"))

        val stmt = connection.prepare("SELECT COUNT(*) FROM `exchange_rates`")
        assertTrue(stmt.step())
        assertEquals(0L, stmt.getLong(0), "no rate is invented; the archive starts empty")
        stmt.close()
    }

    @Test
    fun `the unique triple lets a correction coexist with the observation it corrects`() {
        migrate()

        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `date`, `rate`, `source`) " +
                "VALUES ('USD', '2026-03-10', 5.5, 'DERIVED')"
        )
        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `date`, `rate`, `source`) " +
                "VALUES ('USD', '2026-03-10', 5.6, 'USER')"
        )

        val stmt = connection.prepare(
            "SELECT COUNT(*) FROM `exchange_rates` WHERE `currency` = 'USD' AND `date` = '2026-03-10'"
        )
        assertTrue(stmt.step())
        assertEquals(2L, stmt.getLong(0), "correcting a rate must not destroy the one an operation observed")
        stmt.close()
    }

    @Test
    fun `every pre-existing budget limit is denominated in the currency it already had`() {
        seedBudget(1, "Alimentação", 500.0)
        seedBudget(2, "Transporte", 120.55)

        migrate()

        assertTrue("currency" in connection.getColumns("budgets"))

        val stmt = connection.prepare("SELECT `id`, `amount`, `currency` FROM `budgets` ORDER BY `id`")
        assertTrue(stmt.step())
        assertEquals(1L, stmt.getLong(0))
        assertEquals(500.0, stmt.getDouble(1), "no stored figure moves")
        assertEquals("BRL", stmt.getText(2))
        assertTrue(stmt.step())
        assertEquals(2L, stmt.getLong(0))
        assertEquals(120.55, stmt.getDouble(1))
        assertEquals("BRL", stmt.getText(2))
        assertFalse(stmt.step())
        stmt.close()
    }

    @Test
    fun `a database with no budgets migrates just the same`() {
        migrate()

        val stmt = connection.prepare("SELECT COUNT(*) FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals(0L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `the ledger still balances and no reference dangles`() {
        // A balanced pair of entries and a card purchase, so the three guards the
        // migration closes with have something to actually check.
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "VALUES (1, 'Nubank', 'ASSET', 'BRL', 'wallet', 1, 1000, 0), " +
                "(2, 'Despesas', 'EXPENSE', 'BRL', 'wallet', 0, 1000, 0)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`id`, `title`, `date`) VALUES (1, 'Mercado', '2026-03-10')"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "VALUES (1, 1, -5000, 'BRL', NULL), (1, 2, 5000, 'BRL', NULL)"
        )
        seedBudget(1, "Alimentação", 500.0)

        // The guards throw from inside `migrate`; reaching the assertions is the proof.
        migrate()

        val stmt = connection.prepare("SELECT SUM(`amount`) FROM `entries`")
        assertTrue(stmt.step())
        assertEquals(0L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `a null relabel currency touches no denomination at all`() {
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "VALUES (1, 'Nubank', 'ASSET', 'BRL', 'wallet', 1, 1000, 0)"
        )

        migrate(relabelCurrency = null)

        val stmt = connection.prepare("SELECT `currency` FROM `accounts` WHERE `id` = 1")
        assertTrue(stmt.step())
        assertEquals("BRL", stmt.getText(0), "not relabelling is the common case")
        stmt.close()
    }
}
