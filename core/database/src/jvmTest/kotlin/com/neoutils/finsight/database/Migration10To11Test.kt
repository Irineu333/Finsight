package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    /**
     * Seeds a chart and a balanced transaction, all in the legacy denomination — the
     * shape every existing database actually has.
     */
    private fun seedLegacyLedger() {
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "VALUES (1, 'Nubank', 'ASSET', 'BRL', 'wallet', 1, 1000, 0), " +
                "(2, 'EXPENSES', 'EXPENSE', 'BRL', 'wallet', 0, 1000, 0), " +
                "(3, 'CLOSED_ACCOUNT', 'EQUITY', 'BRL', 'wallet', 0, 1000, 0)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`id`, `title`, `date`) VALUES (1, 'Mercado', '2026-03-10')"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "VALUES (1, 1, -5000, 'BRL', NULL), (1, 2, 5000, 'BRL', NULL)"
        )
    }

    private fun currenciesOf(table: String): List<String> {
        val stmt = connection.prepare("SELECT `currency` FROM `$table` ORDER BY rowid")
        val out = buildList { while (stmt.step()) add(stmt.getText(0)) }
        stmt.close()
        return out
    }

    /**
     * The user who has been reading `$` over data that said BRL all along. The data
     * starts saying what they always read, and not one figure moves.
     */
    @Test
    fun `a foreign region relabels accounts and entries in the same transaction`() {
        seedLegacyLedger()

        migrate(relabelCurrency = "USD")

        assertEquals(listOf("USD", "USD", "USD"), currenciesOf("accounts"))
        assertEquals(listOf("USD", "USD"), currenciesOf("entries"))

        val stmt = connection.prepare("SELECT `amount` FROM `entries` ORDER BY rowid")
        assertTrue(stmt.step())
        assertEquals(-5000L, stmt.getLong(0), "relabelling is re-denomination, never conversion")
        assertTrue(stmt.step())
        assertEquals(5000L, stmt.getLong(0))
        stmt.close()
    }

    /**
     * The account and its entries move **together**. Relabelling one without the other
     * would split that account's history into two currencies, and `LedgerBalanceCheck`
     * — which groups by `(transactionId, currency)` without consulting `accounts` —
     * would stop being readable as the truth about the account.
     */
    @Test
    fun `relabelling preserves the per-currency balance`() {
        seedLegacyLedger()

        // The guard runs inside `migrate`; reaching the assertion is half the proof.
        migrate(relabelCurrency = "EUR")

        val stmt = connection.prepare(
            "SELECT `currency`, SUM(`amount`) FROM `entries` GROUP BY `transactionId`, `currency`"
        )
        var groups = 0
        while (stmt.step()) {
            groups++
            assertEquals("EUR", stmt.getText(0))
            assertEquals(0L, stmt.getLong(1), "every transaction still sums to zero in each currency")
        }
        stmt.close()
        assertEquals(1, groups, "one currency, one group — the history was not split")
    }

    /**
     * **A budget limit is relabelled with the chart it is measured against.**
     *
     * Its denomination was never a choice either — the column was filled with the legacy
     * code because that is what already denominated it. Left behind, the relabelled user
     * gets a limit in a currency he holds nothing in, and a progress bar that
     * consolidates and reads `≈` forever: exactly the cost design D13 keeps off the
     * single-currency user, arriving through the migration instead of through the form.
     */
    @Test
    fun `a relabelled chart takes the budget limits with it`() {
        seedBudget(id = 1, title = "Alimentação", amount = 500.0)
        seedBudget(id = 2, title = "Transporte", amount = 120.55)
        seedLegacyLedger()

        migrate(relabelCurrency = "USD")

        val stmt = connection.prepare("SELECT `amount`, `currency` FROM `budgets` ORDER BY `id`")
        var rows = 0
        while (stmt.step()) {
            rows++
            assertEquals("USD", stmt.getText(1), "a limit left in the legacy currency the user no longer holds")
        }
        stmt.close()
        assertEquals(2, rows)

        // Re-denomination, not conversion — here as everywhere else.
        val amounts = connection.prepare("SELECT `amount` FROM `budgets` ORDER BY `id`")
        assertTrue(amounts.step())
        assertEquals(500.0, amounts.getDouble(0))
        assertTrue(amounts.step())
        assertEquals(120.55, amounts.getDouble(0))
        amounts.close()
    }

    /**
     * The system rows go with the rest. They are lines of the chart like any other, and
     * `Account.currency` has to mean the same thing on every one of them — which is
     * what makes "the currency of an account is immutable" a rule of the chart rather
     * than a rule of the account facade.
     */
    @Test
    fun `the system rows are relabelled too`() {
        seedLegacyLedger()

        migrate(relabelCurrency = "USD")

        val stmt = connection.prepare(
            "SELECT `currency` FROM `accounts` WHERE `name` IN ('EXPENSES', 'CLOSED_ACCOUNT')"
        )
        var rows = 0
        while (stmt.step()) {
            rows++
            assertEquals("USD", stmt.getText(0))
        }
        stmt.close()
        assertEquals(2, rows)
    }

    /**
     * **Relabelling does not repeat**, and no flag records that it ran: the migration
     * is declared for `10 → 11` and `user_version` is what stops it. A later change of
     * device region cannot fire it again, because there is no version left for it to
     * migrate from.
     */
    @Test
    fun `the record that it ran is the schema version, not a flag of its own`() {
        val migration = migration1011("USD")

        assertEquals(10, migration.startVersion)
        assertEquals(11, migration.endVersion)
    }

    /**
     * `execSQL` binds nothing, so the code is interpolated. The caller validates it
     * against the catalog; this is the module declining to depend on that being true.
     */
    @Test
    fun `a code that is not a code never reaches the statement`() {
        seedLegacyLedger()

        assertFailsWith<IllegalArgumentException> { migrate(relabelCurrency = "US'; DROP TABLE accounts; --") }

        assertEquals(listOf("BRL", "BRL", "BRL"), currenciesOf("accounts"))
    }
}
