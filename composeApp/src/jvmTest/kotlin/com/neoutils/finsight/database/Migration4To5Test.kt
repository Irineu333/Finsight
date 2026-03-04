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

class Migration4To5Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`iconCategoryId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`period` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 4 when migrated to 5 then budgets has iconKey column`() {
        MIGRATION_4_5.migrate(connection)

        assertTrue("iconKey" in connection.getColumns("budgets"))
    }

    @Test
    fun `given database at version 4 before migration then budgets does not have iconKey column`() {
        assertFalse("iconKey" in connection.getColumns("budgets"))
    }

    @Test
    fun `given budget with no matching category when migrated to 5 then iconKey defaults to default`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 999, 'Food Budget', 500.0, '2024-01', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("default", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given budget with matching category when migrated to 5 then iconKey is populated from category`() {
        connection.execSQL(
            "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES (1, 'Food', 'food_icon', 'EXPENSE', 1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'Food Budget', 500.0, '2024-01', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("food_icon", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given existing budget when migrated to 5 then other columns are preserved`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'Groceries', 300.0, '2024-03', 2000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `title`, `amount`, `period`, `createdAt` FROM `budgets`"
        )
        assertTrue(stmt.step())
        assertEquals("Groceries", stmt.getText(0))
        assertEquals(300.0, stmt.getDouble(1))
        assertEquals("2024-03", stmt.getText(2))
        assertEquals(2000L, stmt.getLong(3))
        stmt.close()
    }

    @Test
    fun `given multiple budgets with mixed category matches when migrated to 5 then each gets correct iconKey`() {
        connection.execSQL(
            "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES (10, 'Transport', 'car_icon', 'EXPENSE', 1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (10, 10, 'Transport Budget', 200.0, '2024-01', 1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (10, 999, 'Unknown Budget', 100.0, '2024-01', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `title`, `iconKey` FROM `budgets` ORDER BY `id`"
        )
        assertTrue(stmt.step())
        assertEquals("Transport Budget", stmt.getText(0))
        assertEquals("car_icon", stmt.getText(1))

        assertTrue(stmt.step())
        assertEquals("Unknown Budget", stmt.getText(0))
        assertEquals("default", stmt.getText(1))

        stmt.close()
    }
}
