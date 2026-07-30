package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v10 → v11: the rate table appears, and a budget's limit learns which currency it is
 * stated in.
 *
 * There is nothing to convert here and nothing to guess. Every database that can reach this
 * point holds one currency only — a second one is unproducible until the account form offers
 * the choice — so the currency each limit receives is **exactly** the one that already
 * denominated it. The test's job is to say that out loud: no amount moves, and the value the
 * new column gets is read from the user's own default account rather than assumed.
 *
 * The fixture is the real old schema, reached the way a device reaches it: v7 verbatim, then
 * the v7 → v10 migration, then this one.
 */
class Migration10To11Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V7_SCHEMA.forEach(connection::execSQL)

        // Two accounts, the default one first; and a category for the budgets to list.
        connection.execSQL(
            "INSERT INTO `accounts` (`id`,`name`,`iconKey`,`isDefault`,`createdAt`) VALUES " +
                "(1,'A','wallet',1,1000),(2,'B','wallet',0,1000)"
        )
        connection.execSQL(
            "INSERT INTO `categories` (`id`,`name`,`iconKey`,`type`,`createdAt`) VALUES (1,'Food','food','EXPENSE',1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`id`,`categoryId`,`iconCategoryId`,`iconKey`,`title`,`amount`,`period`,`limitType`,`percentage`,`recurringId`,`createdAt`) VALUES " +
                "(1,1,1,'food','Comida',450.0,'MONTHLY','FIXED',NULL,NULL,1000)," +
                "(2,1,1,'food','Lazer',120.5,'MONTHLY','FIXED',NULL,NULL,1000)"
        )
        connection.execSQL("INSERT INTO `budget_categories` (`budgetId`,`categoryId`) VALUES (1,1),(2,1)")

        MIGRATION_7_10.migrate(connection)
    }

    @AfterTest
    fun tearDown() = connection.close()

    @Test
    fun `the rate table is created, empty, keyed by currency date and origin`() {
        MIGRATION_10_11.migrate(connection)

        assertEquals(0L, scalar("SELECT COUNT(*) FROM `exchange_rates`"))

        // Keyed by origin as well: a rate the user typed and one collected from an operation
        // coexist on the same day, and which of them answers is decided by that column.
        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`,`date`,`rate`,`source`) VALUES " +
                "('USD','2026-05-01',5.5,'OPERATION'),('USD','2026-05-01',5.4,'USER')"
        )
        assertEquals(2L, scalar("SELECT COUNT(*) FROM `exchange_rates`"))
    }

    @Test
    fun `every limit is denominated in the currency it already had, and no amount moves`() {
        val before = budgets()

        MIGRATION_10_11.migrate(connection)

        val after = budgets()
        assertEquals(before.map { it.first }, after.map { it.first }, "the same budgets, in the same order")
        assertEquals(before.map { it.second }, after.map { it.second }, "and not a cent of any limit changed")

        // The default account's currency, which for every database reaching v11 is the only
        // currency in it — so the column is exact, not approximate.
        val accountCurrency = text("SELECT `currency` FROM `accounts` WHERE `isDefault` = 1")
        assertEquals(listOf(accountCurrency, accountCurrency), after.map { it.third })
    }

    @Test
    fun `a database with no default account still gets a currency for every limit`() {
        connection.execSQL("UPDATE `accounts` SET `isDefault` = 0")

        MIGRATION_10_11.migrate(connection)

        // The last-resort constant, and never a NULL: the column is NOT NULL, and a limit
        // with no legend at all would be a figure nobody can read.
        assertTrue(budgets().all { it.third.isNotBlank() })
        assertEquals(0L, scalar("SELECT COUNT(*) FROM `budgets` WHERE `currency` IS NULL"))
    }

    @Test
    fun `the budget's categories survive the table being rebuilt`() {
        MIGRATION_10_11.migrate(connection)

        // The m2m rows are what a budget's categories actually are (v10 removed the
        // write-only copy), so rebuilding `budgets` must not strand them.
        assertEquals(2L, scalar("SELECT COUNT(*) FROM `budget_categories`"))
        assertEquals(
            2L,
            scalar(
                "SELECT COUNT(*) FROM `budget_categories` bc " +
                    "JOIN `budgets` b ON b.`id` = bc.`budgetId`"
            ),
        )
    }

    /** Each budget as `(id, amount, currency)`, currency empty while the column is absent. */
    private fun budgets(): List<Triple<Long, Double, String>> {
        val hasCurrency = scalar(
            "SELECT COUNT(*) FROM pragma_table_info('budgets') WHERE `name` = 'currency'"
        ) == 1L
        val currency = if (hasCurrency) "`currency`" else "''"
        return connection.prepare("SELECT `id`, `amount`, $currency FROM `budgets` ORDER BY `id`").use { statement ->
            buildList {
                while (statement.step()) {
                    add(Triple(statement.getLong(0), statement.getDouble(1), statement.getText(2)))
                }
            }
        }
    }

    private fun scalar(sql: String): Long = connection.prepare(sql).use { statement ->
        statement.step()
        statement.getLong(0)
    }

    private fun text(sql: String): String = connection.prepare(sql).use { statement ->
        statement.step()
        statement.getText(0)
    }
}
