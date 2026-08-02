package com.neoutils.finsight.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
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

// 1.9.0-rc01
val MIGRATION_7_10 = object : Migration(7, 10) {
    override fun migrate(connection: SQLiteConnection) {
        val now = "CAST(strftime('%s','now') AS INTEGER) * 1000"

        connection.execSQL("PRAGMA foreign_keys=OFF")

        // --- 1. Extend the chart of accounts. `isArchived` is the single closure flag
        //        (design D21); a card reads it through its accountId instead of keeping
        //        a copy. A category is not in the chart at all, so it owns its own. ---
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'ASSET'")
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'BRL'")
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")

        // --- 2. The dimension table: the analytic axis a leg is classified by. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `dimensions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL)"
        )

        // --- 3. One INVOICE dimension per invoice. With `dimensions` empty,
        //        `id = invoices.id` is free of collision. ---
        connection.execSQL("INSERT INTO `dimensions` (`id`, `kind`) SELECT `id`, 'INVOICE' FROM `invoices`")
        connection.execSQL(
            "ALTER TABLE `invoices` ADD COLUMN `dimensionId` INTEGER " +
                "REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL("UPDATE `invoices` SET `dimensionId` = `id`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_dimensionId` ON `invoices` (`dimensionId`)")

        // --- 4. One CATEGORY dimension per category, offset past the invoice ones so
        //        the two ranges cannot collide. A category never becomes an account:
        //        it is a dimension from the first statement that mentions it. ---
        connection.execSQL("CREATE TEMP TABLE `_dim_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `dimensions`")
        connection.execSQL(
            "INSERT INTO `dimensions` (`id`, `kind`) " +
                "SELECT (SELECT base FROM `_dim_base`) + `id`, 'CATEGORY' FROM `categories`"
        )

        // --- 5. Promote credit cards to LIABILITY accounts (ids disjoint via the
        //        captured offset). A card *is* a facade over a chart row. ---
        connection.execSQL(
            "ALTER TABLE `credit_cards` ADD COLUMN `accountId` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL("CREATE TEMP TABLE `_cc_base` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "SELECT (SELECT base FROM `_cc_base`) + cc.`id`, cc.`name`, 'LIABILITY', 'BRL', cc.`iconKey`, 0, cc.`createdAt`, 0 " +
                "FROM `credit_cards` cc"
        )
        connection.execSQL("UPDATE `credit_cards` SET `accountId` = (SELECT base FROM `_cc_base`) + `id`")

        // --- 6. The three accounts the app keeps for itself, mirroring `SystemAccount`:
        //        reconciliation (the counter-leg of every adjustment and write-off) and
        //        the two nominals every expense and income lands on. Created outright
        //        rather than looked up by name — a user account may well be called
        //        "Despesas" — and always, so the write boundary finds them instead of
        //        creating a second one. No 'Saldo Inicial': the app has no such concept. ---
        connection.execSQL("CREATE TEMP TABLE `_sys` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) VALUES " +
                "((SELECT base FROM `_sys`) + 1, 'Reconciliação', 'EQUITY', 'BRL', 'wallet', 0, $now, 0), " +
                "((SELECT base FROM `_sys`) + 2, 'Despesas', 'EXPENSE', 'BRL', 'default', 0, $now, 0), " +
                "((SELECT base FROM `_sys`) + 3, 'Receitas', 'INCOME', 'BRL', 'default', 0, $now, 0)"
        )

        // --- 7. Reconstruct the accounts deleted in v7. Their legs survived with a
        //        NULL pointer (FK SET NULL), which would abort the upgrade on
        //        `entries.accountId NOT NULL`. The real type is recoverable from
        //        `transactions.target`; the name and the multiplicity are not, so all
        //        the orphans of a type collapse into one closed account.
        //
        //        The EXISTS must match every leg that step 9 will route to the bucket,
        //        which is any leg with a NULL account/card — regardless of `operationId`.
        //        Step 8 backfills the NULL `operationId` of a leg that never had an
        //        aggregate, and step 9 then routes it here too; guarding this EXISTS on
        //        `operationId IS NOT NULL` would skip creating the bucket for a leg that
        //        is *only* such an orphan, leaving its entry pointing at an account that
        //        does not exist (a dangling FK the write-off cannot repair). ---
        connection.execSQL("CREATE TEMP TABLE `_closed` AS SELECT COALESCE(MAX(`id`), 0) AS base FROM `accounts`")
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "SELECT (SELECT base FROM `_closed`) + 1, 'Conta encerrada', 'ASSET', 'BRL', 'wallet', 0, $now, 1 " +
                "WHERE EXISTS (SELECT 1 FROM `transactions` WHERE `target` = 'ACCOUNT' AND `accountId` IS NULL)"
        )
        connection.execSQL(
            "INSERT INTO `accounts` (`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "SELECT (SELECT base FROM `_closed`) + 2, 'Cartão encerrado', 'LIABILITY', 'BRL', 'credit_card', 0, $now, 1 " +
                "WHERE EXISTS (" +
                "SELECT 1 FROM `transactions` t WHERE t.`target` = 'CREDIT_CARD' " +
                "AND (SELECT cc.`accountId` FROM `credit_cards` cc WHERE cc.`id` = t.`creditCardId`) IS NULL)"
        )

        // --- 8. A leg with no aggregate. `transactions.operationId` has been nullable
        //        since v1 and no migration ever backfilled it, so such a row can exist.
        //        Filtering it out would make its money disappear from the balance with
        //        no error and no trace; instead it gets the aggregate it never had,
        //        built from what the leg itself carries. ---
        connection.execSQL(
            "CREATE TEMP TABLE `_orphan_legs` AS " +
                "SELECT `id` AS legId, `title` AS legTitle, `date` AS legDate, `categoryId` AS legCategoryId, " +
                "(SELECT COALESCE(MAX(`id`), 0) FROM `operations`) + ROW_NUMBER() OVER (ORDER BY `id`) AS opId " +
                "FROM `transactions` WHERE `operationId` IS NULL"
        )
        connection.execSQL(
            "INSERT INTO `operations` (`id`, `kind`, `title`, `date`, `categoryId`) " +
                "SELECT opId, 'TRANSACTION', legTitle, legDate, legCategoryId FROM `_orphan_legs`"
        )
        connection.execSQL(
            "UPDATE `transactions` SET `operationId` = " +
                "(SELECT opId FROM `_orphan_legs` WHERE legId = `transactions`.`id`) " +
                "WHERE `operationId` IS NULL"
        )
        connection.execSQL("DROP TABLE `_orphan_legs`")

        // --- 9. Build the ledger. `entries_build` carries no foreign keys because the
        //        aggregate is still called `operations`; the final table is created
        //        against the final names in step 14. ---
        connection.execSQL(
            "CREATE TABLE `entries_build` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`dimensionId` INTEGER)"
        )

        // The real leg of every legacy transaction (debit-positive cents). An invoice's
        // dimension lands on the LIABILITY leg and nowhere else: a payment's account leg
        // also carries `invoiceId` in v7, and tagging it too would cancel the owed sum.
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "SELECT t.`operationId`, " +
                "CASE t.`target` " +
                "WHEN 'ACCOUNT' THEN COALESCE(t.`accountId`, (SELECT base FROM `_closed`) + 1) " +
                "ELSE COALESCE((SELECT cc.`accountId` FROM `credit_cards` cc WHERE cc.`id` = t.`creditCardId`), (SELECT base FROM `_closed`) + 2) END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN -CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL', " +
                "CASE WHEN t.`target` = 'CREDIT_CARD' " +
                "THEN (SELECT i.`dimensionId` FROM `invoices` i WHERE i.`id` = t.`invoiceId`) ELSE NULL END " +
                "FROM `transactions` t WHERE t.`operationId` IS NOT NULL"
        )

        // The synthesized contra leg of every single-leg operation. It lands on a
        // nominal — never on a per-category account, which v10 does not have — and
        // carries the category as its dimension. Which nominal is the *category's*
        // nature when there is one (a v7 expense filed under an income category kept
        // landing on the income side, and still does); with no category it is the
        // leg's own type, and the dimension is absent: "uncategorized" is the absence
        // of a dimension, never a bucket account.
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "SELECT t.`operationId`, " +
                "CASE " +
                "WHEN t.`type` = 'ADJUSTMENT' THEN (SELECT base FROM `_sys`) + 1 " +
                "WHEN COALESCE((SELECT c.`type` FROM `categories` c WHERE c.`id` = t.`categoryId`), t.`type`) = 'INCOME' " +
                "THEN (SELECT base FROM `_sys`) + 3 " +
                "ELSE (SELECT base FROM `_sys`) + 2 END, " +
                "CASE t.`type` WHEN 'EXPENSE' THEN CAST(ROUND(t.`amount` * 100) AS INTEGER) " +
                "ELSE -CAST(ROUND(t.`amount` * 100) AS INTEGER) END, " +
                "'BRL', " +
                "CASE WHEN t.`type` = 'ADJUSTMENT' THEN NULL " +
                "ELSE (SELECT (SELECT base FROM `_dim_base`) + c.`id` FROM `categories` c WHERE c.`id` = t.`categoryId`) END " +
                "FROM `transactions` t " +
                "WHERE t.`operationId` IN (" +
                "SELECT `operationId` FROM `transactions` WHERE `operationId` IS NOT NULL GROUP BY `operationId` HAVING COUNT(*) = 1" +
                ")"
        )

        // --- 10. Write off the reconstructed closed accounts, dated at their last
        //         movement. Without it the money of a deleted account would sit in net
        //         worth forever; in v7 it simply vanished, unrecorded. ---
        connection.execSQL(
            "CREATE TEMP TABLE `_writeoff` AS " +
                "SELECT e.`accountId` AS accountId, SUM(e.`amount`) AS balance, MAX(o.`date`) AS lastDate, " +
                "(SELECT COALESCE(MAX(`id`), 0) FROM `operations`) + ROW_NUMBER() OVER (ORDER BY e.`accountId`) AS opId " +
                "FROM `entries_build` e JOIN `operations` o ON o.`id` = e.`transactionId` " +
                "WHERE e.`accountId` IN ((SELECT base FROM `_closed`) + 1, (SELECT base FROM `_closed`) + 2) " +
                "GROUP BY e.`accountId` HAVING SUM(e.`amount`) <> 0"
        )
        connection.execSQL(
            "INSERT INTO `operations` (`id`, `kind`, `title`, `date`) " +
                "SELECT opId, 'TRANSACTION', 'Encerramento', lastDate FROM `_writeoff`"
        )
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT opId, accountId, -balance, 'BRL' FROM `_writeoff`"
        )
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT opId, (SELECT base FROM `_sys`) + 1, balance, 'BRL' FROM `_writeoff`"
        )

        // --- 11. Nothing may enter the ledger unbalanced. A multi-leg v7 operation was
        //         never checked for `Σ = 0` — the legs are copied verbatim, so a pair
        //         that was not equal and opposite would land as permanent corruption
        //         that no reader could detect and the write boundary never sees.
        //         The residual becomes an explicit equity movement instead. ---
        connection.execSQL(
            "INSERT INTO `entries_build` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "SELECT `transactionId`, (SELECT base FROM `_sys`) + 1, -SUM(`amount`), `currency` " +
                "FROM `entries_build` GROUP BY `transactionId`, `currency` HAVING SUM(`amount`) <> 0"
        )

        // --- 12. The legacy leg model goes away and the aggregate takes its name and
        //         its final shape. `categoryId` goes with it — what a transaction is
        //         spent on is the dimension of its nominal leg — and so do the
        //         installment and recurring foreign keys: the columns stay, the keys
        //         cannot, because their parent tables are not the ledger's (D12). The
        //         nullification they granted for free has an explicit owner in each
        //         facade's removal path.
        //
        //         `operations` is dropped only in step 13, after `recurring_occurrences`
        //         has stopped pointing at it: no statement here may leave a foreign key
        //         aimed at a table that does not exist. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT, " +
                "`date` TEXT NOT NULL, " +
                "`recurringId` INTEGER, " +
                "`recurringCycle` INTEGER, " +
                "`installmentId` INTEGER, " +
                "`installmentNumber` INTEGER)"
        )
        connection.execSQL(
            "INSERT INTO `transactions_new` (`id`, `title`, `date`, `recurringId`, `recurringCycle`, `installmentId`, `installmentNumber`) " +
                "SELECT `id`, `title`, `date`, `recurringId`, `recurringCycle`, `installmentId`, `installmentNumber` FROM `operations`"
        )
        connection.execSQL("DROP TABLE `transactions`")
        connection.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_installmentId` ON `transactions` (`installmentId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurringId` ON `transactions` (`recurringId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_recurringCycle` ON `transactions` (`recurringCycle`)")

        // --- 13. `recurring_occurrences` points at the aggregate under its new name. ---
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
        connection.execSQL("DROP TABLE `operations`")
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

        // --- 14. The entries table, created once with its final name and keys. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transactionId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`dimensionId` INTEGER, " +
                "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`id`, `transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "SELECT `id`, `transactionId`, `accountId`, `amount`, `currency`, `dimensionId` FROM `entries_build`"
        )
        connection.execSQL("DROP TABLE `entries_build`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_transactionId` ON `entries` (`transactionId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_accountId` ON `entries` (`accountId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_dimensionId` ON `entries` (`dimensionId`)")

        // --- 15. Rebuild `categories` around its dimension. `isArchived` is its own —
        //         a category is not in the chart, so it has no account to read closure
        //         from — and starts closed for nobody: v7 had no such concept. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`iconKey` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`dimensionId` INTEGER NOT NULL, " +
                "`isArchived` INTEGER NOT NULL, " +
                "FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION" +
                ")"
        )
        connection.execSQL(
            "INSERT INTO `categories_new` (`id`, `name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`) " +
                "SELECT `id`, `name`, `iconKey`, `type`, `createdAt`, (SELECT base FROM `_dim_base`) + `id`, 0 FROM `categories`"
        )
        connection.execSQL("DROP TABLE `categories`")
        connection.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_dimensionId` ON `categories` (`dimensionId`)")

        // --- 16. Every card now has an account, so the column becomes NOT NULL.
        //         Without it a card created later could exist with no account, and
        //         every reader would have to special-case the absence. ---
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

        // --- 17. `budgets.categoryId` was a write-only copy of the first category,
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

        connection.execSQL("DROP TABLE `_dim_base`")
        connection.execSQL("DROP TABLE `_cc_base`")
        connection.execSQL("DROP TABLE `_sys`")
        connection.execSQL("DROP TABLE `_closed`")
        connection.execSQL("DROP TABLE `_writeoff`")

        // --- 18. Verification. The ledger balances, no entry points at a dimension
        //         that does not exist, and no reference dangles anywhere. Enforcement
        //         is off for the whole rewrite, so this is the only moment the keys
        //         are actually checked. ---
        connection.verifyLedgerBalanced(stage = "v7 → v10")
        connection.verifyNoOrphanDimensions(stage = "v7 → v10")
        connection.verifyForeignKeys(stage = "v7 → v10")
        connection.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Schema 11: the rate archive, and a budget limit that says what it is denominated in.
 *
 * **No stored value changes.** Every existing database is entirely in `'BRL'` — not
 * because anybody chose it, but because it was the model's default — so the currency
 * the new column receives is exactly the one that already denominated each stored
 * limit. The fill is *exact*, not approximate.
 *
 * **And it relabels the legacy chart of accounts**, when asked to. That is
 * re-denomination and not conversion: no value and no balance moves, and `Σ = 0` per
 * currency goes on holding because the currency of every row changes together.
 *
 * **Why a migration and not a startup step.** There is no app initialisation step in
 * this project — `App.kt` only sets the user id, and `EnsureDefaultAccountUseCase` runs
 * fire-and-forget from `DashboardViewModel.init`, concurrent with the dashboard's own
 * flows. A migration has the three properties for free: it runs once, it **records
 * that it ran** through `user_version` with no flag to invent or keep correct, and it
 * precedes every read by construction. The objection that a migration reading the
 * environment stops being deterministic is already settled in this file by a stronger
 * precedent — `MIGRATION_3_4` reads the device's clock and time zone. With the
 * parameter, this one is *more* deterministic: a test fixes the argument.
 *
 * **The false positive, and it is accepted.** Someone whose real currency is the legacy
 * one but whose device is set to a foreign region is relabelled **without being asked**,
 * and design D12 means the app offers no way back. Two things narrow it: the **region**
 * decides rather than the language, so an English interface on a Brazilian device fires
 * nothing; and a currency of other than two decimal places is barred, which falls into
 * the silent case of leaving the denomination alone. The alternative considered — asking
 * once, on the first run after updating, which would get both cases right — was
 * rejected in favour of zero friction: it would put a migration screen in front of
 * every user to serve the rare one, and the common case is a user who has been reading
 * their own currency on screen all along and simply keeps reading it.
 *
 * @param relabelCurrency the currency the legacy chart of accounts should be
 * re-denominated to, already resolved and validated outside this module — `core/database`
 * receives a currency code and knows nothing of locales or catalogues. `null` means "do
 * not relabel", which is the common case.
 */
fun migration1011(
    relabelCurrency: String? = null,
) = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        // --- 1. The rate archive. A surrogate key with a unique triple, so that a
        //        user's correction and the rate an operation observed can coexist on
        //        the same (currency, date) — which is what makes precedence mean
        //        something instead of destroying the other row. ---
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`currency` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`rate` REAL NOT NULL, " +
                "`source` TEXT NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_currency_date_source` " +
                "ON `exchange_rates` (`currency`, `date`, `source`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_currency_date` " +
                "ON `exchange_rates` (`currency`, `date`)"
        )

        // --- 2. A budget limit becomes denominated. `'BRL'` is not a guess: it is what
        //        every existing limit was already denominated in. The SQL default is
        //        only how SQLite accepts a NOT NULL column on an existing table — the
        //        entity declares none, exactly as `budgets.limitType` already does. ---
        connection.execSQL(
            "ALTER TABLE `budgets` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'BRL'"
        )

        // --- 3. Re-denominate the legacy chart of accounts, when asked to.
        //
        //        `accounts` **and** `entries`, in this one transaction. An earlier
        //        reading of the design said "no entry is touched", and that was
        //        incompatible with the change itself: if the accounts became USD while
        //        the history went on saying BRL, the per-currency aggregations would
        //        split each account's story in two, and `LedgerBalanceCheck` — which
        //        groups by `(transactionId, currency)` without consulting `accounts` —
        //        would stop being readable as the truth about that account.
        //
        //        Unconditional, with no `WHERE currency = ...`. Every row of both
        //        tables is the legacy denomination today, so the `UPDATE` is exact
        //        either way; unconditional is the form that *cannot* leave two
        //        currencies behind, which is the property that matters.
        //
        //        The system rows go with them. `CLOSED_ACCOUNT`/`CLOSED_CARD` and the
        //        two nominals are rows of the chart like any other, and design D4
        //        wants `Account.currency` to mean the same thing on every line of it.
        if (relabelCurrency != null) {
            // `execSQL` binds nothing, so the code is interpolated — and a code that
            // is not a code stops here rather than reaching the statement. The caller
            // already validated it; this is the module refusing to
            // depend on that being true.
            require(relabelCurrency.matches(Regex("[A-Z]{3}"))) {
                "relabelCurrency must be an ISO 4217 code, was '$relabelCurrency'"
            }
            connection.execSQL("UPDATE `accounts` SET `currency` = '$relabelCurrency'")
            connection.execSQL("UPDATE `entries` SET `currency` = '$relabelCurrency'")
            // A budget limit goes with them, for the same reason and by the same
            // argument. Its denomination was never a choice either — step 2 above filled
            // it with the legacy code because that is what denominated it — so leaving
            // it behind would hand the relabelled user a limit in a currency he holds
            // nothing in, and a progress bar consolidating and marked `≈` forever. That
            // is precisely the cost design D13 exists to keep off the single-currency
            // user, arriving through the migration instead of through the form.
            connection.execSQL("UPDATE `budgets` SET `currency` = '$relabelCurrency'")
        }

        // --- 4. Verification, the same three guards `v7 → v10` closes with. ---
        connection.verifyLedgerBalanced(stage = "v10 → v11")
        connection.verifyNoOrphanDimensions(stage = "v10 → v11")
        connection.verifyForeignKeys(stage = "v10 → v11")
    }
}

/**
 * Schema 12: a rate stops being *"the currency, against whatever base is in force"* and
 * becomes an observation about a **pair**.
 *
 * **No stored value changes.** `rate`, `date`, `currency` and `source` are read by
 * nothing here. The new column is filled with the base currency in force, and the fill
 * is *exact* rather than approximate: every existing row was measured against that base,
 * which until this schema had no way to change. It is the same quality the budget
 * limit's currency had in [migration1011].
 *
 * **The unique index widens rather than moves.** `(currency, date, source)` becomes
 * `(currency, counterCurrency, date, source)`, which is what lets the dollar be observed
 * against the real and against the euro on the same day — two observations, two rows.
 * The names are the canonical ones Room generates, because it is against those that the
 * identity hash check compares.
 *
 * @param baseCurrency the base currency in force, already resolved outside this module —
 * `core/database` cannot reach `Settings` and receives a plain code.
 */
fun migration1112(
    baseCurrency: String,
) = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        // `execSQL` binds nothing, so the code is interpolated — and a code that is not
        // a code stops here rather than reaching the statement. The caller resolved it
        // above this module; this is it refusing to depend on that being true.
        require(baseCurrency.matches(Regex("[A-Z]{3}"))) {
            "baseCurrency must be an ISO 4217 code, was '$baseCurrency'"
        }

        // --- 1. The counterpart becomes explicit. The SQL default is only how SQLite
        //        accepts a NOT NULL column on an existing table — the entity declares
        //        none, deliberately: a row that does not say its pair is the defect. ---
        connection.execSQL(
            "ALTER TABLE `exchange_rates` ADD COLUMN `counterCurrency` TEXT NOT NULL DEFAULT ''"
        )
        connection.execSQL("UPDATE `exchange_rates` SET `counterCurrency` = '$baseCurrency'")

        // --- 2. The indices follow the pair. ---
        connection.execSQL("DROP INDEX IF EXISTS `index_exchange_rates_currency_date_source`")
        connection.execSQL("DROP INDEX IF EXISTS `index_exchange_rates_currency_date`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_exchange_rates_currency_counterCurrency_date_source` " +
                "ON `exchange_rates` (`currency`, `counterCurrency`, `date`, `source`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exchange_rates_currency_counterCurrency_date` " +
                "ON `exchange_rates` (`currency`, `counterCurrency`, `date`)"
        )

        // --- 3. Verification, the same three guards every migration closes with. ---
        connection.verifyLedgerBalanced(stage = "v11 → v12")
        connection.verifyNoOrphanDimensions(stage = "v11 → v12")
        connection.verifyForeignKeys(stage = "v11 → v12")
    }
}

/**
 * Schema 13: the set of offered currencies stops being an opinion embedded in the code
 * and becomes a **table**.
 *
 * **One write, not two.** The seed, every currency an existing account is already
 * denominated in, and the currency the device's locale names all land in the same
 * `INSERT`. Under an overlay design these would be two migrations with distinct purposes
 * — seed, and materialise what is in use — but with a single table the destination is the
 * same, so they are the same operation.
 *
 * The consequence that matters most: **the locale's auto-registration stops being a
 * mechanism.** There is no "automatic registration" to design, test or explain — there is
 * a seeding, and the device's currency is among what it seeds. A second path to that same
 * write would be a second place the user's currency could fail to exist.
 *
 * **Nobody loses the currency they already use.** `SELECT DISTINCT currency FROM accounts`
 * is what shrinks the shipped set from twenty-two to six without taking ARS from the
 * Argentinian who already has an account in it. It is also what makes this migration and
 * the legacy relabel of [migration1011] fit together **without either knowing the other**:
 * the relabel writes `accounts.currency`, and this reads it. No ordering is required, and
 * none could be arranged — the relabel is `10 → 11` and this can only be `12 → 13`.
 *
 * @param seeding resolved outside this module: `core/database` may name neither a locale
 * nor the platform, and receives rows and a glyph rather than the means to derive them.
 */
fun migration1213(
    seeding: CurrencySeeding,
) = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `currencies` (" +
                "`code` TEXT NOT NULL, " +
                "`symbol` TEXT NOT NULL, " +
                "`name` TEXT, " +
                "`isArchived` INTEGER NOT NULL, " +
                "PRIMARY KEY(`code`))"
        )

        connection.seedCurrencies(seeding)

        // --- Verification, the same three guards every migration of this file closes
        //     with. Nothing here touches the ledger, and that is exactly what they
        //     assert. ---
        connection.verifyLedgerBalanced(stage = "v12 → v13")
        connection.verifyNoOrphanDimensions(stage = "v12 → v13")
        connection.verifyForeignKeys(stage = "v12 → v13")
    }
}

/**
 * The seeding itself, as one statement.
 *
 * It is shared by the migration and by the fresh-install callback because a new database
 * never runs a migration: Room creates the schema from the entities, so without this the
 * only user with no currencies at all would be the one who just installed the app.
 *
 * `INSERT OR IGNORE` makes it idempotent and makes the precedence trivial: the seed's own
 * glyph wins over the platform's suggestion for the same code, and running it twice
 * writes nothing the second time.
 *
 * **`name` is left null on purpose.** Storing a name here would freeze it in the language
 * of the first run — switching the app's language would silently stop translating it. A
 * row keeps a name only when the user writes one; otherwise the platform names it at
 * every read.
 */
private fun SQLiteConnection.seedCurrencies(seeding: CurrencySeeding) {
    val inUse = mutableListOf<String>()
    val statement = prepare("SELECT DISTINCT `currency` FROM `accounts`")
    try {
        while (statement.step()) {
            inUse += statement.getText(0)
        }
    } finally {
        statement.close()
    }

    val rows = (seeding.rows() + inUse.map { SeedCurrency(it, seeding.symbolOf(it)) })
        .filter { it.code.isNotBlank() }
        .distinctBy { it.code }

    if (rows.isEmpty()) return

    // `execSQL` binds nothing, so the values are interpolated — and a code or a glyph
    // that could break out of the statement stops here rather than reaching it.
    val values = rows.joinToString(", ") { row ->
        require(!row.code.contains('\'') && !row.symbol.contains('\'')) {
            "a currency code and its symbol may not contain a quote, was '${row.code}'"
        }
        "('${row.code}', '${row.symbol}', NULL, 0)"
    }
    execSQL("INSERT OR IGNORE INTO `currencies` (`code`, `symbol`, `name`, `isArchived`) VALUES $values")
}

/**
 * The fresh install's half of the seeding.
 *
 * A database created from the entities skips every migration, so the seeding needs this
 * second entry point — and it is the *same* write, called from here, rather than a second
 * one that could drift from it.
 */
private fun seedingCallback(seeding: CurrencySeeding) = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        connection.seedCurrencies(seeding)
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    relabelCurrency: String? = null,
    baseCurrency: String,
    currencySeeding: CurrencySeeding,
): AppDatabase {
    return builder
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_10,
            migration1011(relabelCurrency),
            migration1112(baseCurrency),
            migration1213(currencySeeding),
        )
        .addCallback(seedingCallback(currencySeeding))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
