package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// 1.4.0-rc02
object Migration3To4 : Migration(3, 4) {
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

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`title` TEXT, " +
                "`dayOfMonth` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`accountId` INTEGER, " +
                "`creditCardId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `recurring_new` (`id`, `type`, `amount`, `title`, `dayOfMonth`, `categoryId`, `accountId`, `creditCardId`, `createdAt`, `isActive`) " +
                "SELECT `id`, `type`, `amount`, `title`, `dayOfMonth`, `categoryId`, `accountId`, `creditCardId`, `createdAt`, 1 " +
                "FROM `recurring`"
        )
        connection.execSQL("DROP TABLE `recurring`")
        connection.execSQL("ALTER TABLE `recurring_new` RENAME TO `recurring`")

        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_categoryId` ON `operations` (`categoryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_sourceAccountId` ON `operations` (`sourceAccountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_targetCreditCardId` ON `operations` (`targetCreditCardId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_targetInvoiceId` ON `operations` (`targetInvoiceId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_recurringId` ON `operations` (`recurringId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_recurringCycle` ON `operations` (`recurringCycle`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_operations_installmentId` ON `operations` (`installmentId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_creditCardId` ON `recurring` (`creditCardId`)")
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
