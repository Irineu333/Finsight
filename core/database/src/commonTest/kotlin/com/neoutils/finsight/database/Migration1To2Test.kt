package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration1To2Test {

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
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 1 when migrated to 2 then budgets table is created`() {
        MIGRATION_1_2.migrate(connection)

        assertTrue(connection.tableExists("budgets"))
    }

    @Test
    fun `given database at version 1 when migrated to 2 then budget_categories table is created`() {
        MIGRATION_1_2.migrate(connection)

        assertTrue(connection.tableExists("budget_categories"))
    }

    @Test
    fun `given database at version 1 when migrated to 2 then budgets has all required columns`() {
        MIGRATION_1_2.migrate(connection)

        val columns = connection.getColumns("budgets")
        assertTrue("id" in columns)
        assertTrue("categoryId" in columns)
        assertTrue("iconCategoryId" in columns)
        assertTrue("title" in columns)
        assertTrue("amount" in columns)
        assertTrue("period" in columns)
        assertTrue("createdAt" in columns)
        assertTrue("iconKey" !in columns, "iconKey belongs to v3 schema, must not exist after migration 1→2")
    }

    @Test
    fun `given database at version 1 when migrated to 2 then budget_categories has required columns`() {
        MIGRATION_1_2.migrate(connection)

        val columns = connection.getColumns("budget_categories")
        assertTrue("budgetId" in columns)
        assertTrue("categoryId" in columns)
    }

    @Test
    fun `given database at version 1 when migrated to 2 then index on budgets categoryId is created`() {
        MIGRATION_1_2.migrate(connection)

        assertTrue(connection.indexExists("index_budgets_categoryId"))
    }

    @Test
    fun `given database at version 1 when migrated to 2 then indexes on budget_categories are created`() {
        MIGRATION_1_2.migrate(connection)

        assertTrue(connection.indexExists("index_budget_categories_budgetId"))
        assertTrue(connection.indexExists("index_budget_categories_categoryId"))
    }

    @Test
    fun `given existing categories when migrated to 2 then categories data is preserved`() {
        connection.execSQL(
            "INSERT INTO `categories` (`name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES ('Food', 'food_icon', 'EXPENSE', 1000)"
        )

        MIGRATION_1_2.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `categories`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }
}
