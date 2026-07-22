package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The v10 migration rewrites accounting history: it collapses every per-category
 * account into two nominals and re-tags the legs with dimensions. What makes that
 * acceptable is that it is all-or-nothing — the checks that bracket it throw from
 * inside `migrate()`, and Room rolls the whole transaction back.
 *
 * This is the gate for that claim. The parity of the figures is asserted next door,
 * in `MigrationLedgerReadParityTest`; here the subject is the abort.
 */
class Migration9To10Test {

    private val file: File = File.createTempFile("finsight-v10", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `given an unbalanced ledger then the migration aborts and writes nothing`() {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            buildV9Fixture(connection)
            // One leg short of balancing: exactly the state a device could already be
            // in before v10 ever runs, which is why the check comes before the rewrite.
            connection.execSQL(
                "INSERT INTO `entries` (`transactionId`,`accountId`,`amount`,`currency`) VALUES (1, 1, -1, 'BRL')"
            )

            val failure = assertFailsWith<UnbalancedLedgerException> {
                MIGRATION_9_10.migrate(connection)
            }
            assertEquals(listOf(UnbalancedTransaction(1, "BRL", -1)), failure.offenders)
            assertTrue(failure.message!!.contains("before"))

            // Nothing was rewritten: the check runs before the first statement that
            // would have. (Room owns the transaction in production; here the point is
            // that the throw happens with the schema untouched.)
            assertFalse(connection.tableExists("dimensions"))
            assertTrue(connection.getColumns("categories").contains("accountId"))
            assertTrue(connection.getColumns("transactions").contains("categoryId"))
        }
    }

    @Test
    fun `given a balanced v9 ledger then categories become dimensions and their accounts are gone`() {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            buildV9Fixture(connection)

            MIGRATION_9_10.migrate(connection)

            // The facade kept its identity and gained a dimension of the right kind;
            // the closure flag it used to read off its account is now its own column.
            val foodDimension = connection.queryLong("SELECT `dimensionId` FROM `categories` WHERE `id` = 1")
            assertEquals(
                "CATEGORY",
                connection.queryText("SELECT `kind` FROM `dimensions` WHERE `id` = $foodDimension"),
            )
            assertEquals(0L, connection.queryLong("SELECT `isArchived` FROM `categories` WHERE `id` = 1"))
            assertFalse(connection.getColumns("categories").contains("accountId"))
            assertFalse(connection.getColumns("transactions").contains("categoryId"))

            // The chart no longer contains the category account nor the uncategorized
            // buckets — the guarded delete would have aborted had a leg still pointed
            // at one.
            assertEquals(
                0L,
                connection.queryLong("SELECT COUNT(*) FROM `accounts` WHERE `id` IN (2, 3, 4)"),
            )

            // Both legs landed on the single EXPENSE nominal, and only the categorized
            // one carries a dimension: "uncategorized" is the absence of one.
            val expenses = connection.queryLong("SELECT `id` FROM `accounts` WHERE `name` = 'Despesas'")
            assertEquals(
                5000L,
                connection.queryLong("SELECT COALESCE(SUM(`amount`),0) FROM `entries` WHERE `accountId` = $expenses"),
            )
            assertEquals(
                3000L,
                connection.queryLong("SELECT COALESCE(SUM(`amount`),0) FROM `entries` WHERE `dimensionId` = $foodDimension"),
            )
            assertEquals(
                2000L,
                connection.queryLong(
                    "SELECT COALESCE(SUM(`amount`),0) FROM `entries` WHERE `accountId` = $expenses AND `dimensionId` IS NULL"
                ),
            )
            // The asset side is untouched: no rewrite of the chart may move money.
            assertEquals(
                -5000L,
                connection.queryLong("SELECT COALESCE(SUM(`amount`),0) FROM `entries` WHERE `accountId` = 1"),
            )
        }
    }
}

/**
 * A minimal v9 database, written by hand rather than migrated from v7: the subject
 * here is what v10 does, and a fixture that states the v9 shape outright is the one
 * that keeps saying so when the earlier migrations change.
 *
 * It holds one categorized expense (Food), one uncategorized one, and the two
 * uncategorized bucket accounts `MIGRATION_7_9` seeds.
 */
private fun buildV9Fixture(connection: SQLiteConnection) {
    connection.execSQL(
        "CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `currency` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)"
    )
    connection.execSQL(
        "CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`iconKey` TEXT NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, " +
            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
    )
    connection.execSQL("CREATE INDEX `index_categories_accountId` ON `categories` (`accountId`)")
    connection.execSQL(
        "CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `date` TEXT NOT NULL, " +
            "`categoryId` INTEGER, `recurringId` INTEGER, `recurringCycle` INTEGER, `installmentId` INTEGER, " +
            "`installmentNumber` INTEGER, " +
            "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
    )
    connection.execSQL("CREATE INDEX `index_transactions_categoryId` ON `transactions` (`categoryId`)")
    connection.execSQL(
        "CREATE TABLE `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `creditCardId` INTEGER NOT NULL, " +
            "`openingMonth` TEXT NOT NULL, `closingMonth` TEXT NOT NULL, `dueMonth` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL)"
    )
    connection.execSQL(
        "CREATE TABLE `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, " +
            "`accountId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `invoiceId` INTEGER, " +
            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
            "FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
    )
    connection.execSQL("CREATE INDEX `index_entries_transactionId` ON `entries` (`transactionId`)")
    connection.execSQL("CREATE INDEX `index_entries_accountId` ON `entries` (`accountId`)")
    connection.execSQL("CREATE INDEX `index_entries_invoiceId` ON `entries` (`invoiceId`)")

    connection.execSQL(
        "INSERT INTO `accounts` (`id`,`name`,`type`,`currency`,`iconKey`,`isDefault`,`createdAt`,`isArchived`) VALUES " +
            "(1,'A','ASSET','BRL','wallet',1,1000,0), " +
            "(2,'Food','EXPENSE','BRL','food',0,1000,0), " +
            "(3,'Sem categoria (despesa)','EXPENSE','BRL','default',0,1000,0), " +
            "(4,'Sem categoria (receita)','INCOME','BRL','default',0,1000,0)"
    )
    connection.execSQL(
        "INSERT INTO `categories` (`id`,`name`,`iconKey`,`type`,`createdAt`,`accountId`) VALUES (1,'Food','food','EXPENSE',1000,2)"
    )
    connection.execSQL("INSERT INTO `transactions` (`id`,`title`,`date`,`categoryId`) VALUES (1,'Groceries','2026-01-10',1)")
    connection.execSQL(
        "INSERT INTO `entries` (`transactionId`,`accountId`,`amount`,`currency`) VALUES (1,1,-3000,'BRL'),(1,2,3000,'BRL')"
    )
    connection.execSQL("INSERT INTO `transactions` (`id`,`title`,`date`,`categoryId`) VALUES (2,'Whatever','2026-01-11',NULL)")
    connection.execSQL(
        "INSERT INTO `entries` (`transactionId`,`accountId`,`amount`,`currency`) VALUES (2,1,-2000,'BRL'),(2,3,2000,'BRL')"
    )
}

private fun SQLiteConnection.queryLong(sql: String): Long {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getLong(0)
    } finally {
        statement.close()
    }
}

private fun SQLiteConnection.queryText(sql: String): String {
    val statement = prepare(sql)
    try {
        statement.step()
        return statement.getText(0)
    } finally {
        statement.close()
    }
}
