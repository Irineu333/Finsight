package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration4To5Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        // v4 `categories` table (FK target for budgets.iconCategoryId)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
        )

        // v4 `budgets` table (without `iconKey`)
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

        // v4 `accounts` table (without `iconKey`)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL" +
                ")"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    // --- budgets table ---

    @Test
    fun `given database at version 4 when migrated to 5 then budgets table still exists`() {
        MIGRATION_4_5.migrate(connection)

        assertTrue(connection.tableExists("budgets"))
    }

    @Test
    fun `given database at version 4 when migrated to 5 then budgets has iconKey column`() {
        MIGRATION_4_5.migrate(connection)

        assertTrue("iconKey" in connection.getColumns("budgets"))
    }

    @Test
    fun `given existing budget when migrated to 5 then budget data is preserved`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'Food', 500.0, 'MONTHLY', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `budgets`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given budget with matching category when migrated to 5 then iconKey is copied from category`() {
        connection.execSQL(
            "INSERT INTO `categories` (`id`, `name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES (1, 'Food', 'food_icon', 'EXPENSE', 1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'Food', 500.0, 'MONTHLY', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("food_icon", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given budget with no matching category when migrated to 5 then iconKey defaults to default`() {
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (99, 99, 'Unknown', 100.0, 'MONTHLY', 1000)"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `budgets`")
        assertTrue(stmt.step())
        assertEquals("default", stmt.getText(0))
        stmt.close()
    }

    // --- accounts table ---

    @Test
    fun `given database at version 4 when migrated to 5 then accounts table still exists`() {
        MIGRATION_4_5.migrate(connection)

        assertTrue(connection.tableExists("accounts"))
    }

    @Test
    fun `given database at version 4 when migrated to 5 then accounts has iconKey column`() {
        MIGRATION_4_5.migrate(connection)

        assertTrue("iconKey" in connection.getColumns("accounts"))
    }

    @Test
    fun `given existing account when migrated to 5 then account data is preserved`() {
        connection.execSQL(
            "INSERT INTO `accounts` (`name`) VALUES ('Checking')"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `accounts`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given existing account when migrated to 5 then iconKey defaults to default`() {
        connection.execSQL(
            "INSERT INTO `accounts` (`name`) VALUES ('Savings')"
        )

        MIGRATION_4_5.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `accounts`")
        assertTrue(stmt.step())
        assertEquals("default", stmt.getText(0))
        stmt.close()
    }
}
