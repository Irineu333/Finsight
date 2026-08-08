package com.neoutils.finsight.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Gives a budget its own `iconKey`, seeded from the category it borrowed the icon
 * from, and an account its own.
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
