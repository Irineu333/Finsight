package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration6To7Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        // v6 `budgets` table (without `limitType`, `percentage` and `recurringId`)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`iconCategoryId` INTEGER NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
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
    fun `given database at version 6 when migrated to 7 then budgets table still exists`() {
        MIGRATION_6_7.migrate(connection)

        assertTrue(connection.tableExists("budgets"))
    }

    @Test
    fun `given database at version 6 when migrated to 7 then budgets has the three new columns`() {
        MIGRATION_6_7.migrate(connection)

        val columns = connection.getColumns("budgets")
        assertTrue("limitType" in columns)
        assertTrue("percentage" in columns)
        assertTrue("recurringId" in columns)
    }

    @Test
    fun `given existing budget when migrated to 7 then budget data is preserved`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`id`, `categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 3, 3, 'food_icon', 'Food', 500.0, 'MONTHLY', 1000)"
        )

        MIGRATION_6_7.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `id`, `categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `createdAt` FROM `budgets`"
        )
        assertTrue(stmt.step())
        assertEquals(1L, stmt.getLong(0))
        assertEquals(3L, stmt.getLong(1))
        assertEquals(3L, stmt.getLong(2))
        assertEquals("food_icon", stmt.getText(3))
        assertEquals("Food", stmt.getText(4))
        assertEquals(500.0, stmt.getDouble(5))
        assertEquals("MONTHLY", stmt.getText(6))
        assertEquals(1000L, stmt.getLong(7))
        stmt.close()
    }

    @Test
    fun `given existing budget when migrated to 7 then limitType defaults to FIXED`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'default', 'Food', 500.0, 'MONTHLY', 1000)"
        )

        MIGRATION_6_7.migrate(connection)

        val stmt = connection.prepare("SELECT `limitType` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("FIXED", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given existing budget when migrated to 7 then percentage and recurringId are null`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'default', 'Food', 500.0, 'MONTHLY', 1000)"
        )

        MIGRATION_6_7.migrate(connection)

        val stmt = connection.prepare("SELECT `percentage`, `recurringId` FROM `budgets`")
        assertTrue(stmt.step())
        assertTrue(stmt.isNull(0))
        assertTrue(stmt.isNull(1))
        stmt.close()
    }

    @Test
    fun `given several budgets when migrated to 7 then every row keeps the FIXED default`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `createdAt`) VALUES " +
                "(1, 1, 'default', 'Food', 500.0, 'MONTHLY', 1000), " +
                "(2, 2, 'car', 'Transport', 300.0, 'MONTHLY', 2000), " +
                "(3, 3, 'home', 'Home', 1200.0, 'YEARLY', 3000)"
        )

        MIGRATION_6_7.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `budgets` WHERE `limitType` = 'FIXED'")
        stmt.step()
        assertEquals(3L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given database at version 6 when migrated to 7 then a percentage budget can be written`() {
        MIGRATION_6_7.migrate(connection)

        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `limitType`, `percentage`, `recurringId`, `createdAt`) " +
                "VALUES (1, 1, 'default', 'Food', 0.0, 'MONTHLY', 'PERCENTAGE', 30.0, 7, 1000)"
        )

        val stmt = connection.prepare("SELECT `limitType`, `percentage`, `recurringId` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("PERCENTAGE", stmt.getText(0))
        assertEquals(30.0, stmt.getDouble(1))
        assertEquals(7L, stmt.getLong(2))
        stmt.close()
    }
}
