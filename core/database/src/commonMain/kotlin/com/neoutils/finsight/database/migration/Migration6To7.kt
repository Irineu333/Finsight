package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema 6 → 7: a budget limit may be a percentage.
 *
 * `budgets` gains `limitType`, `percentage` and the `recurringId` the percentage is taken
 * from.
 *
 * Shipped in 1.5.0-rc04.
 */
object Migration6To7 : Migration(6, 7) {
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
