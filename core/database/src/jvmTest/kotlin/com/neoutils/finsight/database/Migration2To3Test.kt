package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration2To3Test {

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
        MIGRATION_1_2.migrate(connection)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 2 when migrated to 3 then recurring table is created`() {
        MIGRATION_2_3.migrate(connection)

        assertTrue(connection.tableExists("recurring"))
    }

    @Test
    fun `given database at version 2 when migrated to 3 then recurring has all required columns`() {
        MIGRATION_2_3.migrate(connection)

        val columns = connection.getColumns("recurring")
        assertTrue("id" in columns)
        assertTrue("type" in columns)
        assertTrue("amount" in columns)
        assertTrue("title" in columns)
        assertTrue("dayOfMonth" in columns)
        assertTrue("categoryId" in columns)
        assertTrue("accountId" in columns)
        assertTrue("createdAt" in columns)
        assertTrue("lastHandledYearMonth" in columns)
        assertTrue("creditCardId" in columns)
        assertTrue("isActive" !in columns, "isActive belongs to v4 schema, must not exist after migration 2→3")
    }

    @Test
    fun `given database at version 2 when migrated to 3 then index on recurring categoryId is created`() {
        MIGRATION_2_3.migrate(connection)

        assertTrue(connection.indexExists("index_recurring_categoryId"))
    }

    @Test
    fun `given database at version 2 when migrated to 3 then index on recurring accountId is created`() {
        MIGRATION_2_3.migrate(connection)

        assertTrue(connection.indexExists("index_recurring_accountId"))
    }

    @Test
    fun `given database at version 2 when migrated to 3 then index on recurring creditCardId is created`() {
        MIGRATION_2_3.migrate(connection)

        assertTrue(connection.indexExists("index_recurring_creditCardId"))
    }

    @Test
    fun `given existing categories when migrated to 3 then categories data is preserved`() {
        connection.execSQL(
            "INSERT INTO `categories` (`name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES ('Food', 'food_icon', 'EXPENSE', 1000)"
        )

        MIGRATION_2_3.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `categories`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given existing budgets when migrated to 3 then budgets data is preserved`() {
        connection.execSQL(
            "INSERT INTO `categories` (`name`, `iconKey`, `type`, `createdAt`) " +
                "VALUES ('Food', 'food_icon', 'EXPENSE', 1000)"
        )
        connection.execSQL(
            "INSERT INTO `budgets` (`categoryId`, `iconCategoryId`, `title`, `amount`, `period`, `createdAt`) " +
                "VALUES (1, 1, 'Food Budget', 500.0, '2024-01', 1000)"
        )

        MIGRATION_2_3.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `budgets`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }
}
