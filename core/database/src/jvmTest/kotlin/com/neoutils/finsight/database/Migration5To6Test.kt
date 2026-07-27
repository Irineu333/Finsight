package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration5To6Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        // v5 `credit_cards` table (without `iconKey`)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `credit_cards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`limit` REAL NOT NULL, " +
                "`closingDay` INTEGER NOT NULL, " +
                "`dueDay` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 5 when migrated to 6 then credit_cards table still exists`() {
        MIGRATION_5_6.migrate(connection)

        assertTrue(connection.tableExists("credit_cards"))
    }

    @Test
    fun `given database at version 5 when migrated to 6 then credit_cards has iconKey column`() {
        MIGRATION_5_6.migrate(connection)

        assertTrue("iconKey" in connection.getColumns("credit_cards"))
    }

    @Test
    fun `given existing credit card when migrated to 6 then card data is preserved`() {
        connection.execSQL(
            "INSERT INTO `credit_cards` (`id`, `name`, `limit`, `closingDay`, `dueDay`, `createdAt`) " +
                "VALUES (1, 'Nubank', 5000.0, 20, 27, 1000)"
        )

        MIGRATION_5_6.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `id`, `name`, `limit`, `closingDay`, `dueDay`, `createdAt` FROM `credit_cards`"
        )
        assertTrue(stmt.step())
        assertEquals(1L, stmt.getLong(0))
        assertEquals("Nubank", stmt.getText(1))
        assertEquals(5000.0, stmt.getDouble(2))
        assertEquals(20L, stmt.getLong(3))
        assertEquals(27L, stmt.getLong(4))
        assertEquals(1000L, stmt.getLong(5))
        stmt.close()
    }

    @Test
    fun `given existing credit card when migrated to 6 then iconKey defaults to card`() {
        connection.execSQL(
            "INSERT INTO `credit_cards` (`name`, `limit`, `closingDay`, `dueDay`, `createdAt`) " +
                "VALUES ('Inter', 1200.0, 5, 12, 1000)"
        )

        MIGRATION_5_6.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `credit_cards`")
        assertTrue(stmt.step())
        assertEquals("card", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given several credit cards when migrated to 6 then every row is preserved`() {
        connection.execSQL(
            "INSERT INTO `credit_cards` (`name`, `limit`, `closingDay`, `dueDay`, `createdAt`) VALUES " +
                "('Nubank', 5000.0, 20, 27, 1000), " +
                "('Inter', 1200.0, 5, 12, 2000), " +
                "('Itaú', 800.0, 1, 8, 3000)"
        )

        MIGRATION_5_6.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `credit_cards` WHERE `iconKey` = 'card'")
        stmt.step()
        assertEquals(3L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given no credit card when migrated to 6 then iconKey is still available for new rows`() {
        MIGRATION_5_6.migrate(connection)

        connection.execSQL(
            "INSERT INTO `credit_cards` (`name`, `limit`, `closingDay`, `dueDay`, `createdAt`) " +
                "VALUES ('Novo', 100.0, 1, 10, 1000)"
        )

        val stmt = connection.prepare("SELECT `iconKey` FROM `credit_cards`")
        assertTrue(stmt.step())
        assertEquals("card", stmt.getText(0))
        stmt.close()
    }
}
