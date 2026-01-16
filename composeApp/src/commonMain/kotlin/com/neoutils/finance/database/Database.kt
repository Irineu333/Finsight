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
        .addMigrations(MIGRATION_7_8)
        .build()
}