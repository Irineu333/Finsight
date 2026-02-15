@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            ALTER TABLE invoices ADD COLUMN openedAt INTEGER
            """.trimIndent()
        )
        
        connection.execSQL(
            """
            UPDATE invoices 
            SET openedAt = createdAt / 86400000
            WHERE status = 'OPEN'
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS operations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                kind TEXT NOT NULL,
                title TEXT,
                date TEXT NOT NULL,
                categoryId INTEGER,
                sourceAccountId INTEGER,
                targetCreditCardId INTEGER,
                targetInvoiceId INTEGER,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL,
                FOREIGN KEY(sourceAccountId) REFERENCES accounts(id) ON DELETE SET NULL,
                FOREIGN KEY(targetCreditCardId) REFERENCES credit_cards(id) ON DELETE SET NULL,
                FOREIGN KEY(targetInvoiceId) REFERENCES invoices(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )

        connection.execSQL("CREATE INDEX IF NOT EXISTS index_operations_categoryId ON operations(categoryId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_operations_sourceAccountId ON operations(sourceAccountId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_operations_targetCreditCardId ON operations(targetCreditCardId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_operations_targetInvoiceId ON operations(targetInvoiceId)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transactions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                operationId INTEGER,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                title TEXT,
                date TEXT NOT NULL,
                categoryId INTEGER,
                target TEXT NOT NULL,
                creditCardId INTEGER,
                invoiceId INTEGER,
                accountId INTEGER,
                installmentNumber INTEGER,
                totalInstallments INTEGER,
                installmentGroupId TEXT,
                installmentTotalAmount REAL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE SET NULL,
                FOREIGN KEY(creditCardId) REFERENCES credit_cards(id) ON DELETE SET NULL,
                FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON DELETE SET NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE SET NULL,
                FOREIGN KEY(operationId) REFERENCES operations(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        connection.execSQL(
            """
            INSERT INTO transactions_new (
                id, type, amount, title, date, categoryId, target, creditCardId, invoiceId, accountId,
                installmentNumber, totalInstallments, installmentGroupId, installmentTotalAmount
            )
            SELECT
                id,
                CASE type
                    WHEN 'INVOICE_PAYMENT' THEN 'EXPENSE'
                    WHEN 'ADVANCE_PAYMENT' THEN 'EXPENSE'
                    ELSE type
                END,
                amount,
                title,
                date,
                categoryId,
                CASE target
                    WHEN 'INVOICE_PAYMENT' THEN 'ACCOUNT'
                    ELSE target
                END,
                creditCardId,
                invoiceId,
                accountId,
                installmentNumber,
                totalInstallments,
                installmentGroupId,
                installmentTotalAmount
            FROM transactions
            """.trimIndent()
        )

        connection.execSQL("DROP TABLE transactions")
        connection.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions(categoryId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_creditCardId ON transactions(creditCardId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_invoiceId ON transactions(invoiceId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_operationId ON transactions(operationId)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        val currentTime = Clock.System.now().toEpochMilliseconds()

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        connection.execSQL(
            """
            INSERT INTO accounts (name, isDefault, createdAt)
            VALUES ('Principal', 1, $currentTime)
            """.trimIndent()
        )

        connection.execSQL(
            """
            ALTER TABLE transactions ADD COLUMN accountId INTEGER
            REFERENCES accounts(id) ON DELETE SET NULL
            """.trimIndent()
        )

        connection.execSQL(
            """
            UPDATE transactions SET accountId = 1
            WHERE target IN ('ACCOUNT', 'INVOICE_PAYMENT')
            """.trimIndent()
        )

        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_transactions_accountId
            ON transactions (accountId)
            """.trimIndent()
        )
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
        .build()
}