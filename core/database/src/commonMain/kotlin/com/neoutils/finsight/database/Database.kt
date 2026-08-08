package com.neoutils.finsight.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.migration.Migration10To11
import com.neoutils.finsight.database.migration.Migration1To2
import com.neoutils.finsight.database.migration.Migration2To3
import com.neoutils.finsight.database.migration.Migration3To4
import com.neoutils.finsight.database.migration.Migration4To5
import com.neoutils.finsight.database.migration.Migration5To6
import com.neoutils.finsight.database.migration.Migration6To7
import com.neoutils.finsight.database.migration.Migration7To10
import kotlinx.coroutines.Dispatchers

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To10,
            Migration10To11,
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
