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

class Migration5To6Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`isDefault` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL" +
                ")"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    @Test
    fun `given database at version 5 when migrated to 6 then accounts has iconKey column`() {
        MIGRATION_5_6.migrate(connection)

        assertTrue("iconKey" in connection.getColumns("accounts"))
    }

    @Test
    fun `given database at version 5 before migration then accounts does not have iconKey column`() {
        assertFalse("iconKey" in connection.getColumns("accounts"))
    }

    @Test
    fun `given existing account when migrated to 6 then iconKey defaults to default`() {
        connection.execSQL(
            "INSERT INTO `accounts` (`name`, `isDefault`, `createdAt`) " +
                "VALUES ('Wallet', 1, 1000)"
        )

        MIGRATION_5_6.migrate(connection)

        val stmt = connection.prepare("SELECT `iconKey` FROM `accounts`")
        assertTrue(stmt.step())
        assertEquals("default", stmt.getText(0))
        stmt.close()
    }

    @Test
    fun `given existing account when migrated to 6 then previous columns are preserved`() {
        connection.execSQL(
            "INSERT INTO `accounts` (`name`, `isDefault`, `createdAt`) " +
                "VALUES ('Savings', 0, 2000)"
        )

        MIGRATION_5_6.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `name`, `isDefault`, `createdAt` FROM `accounts`"
        )
        assertTrue(stmt.step())
        assertEquals("Savings", stmt.getText(0))
        assertEquals(0L, stmt.getLong(1))
        assertEquals(2000L, stmt.getLong(2))
        stmt.close()
    }
}
