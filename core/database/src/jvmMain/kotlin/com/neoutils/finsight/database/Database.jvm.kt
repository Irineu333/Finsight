package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * The file the desktop app serves its own database from. Nothing is created here: the
 * path is only spelled out, and whoever opens it decides whether it comes into being.
 */
fun defaultDatabasePath(): String =
    File(System.getProperty("user.home"), ".finance/finsight.db").absolutePath

fun getDatabaseBuilder(path: String = defaultDatabasePath()): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = path,
    )
}
