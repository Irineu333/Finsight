package com.neoutils.finsight.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`iconCategoryId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`period` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_categories` (" +
                "`budgetId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`budgetId`, `categoryId`), " +
                "FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_budget_categories_budgetId` ON `budget_categories` (`budgetId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_budget_categories_categoryId` ON `budget_categories` (`categoryId`)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
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
                "`creditCardId` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_creditCardId` ON `recurring` (`creditCardId`)"
        )
    }
}

// Versions 4-8 only existed in local WIP history, so their transient schema changes are
// collapsed into the single public upgrade path from v3 to the final v9 schema.
val MIGRATION_3_9 = object : Migration(3, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys=OFF")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `operations_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT, " +
                "`date` TEXT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`sourceAccountId` INTEGER, " +
                "`targetCreditCardId` INTEGER, " +
                "`targetInvoiceId` INTEGER, " +
                "`recurringId` INTEGER, " +
                "`recurringCycle` INTEGER, " +
                "`installmentId` INTEGER, " +
                "`installmentNumber` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`sourceAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`targetCreditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`targetInvoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`installmentId`) REFERENCES `installments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `operations_new` (`id`, `kind`, `title`, `date`, `categoryId`, `sourceAccountId`, `targetCreditCardId`, `targetInvoiceId`, `installmentId`, `installmentNumber`) " +
                "SELECT `id`, `kind`, `title`, `date`, `categoryId`, `sourceAccountId`, `targetCreditCardId`, `targetInvoiceId`, `installmentId`, `installmentNumber` " +
                "FROM `operations`"
        )
        connection.execSQL("DROP TABLE `operations`")
        connection.execSQL("ALTER TABLE `operations_new` RENAME TO `operations`")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_occurrences` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recurringId` INTEGER NOT NULL, " +
                "`cycleNumber` INTEGER NOT NULL, " +
                "`yearMonth` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`operationId` INTEGER, " +
                "`effectiveDate` TEXT NOT NULL, " +
                "`handledAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`operationId`) REFERENCES `operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `recurring_occurrences` (`recurringId`, `cycleNumber`, `yearMonth`, `status`, `operationId`, `effectiveDate`, `handledAt`) " +
                "SELECT " +
                "`id`, " +
                "((CAST(substr(`lastHandledYearMonth`, 1, 4) AS INTEGER) - CAST(strftime('%Y', `createdAt` / 1000, 'unixepoch', 'localtime') AS INTEGER)) * 12) + " +
                "(CAST(substr(`lastHandledYearMonth`, 6, 2) AS INTEGER) - CAST(strftime('%m', `createdAt` / 1000, 'unixepoch', 'localtime') AS INTEGER)) + 1, " +
                "`lastHandledYearMonth`, " +
                "'SKIPPED', " +
                "NULL, " +
                "`lastHandledYearMonth` || '-' || printf('%02d', MIN(`dayOfMonth`, CAST(strftime('%d', date(`lastHandledYearMonth` || '-01', 'start of month', '+1 month', '-1 day')) AS INTEGER))), " +
                "CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                "FROM `recurring` " +
                "WHERE `lastHandledYearMonth` IS NOT NULL"
        )

        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_categoryId` ON `operations` (`categoryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_sourceAccountId` ON `operations` (`sourceAccountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_targetCreditCardId` ON `operations` (`targetCreditCardId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_targetInvoiceId` ON `operations` (`targetInvoiceId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_recurringId` ON `operations` (`recurringId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_recurringCycle` ON `operations` (`recurringCycle`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_installmentId` ON `operations` (`installmentId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId` ON `recurring_occurrences` (`recurringId`)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_operationId` ON `recurring_occurrences` (`operationId`)")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_yearMonth` ON `recurring_occurrences` (`recurringId`, `yearMonth`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_cycleNumber` ON `recurring_occurrences` (`recurringId`, `cycleNumber`)"
        )

        connection.execSQL("PRAGMA foreign_keys=ON")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `recurring` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1"
        )
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_9, MIGRATION_9_10)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
