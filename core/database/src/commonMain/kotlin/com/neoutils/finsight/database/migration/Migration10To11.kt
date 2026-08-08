package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// unpublished
object Migration10To11 : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `accounts` ADD COLUMN `yieldsInterest` INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `systemKey` TEXT DEFAULT NULL"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_systemKey` ON `categories` (`systemKey`)"
        )
    }
}
