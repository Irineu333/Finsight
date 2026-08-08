package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The migration is additive and has no backfill, so what has to be proven is mostly
 * what it *does not* do: no account starts yielding, no category becomes a system
 * one, and no transaction is rewritten.
 */
class Migration10To11Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`isDefault` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`isArchived` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `dimensions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`dimensionId` INTEGER NOT NULL, " +
                "`isArchived` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT, " +
                "`date` TEXT NOT NULL, " +
                "`recurringId` INTEGER, " +
                "`recurringCycle` INTEGER, " +
                "`installmentId` INTEGER, " +
                "`installmentNumber` INTEGER)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`dimensionId` INTEGER)"
        )

        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) VALUES " +
                "(1, 'Nubank', 'ASSET', 'BRL', 'wallet', 1, 1000, 0), " +
                "(2, 'Receitas', 'INCOME', 'BRL', 'default', 0, 1000, 0)"
        )
        connection.execSQL("INSERT INTO `dimensions` (`id`, `kind`) VALUES (1, 'CATEGORY')")
        connection.execSQL(
            "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`) " +
                "VALUES (1, 'Salário', 'default', 'INCOME', 1000, 1, 0)"
        )
        connection.execSQL("INSERT INTO `transactions` (`id`, `title`, `date`) VALUES (1, 'Salário', '2026-07-05')")
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) VALUES " +
                "(1, 1, 500000, 'BRL', NULL), (1, 2, -500000, 'BRL', 1)"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 10 when migrated to 11 then the two columns exist`() {
        MIGRATION_10_11.migrate(connection)

        assertTrue("yieldsInterest" in connection.getColumns("accounts"))
        assertTrue("systemKey" in connection.getColumns("categories"))
    }

    @Test
    fun `given existing accounts when migrated to 11 then none of them yields`() {
        MIGRATION_10_11.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `accounts` WHERE `yieldsInterest` = 0")
        stmt.step()
        assertEquals(2L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given an existing account when migrated to 11 then its other data survives`() {
        MIGRATION_10_11.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived` " +
                "FROM `accounts` WHERE `id` = 1"
        )
        assertTrue(stmt.step())
        assertEquals("Nubank", stmt.getText(0))
        assertEquals("ASSET", stmt.getText(1))
        assertEquals("BRL", stmt.getText(2))
        assertEquals("wallet", stmt.getText(3))
        assertEquals(1L, stmt.getLong(4))
        assertEquals(1000L, stmt.getLong(5))
        assertEquals(0L, stmt.getLong(6))
        stmt.close()
    }

    @Test
    fun `given existing categories when migrated to 11 then none of them is a system category`() {
        MIGRATION_10_11.migrate(connection)

        val stmt = connection.prepare("SELECT `name`, `dimensionId`, `systemKey` FROM `categories` WHERE `id` = 1")
        assertTrue(stmt.step())
        assertEquals("Salário", stmt.getText(0))
        assertEquals(1L, stmt.getLong(1))
        assertTrue(stmt.isNull(2))
        stmt.close()
    }

    @Test
    fun `given an existing ledger when migrated to 11 then no transaction is rewritten`() {
        MIGRATION_10_11.migrate(connection)

        val transactions = connection.prepare("SELECT COUNT(*) FROM `transactions`")
        transactions.step()
        assertEquals(1L, transactions.getLong(0))
        transactions.close()

        val entries = connection.prepare(
            "SELECT COUNT(*), COALESCE(SUM(`amount`), 0), " +
                "COALESCE(SUM(CASE WHEN `dimensionId` IS NULL THEN 0 ELSE 1 END), 0) FROM `entries`"
        )
        entries.step()
        assertEquals(2L, entries.getLong(0))
        assertEquals(0L, entries.getLong(1))
        assertEquals(1L, entries.getLong(2))
        entries.close()
    }

    @Test
    fun `given database at version 10 when migrated to 11 then systemKey is unique`() {
        MIGRATION_10_11.migrate(connection)

        assertTrue(connection.indexExists("index_categories_systemKey"))

        connection.execSQL(
            "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`, `systemKey`) " +
                "VALUES (2, 'Rendimentos', 'savings', 'INCOME', 1000, 1, 0, 'yield')"
        )

        // A second row under the same key would split the dimension in two: the reads
        // would separate by one of them and silently return half the yield.
        assertFailsWith<SQLiteException> {
            connection.execSQL(
                "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`, `systemKey`) " +
                    "VALUES (3, 'CDI', 'money', 'INCOME', 1000, 1, 0, 'yield')"
            )
        }
    }

    @Test
    fun `given the unique key when many categories have none then they coexist`() {
        MIGRATION_10_11.migrate(connection)

        // NULLs are exempt from a unique index in SQLite — which is the whole reason
        // the user's own categories are untouched by it.
        connection.execSQL(
            "INSERT INTO `categories` (`name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`) VALUES " +
                "('Mercado', 'cart', 'EXPENSE', 1000, 1, 0), ('Lazer', 'movie', 'EXPENSE', 1000, 1, 0)"
        )

        val stmt = connection.prepare("SELECT COUNT(*) FROM `categories` WHERE `systemKey` IS NULL")
        stmt.step()
        assertEquals(3L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given database at version 11 when a yielding account is written then it reads back`() {
        MIGRATION_10_11.migrate(connection)

        connection.execSQL("UPDATE `accounts` SET `yieldsInterest` = 1 WHERE `id` = 1")
        connection.execSQL("UPDATE `categories` SET `systemKey` = 'yield' WHERE `id` = 1")

        val stmt = connection.prepare(
            "SELECT (SELECT `yieldsInterest` FROM `accounts` WHERE `id` = 1), " +
                "(SELECT `systemKey` FROM `categories` WHERE `id` = 1)"
        )
        assertTrue(stmt.step())
        assertEquals(1L, stmt.getLong(0))
        assertEquals("yield", stmt.getText(1))
        stmt.close()
    }
}
