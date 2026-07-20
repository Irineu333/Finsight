package com.neoutils.finsight.database

/**
 * The v7 schema, verbatim from `schemas/…/7.json` — the shape a real device is on
 * before the upgrade. v7 is frozen history, so this list never changes; writing it
 * by hand is what makes a migration test lie, because a fixture that is not the
 * real old schema proves nothing about the real migration.
 *
 * Order matters only in that foreign keys are not enforced at CREATE time.
 */
internal val V7_SCHEMA: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operationId` INTEGER, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `title` TEXT, `date` TEXT NOT NULL, `categoryId` INTEGER, `target` TEXT NOT NULL, `creditCardId` INTEGER, `invoiceId` INTEGER, `accountId` INTEGER, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`operationId`) REFERENCES `operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_creditCardId` ON `transactions` (`creditCardId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_invoiceId` ON `transactions` (`invoiceId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_operationId` ON `transactions` (`operationId`)",
    "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `credit_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `limit` REAL NOT NULL, `closingDay` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `creditCardId` INTEGER NOT NULL, `openingMonth` TEXT NOT NULL, `closingMonth` TEXT NOT NULL, `dueMonth` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `openedAt` TEXT, `closedAt` TEXT, `paidAt` TEXT, FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_invoices_creditCardId` ON `invoices` (`creditCardId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_openingMonth` ON `invoices` (`creditCardId`, `openingMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_closingMonth` ON `invoices` (`creditCardId`, `closingMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_creditCardId_dueMonth` ON `invoices` (`creditCardId`, `dueMonth`)",
    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `installments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `count` INTEGER NOT NULL, `totalAmount` REAL NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `operations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT, `date` TEXT NOT NULL, `categoryId` INTEGER, `sourceAccountId` INTEGER, `targetCreditCardId` INTEGER, `targetInvoiceId` INTEGER, `recurringId` INTEGER, `recurringCycle` INTEGER, `installmentId` INTEGER, `installmentNumber` INTEGER, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`sourceAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`targetCreditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`targetInvoiceId`) REFERENCES `invoices`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`installmentId`) REFERENCES `installments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_operations_categoryId` ON `operations` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_sourceAccountId` ON `operations` (`sourceAccountId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_targetCreditCardId` ON `operations` (`targetCreditCardId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_targetInvoiceId` ON `operations` (`targetInvoiceId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_installmentId` ON `operations` (`installmentId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_recurringId` ON `operations` (`recurringId`)",
    "CREATE INDEX IF NOT EXISTS `index_operations_recurringCycle` ON `operations` (`recurringCycle`)",
    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `iconCategoryId` INTEGER NOT NULL, `iconKey` TEXT NOT NULL, `title` TEXT NOT NULL, `amount` REAL NOT NULL, `period` TEXT NOT NULL, `limitType` TEXT NOT NULL, `percentage` REAL, `recurringId` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)",
    "CREATE TABLE IF NOT EXISTS `budget_categories` (`budgetId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`budgetId`, `categoryId`), FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_budget_categories_budgetId` ON `budget_categories` (`budgetId`)",
    "CREATE INDEX IF NOT EXISTS `index_budget_categories_categoryId` ON `budget_categories` (`categoryId`)",
    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `title` TEXT, `dayOfMonth` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `creditCardId` INTEGER, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`creditCardId`) REFERENCES `credit_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_recurring_categoryId` ON `recurring` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_recurring_accountId` ON `recurring` (`accountId`)",
    "CREATE INDEX IF NOT EXISTS `index_recurring_creditCardId` ON `recurring` (`creditCardId`)",
    "CREATE TABLE IF NOT EXISTS `recurring_occurrences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recurringId` INTEGER NOT NULL, `cycleNumber` INTEGER NOT NULL, `yearMonth` TEXT NOT NULL, `status` TEXT NOT NULL, `operationId` INTEGER, `effectiveDate` TEXT NOT NULL, `handledAt` INTEGER NOT NULL, FOREIGN KEY(`recurringId`) REFERENCES `recurring`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`operationId`) REFERENCES `operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId` ON `recurring_occurrences` (`recurringId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_operationId` ON `recurring_occurrences` (`operationId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_yearMonth` ON `recurring_occurrences` (`recurringId`, `yearMonth`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_occurrences_recurringId_cycleNumber` ON `recurring_occurrences` (`recurringId`, `cycleNumber`)",
)
