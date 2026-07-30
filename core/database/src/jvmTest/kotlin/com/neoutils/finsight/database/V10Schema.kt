package com.neoutils.finsight.database

/**
 * The v10 schema, verbatim from `schemas/…/10.json` — the shape a device is on before
 * the upgrade to 11. Frozen history, in the mould of [V7_SCHEMA]: a fixture that is not
 * the real old schema proves nothing about the real migration.
 *
 * Order matters only in that foreign keys are not enforced at CREATE time.
 */
internal val V10_SCHEMA: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `date` TEXT NOT NULL, `recurringId` INTEGER, `recurringCycle` INTEGER, `installmentId` INTEGER, `installmentNumber` INTEGER)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_installmentId` ON `transactions` (`installmentId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_recurringId` ON `transactions` (`recurringId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_recurringCycle` ON `transactions` (`recurringCycle`)",
    "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `dimensionId` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_categories_dimensionId` ON `categories` (`dimensionId`)",
    "CREATE TABLE IF NOT EXISTS `credit_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `limit` REAL NOT NULL, `closingDay` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_credit_cards_accountId` ON `credit_cards` (`accountId`)",
    "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `creditCardId` INTEGER NOT NULL, `dimensionId` INTEGER, `openingMonth` TEXT NOT NULL, `closingMonth` TEXT NOT NULL, `dueMonth` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `openedAt` TEXT, `closedAt` TEXT, `paidAt` TEXT, FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_invoices_creditCardId` ON `invoices` (`creditCardId`)",
    "CREATE INDEX IF NOT EXISTS `index_invoices_dimensionId` ON `invoices` (`dimensionId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_openingMonth` ON `invoices` (`creditCardId`, `openingMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_closingMonth` ON `invoices` (`creditCardId`, `closingMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_dueMonth` ON `invoices` (`creditCardId`, `dueMonth`)",
    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `currency` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `installments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `count` INTEGER NOT NULL, `totalAmount` REAL NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `iconCategoryId` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `title` TEXT NOT NULL, `amount` REAL NOT NULL, `period` TEXT NOT NULL, `limitType` TEXT NOT NULL, `percentage` REAL, `recurringId` INTEGER, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `budget_categories` (`budgetId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`budgetId`, `categoryId`), FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_budget_categories_budgetId` ON `budget_categories` (`budgetId`)",
    "CREATE INDEX IF NOT EXISTS `index_budget_categories_categoryId` ON `budget_categories` (`categoryId`)",
    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `title` TEXT, `dayOfMonth` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `creditCardId` INTEGER, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)",
    "CREATE INDEX IF NOT EXISTS `index_recurring_creditCardId` ON `recurring` (`creditCardId`)",
    "CREATE TABLE IF NOT EXISTS `recurring_occurrences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recurringId` INTEGER NOT NULL, `cycleNumber` INTEGER NOT NULL, `yearMonth` TEXT NOT NULL, `status` TEXT NOT NULL, `transactionId` INTEGER, `effectiveDate` TEXT NOT NULL, `handledAt` INTEGER NOT NULL, FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId` ON `recurring_occurrences` (`recurringId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_transactionId` ON `recurring_occurrences` (`transactionId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_yearMonth` ON `recurring_occurrences` (`recurringId`, `yearMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_cycleNumber` ON `recurring_occurrences` (`recurringId`, `cycleNumber`)",
    "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `dimensionId` INTEGER, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_entries_transactionId` ON `entries` (`transactionId`)",
    "CREATE INDEX IF NOT EXISTS `index_entries_accountId` ON `entries` (`accountId`)",
    "CREATE INDEX IF NOT EXISTS `index_entries_dimensionId` ON `entries` (`dimensionId`)",
    "CREATE TABLE IF NOT EXISTS `dimensions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL)",
)
