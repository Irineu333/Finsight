package com.neoutils.finsight.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

// 1.2.0
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

// 1.4.0-rc01
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

// 1.4.0-rc02
val MIGRATION_3_4 = object : Migration(3, 4) {
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

// 1.4.0
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'default'"
        )
        connection.execSQL(
            "UPDATE `budgets` " +
                    "SET `iconKey` = COALESCE((" +
                    "SELECT `iconKey` FROM `categories` " +
                    "WHERE `categories`.`id` = `budgets`.`iconCategoryId`" +
                    "), 'default')"
        )
        connection.execSQL(
            "ALTER TABLE `accounts` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'default'"
        )
    }
}

// 1.5.0-rc01
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `credit_cards` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'card'"
        )
    }
}

// 1.5.0-rc04
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `limitType` TEXT NOT NULL DEFAULT 'FIXED'"
        )
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `percentage` REAL"
        )
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `recurringId` INTEGER"
        )
    }
}

// 1.6.0 — balanced double-entry ledger foundation.
// Promotes every category and credit card into the unified chart of accounts,
// seeds EQUITY system accounts, and synthesizes balanced entries from the legacy
// single/dual-leg transactions (Double reais -> Long cents, debit-positive sign).
// System-account names mirror `SystemAccount` in :core:model.
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        // --- 1. Extend the chart of accounts ---
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'ASSET'")
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'BRL'")

        // --- 2. Facade back-references (category/card -> its ledger account) ---
        connection.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `accountId` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL(
            "ALTER TABLE `credit_cards` ADD COLUMN `accountId` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_accountId` ON `categories` (`accountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_cards_accountId` ON `credit_cards` (`accountId`)")

        // --- 3. The ledger entries table ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`operationId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL DEFAULT 'BRL', " +
                "`invoiceId` INTEGER, " +
                "FOREIGN KEY(`operationId`) REFERENCES `operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_operationId` ON `entries` (`operationId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_accountId` ON `entries` (`accountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_invoiceId` ON `entries` (`invoiceId`)")

        // --- 4. Promote categories to INCOME/EXPENSE accounts (ids disjoint via captured offset) ---
        connection.execSQL("CREATE TEMP TABLE `_cat_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`) " +
                "SELECT (SELECT base FROM `_cat_base`) + c.`id`, c.`name`, c.`type`, 'BRL', c.`iconKey`, 0, c.`createdAt` " +
                "FROM `categories` c"
        )
        connection.execSQL("UPDATE `categories` SET `accountId` = (SELECT base FROM `_cat_base`) + `id`")

        // --- 5. Promote credit cards to LIABILITY accounts ---
        connection.execSQL("CREATE TEMP TABLE `_cc_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`) " +
                "SELECT (SELECT base FROM `_cc_base`) + cc.`id`, cc.`name`, 'LIABILITY', 'BRL', cc.`iconKey`, 0, cc.`createdAt` " +
                "FROM `credit_cards` cc"
        )
        connection.execSQL("UPDATE `credit_cards` SET `accountId` = (SELECT base FROM `_cc_base`) + `id`")

        // --- 6. Seed EQUITY + uncategorized system accounts (ids base+1..+4) ---
        connection.execSQL("CREATE TEMP TABLE `_sys` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`) VALUES " +
                "((SELECT base FROM `_sys`) + 1, 'Reconciliação', 'EQUITY', 'BRL', 'wallet', 0, CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                "((SELECT base FROM `_sys`) + 2, 'Saldo Inicial', 'EQUITY', 'BRL', 'wallet', 0, CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                "((SELECT base FROM `_sys`) + 3, 'Sem categoria (despesa)', 'EXPENSE', 'BRL', 'default', 0, CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                "((SELECT base FROM `_sys`) + 4, 'Sem categoria (receita)', 'INCOME', 'BRL', 'default', 0, CAST(strftime('%s','now') AS INTEGER) * 1000)"
        )

        // --- 7. Real-leg entry for every legacy transaction (debit-positive cents = signedImpact * 100) ---
        connection.execSQL(
            "INSERT INTO `entries` (`operationId`, `accountId`, `amount`, `currency`, `invoiceId`) " +
                "SELECT t.`operationId`, " +
                "CASE t.`target` WHEN 'ACCOUNT' THEN t.`accountId` " +
                "ELSE (SELECT cc.`accountId` FROM `credit_cards` cc WHERE cc.`id` = t.`creditCardId`) END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN -CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL', " +
                "t.`invoiceId` " +
                "FROM `transactions` t WHERE t.`operationId` IS NOT NULL"
        )

        // --- 8. Synthesized contra-leg for single-transaction operations (contra = -real leg) ---
        connection.execSQL(
            "INSERT INTO `entries` (`operationId`, `accountId`, `amount`, `currency`) " +
                "SELECT t.`operationId`, " +
                "CASE t.`type` " +
                "WHEN 'ADJUSTMENT' THEN (SELECT base FROM `_sys`) + 1 " +
                "WHEN 'EXPENSE' THEN COALESCE((SELECT c.`accountId` FROM `categories` c WHERE c.`id` = t.`categoryId`), (SELECT base FROM `_sys`) + 3) " +
                "WHEN 'INCOME' THEN COALESCE((SELECT c.`accountId` FROM `categories` c WHERE c.`id` = t.`categoryId`), (SELECT base FROM `_sys`) + 4) " +
                "END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE -CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL' " +
                "FROM `transactions` t " +
                "WHERE t.`operationId` IN (" +
                "SELECT `operationId` FROM `transactions` WHERE `operationId` IS NOT NULL GROUP BY `operationId` HAVING COUNT(*) = 1" +
                ")"
        )

        connection.execSQL("DROP TABLE `_cat_base`")
        connection.execSQL("DROP TABLE `_cc_base`")
        connection.execSQL("DROP TABLE `_sys`")
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
