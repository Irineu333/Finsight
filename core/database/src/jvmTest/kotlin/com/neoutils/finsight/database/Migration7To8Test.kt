package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Migration7To8Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")

        // v7 accounts (no type/currency yet)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `iconKey` TEXT NOT NULL DEFAULT 'wallet', " +
                "`isDefault` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL" +
                ")"
        )
        // v7 categories (no accountId yet)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL" +
                ")"
        )
        // v7 credit_cards (no accountId yet)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `credit_cards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `limit` REAL NOT NULL, `closingDay` INTEGER NOT NULL, " +
                "`dueDay` INTEGER NOT NULL, `iconKey` TEXT NOT NULL DEFAULT 'card', `createdAt` INTEGER NOT NULL" +
                ")"
        )
        // FK-target tables present in the real v7 schema.
        connection.execSQL("CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `installments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `operations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT, `date` TEXT NOT NULL, " +
                "`categoryId` INTEGER, `sourceAccountId` INTEGER, `targetCreditCardId` INTEGER, `targetInvoiceId` INTEGER, " +
                "`recurringId` INTEGER, `recurringCycle` INTEGER, `installmentId` INTEGER, `installmentNumber` INTEGER)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operationId` INTEGER, " +
                "`type` TEXT NOT NULL, `amount` REAL NOT NULL, `title` TEXT, `date` TEXT NOT NULL, " +
                "`categoryId` INTEGER, `target` TEXT NOT NULL DEFAULT 'ACCOUNT', " +
                "`creditCardId` INTEGER, `invoiceId` INTEGER, `accountId` INTEGER" +
                ")"
        )

        // Sample data: accounts A(1), B(2); category Food(1, EXPENSE)
        connection.execSQL("INSERT INTO `accounts` (`id`,`name`,`iconKey`,`isDefault`,`createdAt`) VALUES (1,'A','wallet',1,1000),(2,'B','wallet',0,1000)")
        connection.execSQL("INSERT INTO `categories` (`id`,`name`,`iconKey`,`type`,`createdAt`) VALUES (1,'Food','food','EXPENSE',1000)")

        // op1: expense 50 from A, category Food (single leg)
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (1,'TRANSACTION','2024-01-10')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`categoryId`,`target`,`accountId`) VALUES (1,'EXPENSE',50.0,'2024-01-10',1,'ACCOUNT',1)")

        // op2: transfer 100 A->B (two legs, already balanced)
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (2,'TRANSFER','2024-01-11')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (2,'EXPENSE',100.0,'2024-01-11','ACCOUNT',1)")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (2,'INCOME',100.0,'2024-01-11','ACCOUNT',2)")

        // op3: adjustment +30 on A (single leg)
        connection.execSQL("INSERT INTO `operations` (`id`,`kind`,`date`) VALUES (3,'TRANSACTION','2024-01-12')")
        connection.execSQL("INSERT INTO `transactions` (`operationId`,`type`,`amount`,`date`,`target`,`accountId`) VALUES (3,'ADJUSTMENT',30.0,'2024-01-12','ACCOUNT',1)")
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun scalar(sql: String): Long {
        val stmt = connection.prepare(sql)
        stmt.step()
        val value = stmt.getLong(0)
        stmt.close()
        return value
    }

    @Test
    fun `given v7 when migrated then entries table and indices are created`() {
        MIGRATION_7_8.migrate(connection)

        assertTrue(connection.tableExists("entries"))
        assertTrue(connection.indexExists("index_entries_operationId"))
        assertTrue(connection.indexExists("index_entries_accountId"))
    }

    @Test
    fun `given v7 when migrated then accounts gains type and currency columns`() {
        MIGRATION_7_8.migrate(connection)

        val columns = connection.getColumns("accounts")
        assertTrue("type" in columns)
        assertTrue("currency" in columns)
    }

    @Test
    fun `given a category when migrated then it is promoted to an EXPENSE account and linked`() {
        MIGRATION_7_8.migrate(connection)

        val accountId = scalar("SELECT `accountId` FROM `categories` WHERE `id` = 1")
        assertTrue(accountId > 0, "category should be linked to a promoted account")
        val type = connection.prepare("SELECT `type` FROM `accounts` WHERE `id` = $accountId")
        type.step()
        assertEquals("EXPENSE", type.getText(0))
        type.close()
    }

    @Test
    fun `given legacy operations when migrated then every operation sums to zero`() {
        MIGRATION_7_8.migrate(connection)

        // Number of operations whose entries do NOT sum to zero must be zero.
        val unbalanced = scalar(
            "SELECT COUNT(*) FROM (" +
                "SELECT `operationId`, SUM(`amount`) AS s FROM `entries` GROUP BY `operationId` HAVING s <> 0" +
                ")"
        )
        assertEquals(0L, unbalanced)
    }

    @Test
    fun `given legacy transactions when migrated then account balance is preserved in cents`() {
        MIGRATION_7_8.migrate(connection)

        // Legacy signed balance of A = -50 (expense) - 100 (transfer out) + 30 (adjustment) = -120.00 -> -12000 cents.
        val balanceA = scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 1")
        assertEquals(-12000L, balanceA)

        // B received the transfer: +100.00 -> +10000 cents.
        val balanceB = scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 2")
        assertEquals(10000L, balanceB)
    }

    @Test
    fun `given the whole ledger when migrated then it sums to zero`() {
        MIGRATION_7_8.migrate(connection)

        assertEquals(0L, scalar("SELECT COALESCE(SUM(`amount`), 0) FROM `entries`"))
    }

    @Test
    fun `given an adjustment when migrated then its contra is the reconciliation equity account`() {
        MIGRATION_7_8.migrate(connection)

        // op3 has two entries: +3000 on A and -3000 on the reconciliation EQUITY account.
        val reconciliationSum = scalar(
            "SELECT COALESCE(SUM(e.`amount`), 0) FROM `entries` e " +
                "JOIN `accounts` a ON a.`id` = e.`accountId` " +
                "WHERE a.`type` = 'EQUITY' AND a.`name` = 'Reconciliação'"
        )
        assertEquals(-3000L, reconciliationSum)
    }
}
