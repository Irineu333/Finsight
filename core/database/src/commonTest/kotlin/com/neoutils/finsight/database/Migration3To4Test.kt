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

class Migration3To4Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        // Supporting tables (FK targets — not directly touched by migration)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL" +
                ")"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL" +
                ")"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `credit_cards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL" +
                ")"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `invoices` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" +
                ")"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `installments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" +
                ")"
        )

        // v3 `operations` table (columns before restructuring)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `operations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT, " +
                "`date` TEXT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`sourceAccountId` INTEGER, " +
                "`targetCreditCardId` INTEGER, " +
                "`targetInvoiceId` INTEGER, " +
                "`installmentId` INTEGER, " +
                "`installmentNumber` INTEGER" +
                ")"
        )

        // v3 `recurring` table (columns before restructuring — added by MIGRATION_2_3)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`title` TEXT, " +
                "`dayOfMonth` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`accountId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`lastHandledYearMonth` TEXT, " +
                "`creditCardId` INTEGER" +
                ")"
        )
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    // --- operations table ---

    @Test
    fun `given database at version 3 when migrated to 4 then operations table still exists`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue(connection.tableExists("operations"))
    }

    @Test
    fun `given database at version 3 when migrated to 4 then operations has recurringId column`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue("recurringId" in connection.getColumns("operations"))
    }

    @Test
    fun `given database at version 3 when migrated to 4 then operations has recurringCycle column`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue("recurringCycle" in connection.getColumns("operations"))
    }

    @Test
    fun `given existing operations when migrated to 4 then operations data is preserved`() {
        connection.execSQL(
            "INSERT INTO `operations` (`kind`, `date`) VALUES ('EXPENSE', '2024-01-15')"
        )

        MIGRATION_3_4.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `operations`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    // --- recurring table ---

    @Test
    fun `given database at version 3 when migrated to 4 then recurring no longer has lastHandledYearMonth`() {
        MIGRATION_3_4.migrate(connection)

        assertFalse("lastHandledYearMonth" in connection.getColumns("recurring"))
    }

    @Test
    fun `given database at version 3 when migrated to 4 then recurring has isActive column`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue("isActive" in connection.getColumns("recurring"))
    }

    @Test
    fun `given existing recurring when migrated to 4 then all recurring have isActive set to 1`() {
        connection.execSQL(
            "INSERT INTO `recurring` (`type`, `amount`, `dayOfMonth`, `createdAt`) " +
                "VALUES ('EXPENSE', 100.0, 15, 1000)"
        )

        MIGRATION_3_4.migrate(connection)

        val stmt = connection.prepare("SELECT `isActive` FROM `recurring`")
        assertTrue(stmt.step())
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    @Test
    fun `given existing recurring data when migrated to 4 then recurring data is preserved`() {
        connection.execSQL(
            "INSERT INTO `recurring` (`type`, `amount`, `dayOfMonth`, `createdAt`) " +
                "VALUES ('INCOME', 200.0, 5, 1000)"
        )

        MIGRATION_3_4.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `recurring`")
        stmt.step()
        assertEquals(1L, stmt.getLong(0))
        stmt.close()
    }

    // --- recurring_occurrences table ---

    @Test
    fun `given database at version 3 when migrated to 4 then recurring_occurrences table is created`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue(connection.tableExists("recurring_occurrences"))
    }

    @Test
    fun `given database at version 3 when migrated to 4 then recurring_occurrences has all required columns`() {
        MIGRATION_3_4.migrate(connection)

        val columns = connection.getColumns("recurring_occurrences")
        assertTrue("id" in columns)
        assertTrue("recurringId" in columns)
        assertTrue("cycleNumber" in columns)
        assertTrue("yearMonth" in columns)
        assertTrue("status" in columns)
        assertTrue("operationId" in columns)
        assertTrue("effectiveDate" in columns)
        assertTrue("handledAt" in columns)
    }

    @Test
    fun `given recurring with lastHandledYearMonth when migrated to 4 then a SKIPPED occurrence is created`() {
        // 2024-01-15 12:00:00 UTC — noon avoids local-time edge cases across timezones
        connection.execSQL(
            "INSERT INTO `recurring` (`type`, `amount`, `dayOfMonth`, `createdAt`, `lastHandledYearMonth`) " +
                "VALUES ('EXPENSE', 100.0, 15, 1705320000000, '2024-01')"
        )

        MIGRATION_3_4.migrate(connection)

        val stmt = connection.prepare(
            "SELECT `status`, `operationId`, `yearMonth` FROM `recurring_occurrences`"
        )
        assertTrue(stmt.step(), "Expected at least one recurring_occurrence row")
        assertEquals("SKIPPED", stmt.getText(0))
        assertTrue(stmt.isNull(1))
        assertEquals("2024-01", stmt.getText(2))
        stmt.close()
    }

    @Test
    fun `given recurring without lastHandledYearMonth when migrated to 4 then no occurrence is created`() {
        connection.execSQL(
            "INSERT INTO `recurring` (`type`, `amount`, `dayOfMonth`, `createdAt`) " +
                "VALUES ('EXPENSE', 100.0, 15, 1000)"
        )

        MIGRATION_3_4.migrate(connection)

        val stmt = connection.prepare("SELECT COUNT(*) FROM `recurring_occurrences`")
        stmt.step()
        assertEquals(0L, stmt.getLong(0))
        stmt.close()
    }

    // --- indexes on operations ---

    @Test
    fun `given database at version 3 when migrated to 4 then all operations indexes are created`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue(connection.indexExists("index_operations_categoryId"))
        assertTrue(connection.indexExists("index_operations_sourceAccountId"))
        assertTrue(connection.indexExists("index_operations_targetCreditCardId"))
        assertTrue(connection.indexExists("index_operations_targetInvoiceId"))
        assertTrue(connection.indexExists("index_operations_recurringId"))
        assertTrue(connection.indexExists("index_operations_recurringCycle"))
        assertTrue(connection.indexExists("index_operations_installmentId"))
    }

    // --- indexes on recurring ---

    @Test
    fun `given database at version 3 when migrated to 4 then all recurring indexes are created`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue(connection.indexExists("index_recurring_categoryId"))
        assertTrue(connection.indexExists("index_recurring_accountId"))
        assertTrue(connection.indexExists("index_recurring_creditCardId"))
    }

    // --- indexes on recurring_occurrences ---

    @Test
    fun `given database at version 3 when migrated to 4 then all recurring_occurrences indexes are created`() {
        MIGRATION_3_4.migrate(connection)

        assertTrue(connection.indexExists("index_recurring_occurrences_recurringId"))
        assertTrue(connection.indexExists("index_recurring_occurrences_operationId"))
        assertTrue(connection.indexExists("index_recurring_occurrences_recurringId_yearMonth"))
        assertTrue(connection.indexExists("index_recurring_occurrences_recurringId_cycleNumber"))
    }
}
