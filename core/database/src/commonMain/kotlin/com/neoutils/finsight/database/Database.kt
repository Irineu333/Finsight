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

// 1.6.0 — the balanced double-entry ledger becomes the single source of truth.
//
// A single v7 -> v9 step: v8 never shipped, so there is no device to carry through
// it and no reason to inherit its two mistakes (a 'Saldo Inicial' account nothing
// referenced, and routing the legs of deleted accounts into a 'Conta removida'
// bucket) only to correct them afterwards.
//
// It builds the chart of accounts, derives the ledger from the legacy legs,
// reconstructs accounts deleted in v7 as *closed* accounts carrying their real
// type plus a dated write-off, drops the legacy `transactions` table, and renames
// `operations` -> `transactions` so the aggregate finally owns the user's word.
// System-account names mirror `SystemAccount` in :core:model.
val MIGRATION_7_9 = object : Migration(7, 9) {
    override fun migrate(connection: SQLiteConnection) {
        val now = "CAST(strftime('%s','now') AS INTEGER) * 1000"

        connection.execSQL("PRAGMA foreign_keys=OFF")

        // --- 0. Rebuild operations: drop the now-derived `kind` and the denormalized
        //        `sourceAccountId`/`targetCreditCardId`/`targetInvoiceId`. Table rebuild
        //        because those columns carry foreign keys. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `operations_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT, " +
                "`date` TEXT NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`recurringId` INTEGER, " +
                "`recurringCycle` INTEGER, " +
                "`installmentId` INTEGER, " +
                "`installmentNumber` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`installmentId`) REFERENCES `installments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                "FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `operations_new` (`id`, `title`, `date`, `categoryId`, `recurringId`, `recurringCycle`, `installmentId`, `installmentNumber`) " +
                "SELECT `id`, `title`, `date`, `categoryId`, `recurringId`, `recurringCycle`, `installmentId`, `installmentNumber` FROM `operations`"
        )
        connection.execSQL("DROP TABLE `operations`")
        connection.execSQL("ALTER TABLE `operations_new` RENAME TO `operations`")

        // --- 1. Extend the chart of accounts. `isClosed` is the single closure flag
        //        (design D21): categories and cards read it through their accountId
        //        instead of each keeping a copy. ---
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'ASSET'")
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'BRL'")
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `isClosed` INTEGER NOT NULL DEFAULT 0")

        // --- 2. Facade back-references (category/card -> its ledger account) ---
        connection.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `accountId` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL(
            "ALTER TABLE `credit_cards` ADD COLUMN `accountId` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )

        // --- 3. Promote categories to INCOME/EXPENSE accounts (ids disjoint via captured offset) ---
        connection.execSQL("CREATE TEMP TABLE `_cat_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isClosed`) " +
                "SELECT (SELECT base FROM `_cat_base`) + c.`id`, c.`name`, c.`type`, 'BRL', c.`iconKey`, 0, c.`createdAt`, 0 " +
                "FROM `categories` c"
        )
        connection.execSQL("UPDATE `categories` SET `accountId` = (SELECT base FROM `_cat_base`) + `id`")

        // --- 4. Promote credit cards to LIABILITY accounts ---
        connection.execSQL("CREATE TEMP TABLE `_cc_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isClosed`) " +
                "SELECT (SELECT base FROM `_cc_base`) + cc.`id`, cc.`name`, 'LIABILITY', 'BRL', cc.`iconKey`, 0, cc.`createdAt`, 0 " +
                "FROM `credit_cards` cc"
        )
        connection.execSQL("UPDATE `credit_cards` SET `accountId` = (SELECT base FROM `_cc_base`) + `id`")

        // --- 5. System accounts. Only the ones with a real consumer: reconciliation
        //        (the counter-leg of every adjustment and write-off) and the two
        //        uncategorized buckets. No 'Saldo Inicial' — the app has no such
        //        concept — and no 'Conta removida': a deleted account is reconstructed
        //        below as itself, closed, instead of losing its identity in a bucket. ---
        connection.execSQL("CREATE TEMP TABLE `_sys` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isClosed`) VALUES " +
                "((SELECT base FROM `_sys`) + 1, 'Reconciliação', 'EQUITY', 'BRL', 'wallet', 0, $now, 0), " +
                "((SELECT base FROM `_sys`) + 2, 'Sem categoria (despesa)', 'EXPENSE', 'BRL', 'default', 0, $now, 0), " +
                "((SELECT base FROM `_sys`) + 3, 'Sem categoria (receita)', 'INCOME', 'BRL', 'default', 0, $now, 0)"
        )

        // --- 6. Reconstruct the accounts deleted in v7. Their legs survived with a
        //        NULL pointer (FK SET NULL), which would abort the upgrade on
        //        `entries.accountId NOT NULL`. The real type is recoverable from
        //        `transactions.target`; the name and the multiplicity are not, so all
        //        the orphans of a type collapse into one closed account. ---
        connection.execSQL("CREATE TEMP TABLE `_closed` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isClosed`) " +
                "SELECT (SELECT base FROM `_closed`) + 1, 'Conta encerrada', 'ASSET', 'BRL', 'wallet', 0, $now, 1 " +
                "WHERE EXISTS (SELECT 1 FROM `transactions` WHERE `target` = 'ACCOUNT' AND `accountId` IS NULL AND `operationId` IS NOT NULL)"
        )
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isClosed`) " +
                "SELECT (SELECT base FROM `_closed`) + 2, 'Cartão encerrado', 'LIABILITY', 'BRL', 'credit_card', 0, $now, 1 " +
                "WHERE EXISTS (" +
                "SELECT 1 FROM `transactions` t WHERE t.`target` = 'CREDIT_CARD' AND t.`operationId` IS NOT NULL " +
                "AND (SELECT cc.`accountId` FROM `credit_cards` cc WHERE cc.`id` = t.`creditCardId`) IS NULL)"
        )

        // --- 6b. A leg with no aggregate. `transactions.operationId` has been nullable
        //          since v1 and no migration ever backfilled it, so such a row can exist.
        //          Filtering it out would make its money disappear from the balance with
        //          no error and no trace; instead it gets the aggregate it never had,
        //          built from what the leg itself carries. ---
        connection.execSQL(
            "CREATE TEMP TABLE `_orphan_legs` AS " +
                "SELECT `id` AS legId, `title` AS legTitle, `date` AS legDate, `categoryId` AS legCategoryId, " +
                "(SELECT COALESCE(MAX(`id`), 0) FROM `operations`) + ROW_NUMBER() OVER (ORDER BY `id`) AS opId " +
                "FROM `transactions` WHERE `operationId` IS NULL"
        )
        connection.execSQL(
            "INSERT INTO `operations` (`id`, `title`, `date`, `categoryId`) " +
                "SELECT opId, legTitle, legDate, legCategoryId FROM `_orphan_legs`"
        )
        connection.execSQL(
            "UPDATE `transactions` SET `operationId` = " +
                "(SELECT opId FROM `_orphan_legs` WHERE legId = `transactions`.`id`) " +
                "WHERE `operationId` IS NULL"
        )
        connection.execSQL("DROP TABLE `_orphan_legs`")

        // --- 7. Build the ledger. `entries_build` carries no foreign keys because
        //        `operations` has not been renamed yet; the final table is created
        //        against the final names in step 10. ---
        connection.execSQL(
            "CREATE TABLE `entries_build` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL DEFAULT 'BRL', " +
                "`invoiceId` INTEGER)"
        )

        // The real leg of every legacy transaction (debit-positive cents).
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`, `invoiceId`) " +
                "SELECT t.`operationId`, " +
                "CASE t.`target` " +
                "WHEN 'ACCOUNT' THEN COALESCE(t.`accountId`, (SELECT base FROM `_closed`) + 1) " +
                "ELSE COALESCE((SELECT cc.`accountId` FROM `credit_cards` cc WHERE cc.`id` = t.`creditCardId`), (SELECT base FROM `_closed`) + 2) END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN -CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL', " +
                // Only the credit-card leg tags the invoice; a payment's account leg
                // also has invoiceId but must not, or the legs cancel the owed sum.
                "CASE WHEN t.`target` = 'CREDIT_CARD' THEN t.`invoiceId` ELSE NULL END " +
                "FROM `transactions` t WHERE t.`operationId` IS NOT NULL"
        )

        // The synthesized contra leg of every single-leg operation.
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT t.`operationId`, " +
                "CASE t.`type` " +
                "WHEN 'ADJUSTMENT' THEN (SELECT base FROM `_sys`) + 1 " +
                "WHEN 'EXPENSE' THEN COALESCE((SELECT c.`accountId` FROM `categories` c WHERE c.`id` = t.`categoryId`), (SELECT base FROM `_sys`) + 2) " +
                "WHEN 'INCOME' THEN COALESCE((SELECT c.`accountId` FROM `categories` c WHERE c.`id` = t.`categoryId`), (SELECT base FROM `_sys`) + 3) " +
                "END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE -CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL' " +
                "FROM `transactions` t " +
                "WHERE t.`operationId` IN (" +
                "SELECT `operationId` FROM `transactions` WHERE `operationId` IS NOT NULL GROUP BY `operationId` HAVING COUNT(*) = 1" +
                ")"
        )

        // --- 8. Write off the reconstructed closed accounts, dated at their last
        //        movement. Without it the money of a deleted account would sit in net
        //        worth forever; in v7 it simply vanished, unrecorded. ---
        connection.execSQL(
            "CREATE TEMP TABLE `_writeoff` AS " +
                "SELECT e.`accountId` AS accountId, SUM(e.`amount`) AS balance, MAX(o.`date`) AS lastDate, " +
                "(SELECT COALESCE(MAX(`id`), 0) FROM `operations`) + ROW_NUMBER() OVER (ORDER BY e.`accountId`) AS opId " +
                "FROM `entries_build` e JOIN `operations` o ON o.`id` = e.`transactionId` " +
                "WHERE e.`accountId` IN ((SELECT base FROM `_closed`) + 1, (SELECT base FROM `_closed`) + 2) " +
                "GROUP BY e.`accountId` HAVING SUM(e.`amount`) <> 0"
        )
        connection.execSQL(
            "INSERT INTO `operations` (`id`, `title`, `date`) SELECT opId, 'Encerramento', lastDate FROM `_writeoff`"
        )
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT opId, accountId, -balance, 'BRL' FROM `_writeoff`"
        )
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT opId, (SELECT base FROM `_sys`) + 1, balance, 'BRL' FROM `_writeoff`"
        )

        // --- 8b. Nothing may enter the ledger unbalanced. A multi-leg v7 operation was
        //          never checked for `Σ = 0` — the legs are copied verbatim, so a pair
        //          that was not equal and opposite would land as permanent corruption
        //          that no reader could detect and the write boundary never sees.
        //          The residual becomes an explicit equity movement instead. ---
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT `transactionId`, (SELECT base FROM `_sys`) + 1, -SUM(`amount`), `currency` " +
                "FROM `entries_build` GROUP BY `transactionId`, `currency` HAVING SUM(`amount`) <> 0"
        )

        // --- 9. The legacy leg model goes away, and the aggregate takes its name. ---
        connection.execSQL("DROP TABLE `transactions`")
        connection.execSQL("ALTER TABLE `operations` RENAME TO `transactions`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_installmentId` ON `transactions` (`installmentId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurringId` ON `transactions` (`recurringId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurringCycle` ON `transactions` (`recurringCycle`)")

        // --- 10. The entries table, created once with its final name and keys. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL DEFAULT 'BRL', " +
                "`invoiceId` INTEGER, " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`id`, `transactionId`, `accountId`, `amount`, `currency`, `invoiceId`) " +
                "SELECT `id`, `transactionId`, `accountId`, `amount`, `currency`, `invoiceId` FROM `entries_build`"
        )
        connection.execSQL("DROP TABLE `entries_build`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_transactionId` ON `entries` (`transactionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_accountId` ON `entries` (`accountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_invoiceId` ON `entries` (`invoiceId`)")

        // --- 11. `recurring_occurrences` points at the aggregate too. ---
        connection.execSQL(
            "CREATE TABLE `recurring_occurrences_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recurringId` INTEGER NOT NULL, " +
                "`cycleNumber` INTEGER NOT NULL, " +
                "`yearMonth` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`transactionId` INTEGER, " +
                "`effectiveDate` TEXT NOT NULL, " +
                "`handledAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        connection.execSQL(
            "INSERT INTO `recurring_occurrences_new` (`id`, `recurringId`, `cycleNumber`, `yearMonth`, `status`, `transactionId`, `effectiveDate`, `handledAt`) " +
                "SELECT `id`, `recurringId`, `cycleNumber`, `yearMonth`, `status`, `operationId`, `effectiveDate`, `handledAt` FROM `recurring_occurrences`"
        )
        connection.execSQL("DROP TABLE `recurring_occurrences`")
        connection.execSQL("ALTER TABLE `recurring_occurrences_new` RENAME TO `recurring_occurrences`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId` ON `recurring_occurrences` (`recurringId`)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_transactionId` ON `recurring_occurrences` (`transactionId`)")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_yearMonth` " +
                "ON `recurring_occurrences` (`recurringId`, `yearMonth`)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_cycleNumber` " +
                "ON `recurring_occurrences` (`recurringId`, `cycleNumber`)"
        )

        // --- 13. Every facade now has an account, so the column becomes NOT NULL.
        //         Without it a category created later could exist with no account, and
        //         every reader would have to special-case the absence. ---
        connection.execSQL(
            "CREATE TABLE `categories_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        connection.execSQL(
            "INSERT INTO `categories_new` (`id`, `name`, `iconKey`, `type`, `createdAt`, `accountId`) " +
                "SELECT `id`, `name`, `iconKey`, `type`, `createdAt`, `accountId` FROM `categories`"
        )
        connection.execSQL("DROP TABLE `categories`")
        connection.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_accountId` ON `categories` (`accountId`)")

        connection.execSQL(
            "CREATE TABLE `credit_cards_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`limit` REAL NOT NULL, " +
                "`closingDay` INTEGER NOT NULL, " +
                "`dueDay` INTEGER NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        connection.execSQL(
            "INSERT INTO `credit_cards_new` (`id`, `name`, `limit`, `closingDay`, `dueDay`, `iconKey`, `createdAt`, `accountId`) " +
                "SELECT `id`, `name`, `limit`, `closingDay`, `dueDay`, `iconKey`, `createdAt`, `accountId` FROM `credit_cards`"
        )
        connection.execSQL("DROP TABLE `credit_cards`")
        connection.execSQL("ALTER TABLE `credit_cards_new` RENAME TO `credit_cards`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_cards_accountId` ON `credit_cards` (`accountId`)")

        // --- 12. `budgets.categoryId` was a write-only copy of the first category,
        //         and its CASCADE destroyed whole budgets. The M2M table is the truth. ---
        connection.execSQL(
            "CREATE TABLE `budgets_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`iconCategoryId` INTEGER NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`period` TEXT NOT NULL, " +
                "`limitType` TEXT NOT NULL DEFAULT 'FIXED', " +
                "`percentage` REAL, " +
                "`recurringId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "INSERT INTO `budgets_new` (`id`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `limitType`, `percentage`, `recurringId`, `createdAt`) " +
                "SELECT `id`, `iconCategoryId`, `iconKey`, `title`, `amount`, `period`, `limitType`, `percentage`, `recurringId`, `createdAt` FROM `budgets`"
        )
        connection.execSQL("DROP TABLE `budgets`")
        connection.execSQL("ALTER TABLE `budgets_new` RENAME TO `budgets`")

        connection.execSQL("DROP TABLE `_cat_base`")
        connection.execSQL("DROP TABLE `_cc_base`")
        connection.execSQL("DROP TABLE `_sys`")
        connection.execSQL("DROP TABLE `_closed`")
        connection.execSQL("DROP TABLE `_writeoff`")
        connection.execSQL("PRAGMA foreign_keys=ON")
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_9)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
