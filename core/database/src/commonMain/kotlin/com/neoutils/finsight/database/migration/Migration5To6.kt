package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Gives a credit card its own `iconKey`.
 *
 * Shipped in 1.5.0-rc01.
 */
object Migration5To6 : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `credit_cards` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'card'"
        )
    }
}
