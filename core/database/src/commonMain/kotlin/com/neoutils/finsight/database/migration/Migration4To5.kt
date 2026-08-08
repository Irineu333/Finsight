package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema 4 → 5: an icon of its own.
 *
 * `budgets` and `accounts` gain `iconKey`. A budget's is seeded from the category it was
 * borrowing the icon from, so nothing changes on screen.
 *
 * Shipped in 1.4.0.
 */
object Migration4To5 : Migration(4, 5) {
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
