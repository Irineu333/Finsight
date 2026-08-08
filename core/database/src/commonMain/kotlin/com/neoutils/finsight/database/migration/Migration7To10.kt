package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.extension.verifyForeignKeys
import com.neoutils.finsight.database.extension.verifyLedgerBalanced
import com.neoutils.finsight.database.extension.verifyNoOrphanDimensions

/** Schema 7 → 10: the double-entry ledger — shipped in 1.9.0-rc01. */
object Migration7To10 : Migration(7, 10) {
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
